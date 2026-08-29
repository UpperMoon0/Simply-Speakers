package com.nstut.simplyspeakers.audio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.network.AcknowledgeUploadPacketS2C;
import com.nstut.simplyspeakers.network.PacketRegistries;
import com.nstut.simplyspeakers.network.RespondUploadAudioPacketS2C;
import com.nstut.simplyspeakers.network.SendAudioFilePacketS2C;
import com.nstut.simplyspeakers.network.SendAudioListPacketS2C;
import com.nstut.simplyspeakers.network.SpeakerStateUpdatePacketS2C;
import com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;

public class AudioFileManager {
    private static final String AUDIO_DIR_NAME = "simply_speakers_audios";
    private static final String MANIFEST_FILE_NAME = "audio_manifest.json";
    private static final int MAX_CHUNK_SIZE = 32000;
    private static final int MAX_CONCURRENT_UPLOADS = 5;
    private static final long UPLOAD_TIMEOUT_MS = 60_000L;

    private final Map<UUID, UploadSession> activeUploads = new ConcurrentHashMap<>();
    private final TransferRequestCoordinator<String> activeDownloads =
            new TransferRequestCoordinator<>(Duration.ofSeconds(30));
    private final ExecutorService audioFileExecutor =
            ChunkedFileTransfer.newDaemonFixedThreadPool(2, "Simply Speakers Audio File");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path audioDirPath;
    private final Path manifestPath;
    private final Map<String, AudioFileMetadata> manifest = new ConcurrentHashMap<>();
    private final Map<String, Long> playbackGrants = new ConcurrentHashMap<>();
    private volatile long nextPlaybackGrantCleanupTime;

    public AudioFileManager(Path worldSavePath) {
        this.audioDirPath = worldSavePath.resolve(AUDIO_DIR_NAME);
        this.manifestPath = audioDirPath.resolve(MANIFEST_FILE_NAME);
        try {
            Files.createDirectories(audioDirPath);
            loadManifest();
        } catch (IOException e) {
            SimplySpeakers.LOGGER.error("Failed to create audio directory", e);
        }
    }

    private synchronized void loadManifest() {
        if (!Files.exists(manifestPath)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(manifestPath)) {
            Type type = new TypeToken<Map<String, AudioFileMetadata>>() {}.getType();
            Map<String, AudioFileMetadata> loadedManifest = GSON.fromJson(reader, type);
            if (loadedManifest != null) {
                manifest.clear();
                boolean manifestModified = false;
                for (Map.Entry<String, AudioFileMetadata> entry : loadedManifest.entrySet()) {
                    AudioFileMetadata meta = entry.getValue();
                    if (meta != null && meta.getDurationSeconds() <= 0.0f) {
                        String extension = FilenameUtils.getExtension(meta.getOriginalFilename());
                        Path filePath = audioDirPath.resolve(meta.getUuid() + (extension.isEmpty() ? "" : "." + extension));
                        if (Files.exists(filePath)) {
                            float duration = AudioDurationCalculator.calculateDurationSeconds(filePath);
                            if (duration > 0.0f) {
                                meta = meta.withDuration(duration);
                                manifestModified = true;
                            }
                        }
                    }
                    if (meta != null) {
                        manifest.put(entry.getKey(), meta);
                    }
                }
                if (manifestModified) {
                    saveManifest();
                }
            }
        } catch (Exception e) {
            SimplySpeakers.LOGGER.error("Failed to load audio manifest, quarantining corrupt manifest", e);
            try {
                Path corruptPath = audioDirPath.resolve(MANIFEST_FILE_NAME + ".corrupt." + System.currentTimeMillis());
                Files.move(manifestPath, corruptPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {}
        }
    }

    private synchronized void saveManifest() {
        Path tmpPath = audioDirPath.resolve(MANIFEST_FILE_NAME + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(tmpPath)) {
                GSON.toJson(manifest, writer);
            }
            try {
                Files.move(tmpPath, manifestPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                Files.move(tmpPath, manifestPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            SimplySpeakers.LOGGER.error("Failed to save audio manifest", e);
            try {
                Files.deleteIfExists(tmpPath);
            } catch (IOException ignored) {}
        }
    }

    public synchronized void shutdown() {
        for (UploadSession session : activeUploads.values()) {
            session.cleanup();
        }
        activeUploads.clear();
        activeDownloads.clear();
        audioFileExecutor.shutdownNow();
        SimplySpeakers.LOGGER.info("AudioFileManager shut down cleanly.");
    }

    public boolean validateFile(String filename) {
        if (filename == null) return false;
        String extension = FilenameUtils.getExtension(filename).toLowerCase();
        return extension.equals("mp3") || extension.equals("wav");
    }

    public static boolean validateAudioContent(Path filePath, String originalFilename) {
        String extension = FilenameUtils.getExtension(originalFilename).toLowerCase();
        if (extension.equals("mp3")) {
            try (InputStream in = Files.newInputStream(filePath)) {
                Bitstream bitstream = new Bitstream(in);
                var header = bitstream.readFrame();
                if (header == null) return false;
                new Decoder().decodeFrame(header, bitstream);
                bitstream.closeFrame();
                return true;
            } catch (Exception e) {
                SimplySpeakers.LOGGER.warn("MP3 frame validation failed for {}: {}", filePath, e.getMessage());
                return false;
            }
        }
        if (!extension.equals("wav")) return false;
        try (InputStream in = Files.newInputStream(filePath);
             java.io.BufferedInputStream bin = new java.io.BufferedInputStream(in)) {
            javax.sound.sampled.AudioFileFormat format = javax.sound.sampled.AudioSystem.getAudioFileFormat(bin);
            return format != null && format.getFormat() != null && format.getFormat().getSampleRate() > 0;
        } catch (Exception e) {
            SimplySpeakers.LOGGER.warn("Audio header validation failed for {}: {}", filePath, e.getMessage());
            return false;
        }
    }

    public AudioFileMetadata saveFile(InputStream inputStream, String originalFilename, String ownerUUID) throws IOException {
        if (!validateFile(originalFilename)) {
            throw new IOException("Invalid file type: " + originalFilename);
        }

        String uuid = UUID.randomUUID().toString();
        String extension = FilenameUtils.getExtension(originalFilename);
        Path filePath = audioDirPath.resolve(uuid + (extension.isEmpty() ? "" : "." + extension));

        Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        float durationSeconds = AudioDurationCalculator.calculateDurationSeconds(filePath);

        AudioFileMetadata metadata = new AudioFileMetadata(uuid, originalFilename, ownerUUID, durationSeconds);
        manifest.put(uuid, metadata);
        saveManifest();

        return metadata;
    }

    public Path getAudioFilePath(String uuid) {
        AudioFileMetadata metadata = manifest.get(uuid);
        if (metadata == null) {
            return null;
        }
        String extension = FilenameUtils.getExtension(metadata.getOriginalFilename());
        return audioDirPath.resolve(uuid + (extension.isEmpty() ? "" : "." + extension));
    }

    private void cleanStaleUploads() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, UploadSession> entry : activeUploads.entrySet()) {
            if (now - entry.getValue().lastActivityTime > UPLOAD_TIMEOUT_MS) {
                UploadSession session = activeUploads.remove(entry.getKey());
                if (session != null) {
                    session.cleanup();
                    SimplySpeakers.LOGGER.warn("Timed out stale upload session {}", entry.getKey());
                }
            }
        }
    }

    public void handleUploadRequest(ServerPlayer player, BlockPos blockPos, UUID transactionId, String fileName, long fileSize) {
        cleanStaleUploads();
        SimplySpeakers.LOGGER.debug("Handling upload request for transaction ID: {}", transactionId);

        if (Config.disableUpload) {
            PacketRegistries.CHANNEL.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0, Component.literal("Audio uploads are disabled on this server.")));
            return;
        }

        if (fileSize <= 0 || fileSize > Config.maxUploadSize || fileSize > Config.MAX_FILE_SIZE) {
            PacketRegistries.CHANNEL.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0, Component.literal("File size is invalid or exceeds maximum allowed upload size.")));
            return;
        }

        String playerUUID = player.getUUID().toString();
        boolean alreadyUploading = activeUploads.values().stream()
                .anyMatch(s -> playerUUID.equals(s.ownerUUID));
        if (alreadyUploading) {
            PacketRegistries.CHANNEL.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0, Component.literal("You already have an active upload in progress.")));
            return;
        }

        if (!validateFile(fileName)) {
            PacketRegistries.CHANNEL.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0, Component.literal("Invalid file type. Only MP3 and WAV files are supported.")));
            return;
        }

        if (activeUploads.size() >= MAX_CONCURRENT_UPLOADS) {
            PacketRegistries.CHANNEL.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0, Component.literal("Server is currently busy with other uploads. Please try again shortly.")));
            return;
        }

        try {
            Path tmpPath = audioDirPath.resolve(transactionId.toString() + ".tmp");
            UploadSession session = new UploadSession(transactionId, fileName, fileSize, blockPos, player.getUUID().toString(), tmpPath);
            activeUploads.put(transactionId, session);
            PacketRegistries.CHANNEL.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, true, MAX_CHUNK_SIZE, Component.literal("Upload approved")));
        } catch (IOException e) {
            SimplySpeakers.LOGGER.error("Failed to initialize upload temporary file", e);
            PacketRegistries.CHANNEL.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0, Component.literal("Server failed to create upload session.")));
        }
    }

    public void handleUploadData(ServerPlayer player, UUID transactionId, byte[] data) {
        UploadSession session = activeUploads.get(transactionId);
        if (session == null) {
            SimplySpeakers.LOGGER.warn("Received upload data for unknown/expired transaction ID: {}", transactionId);
            PacketRegistries.CHANNEL.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0, Component.literal("Upload session not found or timed out.")));
            return;
        }
        if (Config.disableUpload) {
            activeUploads.remove(transactionId);
            session.cleanup();
            PacketRegistries.CHANNEL.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0, Component.literal("Audio uploads were disabled by the server.")));
            return;
        }

        if (!session.ownerUUID.equals(player.getUUID().toString())) {
            SimplySpeakers.LOGGER.warn("Upload rejected for transaction ID {}: player mismatch", transactionId);
            return;
        }

        session.lastActivityTime = System.currentTimeMillis();
        long newSize = session.receivedSize + data.length;
        if (data.length > MAX_CHUNK_SIZE || newSize > session.declaredFileSize || newSize > Config.maxUploadSize) {
            activeUploads.remove(transactionId);
            session.cleanup();
            SimplySpeakers.LOGGER.warn("Upload rejected for transaction ID {}: size exceeded limit", transactionId);
            PacketRegistries.CHANNEL.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0, Component.literal("Upload exceeded maximum size limits.")));
            return;
        }

        try {
            session.appendChunk(data);
        } catch (IOException e) {
            activeUploads.remove(transactionId);
            session.cleanup();
            SimplySpeakers.LOGGER.error("Failed writing chunk to disk for upload {}", transactionId, e);
            PacketRegistries.CHANNEL.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0, Component.literal("Disk write error during upload.")));
            return;
        }

        if (session.isComplete()) {
            activeUploads.remove(transactionId);
            MinecraftServer server = player.getServer();
            audioFileExecutor.submit(() -> finishUpload(player, server, transactionId, session));
        }
    }

    private void finishUpload(ServerPlayer player, MinecraftServer server, UUID transactionId, UploadSession session) {
        try {
            session.close();

            if (!validateAudioContent(session.tempFilePath, session.fileName)) {
                session.cleanup();
                SimplySpeakers.LOGGER.warn("Rejecting upload {}: invalid or corrupt audio content", transactionId);
                if (server != null) {
                    server.execute(() -> PacketRegistries.CHANNEL.sendToPlayer(player, new AcknowledgeUploadPacketS2C(transactionId, false, Component.literal("Uploaded file is corrupt or not a valid audio file."), session.blockPos)));
                }
                return;
            }

            String uuid = UUID.randomUUID().toString();
            String extension = FilenameUtils.getExtension(session.fileName);
            Path finalPath = audioDirPath.resolve(uuid + (extension.isEmpty() ? "" : "." + extension));

            try {
                Files.move(session.tempFilePath, finalPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                Files.move(session.tempFilePath, finalPath, StandardCopyOption.REPLACE_EXISTING);
            }
            float durationSeconds = AudioDurationCalculator.calculateDurationSeconds(finalPath);

            UploadSession state = session;
            AudioFileMetadata metadata = new AudioFileMetadata(uuid, state.fileName, state.ownerUUID, durationSeconds);
            manifest.put(uuid, metadata);
            saveManifest();

            SimplySpeakers.LOGGER.info("Upload completed for transaction {}. Saved as {} (duration: {}s)", transactionId, uuid, durationSeconds);
            if (server != null) {
                server.execute(() -> PacketRegistries.CHANNEL.sendToPlayer(player, new AcknowledgeUploadPacketS2C(transactionId, true, Component.literal("File uploaded successfully: " + metadata.getOriginalFilename()), session.blockPos)));
            }
        } catch (Exception e) {
            session.cleanup();
            SimplySpeakers.LOGGER.error("Failed to complete upload for transaction {}", transactionId, e);
            if (server != null) {
                server.execute(() -> PacketRegistries.CHANNEL.sendToPlayer(player, new AcknowledgeUploadPacketS2C(transactionId, false, Component.literal("Failed to save uploaded file on server."), session.blockPos)));
            }
        }
    }

    public void sendAudioList(ServerPlayer player, BlockPos blockPos) {
        List<AudioFileMetadata> audioList = getAudioListForPlayer(player.getUUID().toString());
        PacketRegistries.CHANNEL.sendToPlayer(player, new SendAudioListPacketS2C(audioList));
    }

    public boolean deleteAudioFile(String audioId, String playerUUID, MinecraftServer server) {
        AudioFileMetadata metadata = manifest.get(audioId);
        if (metadata == null) {
            return false;
        }

        if (!AudioOwnership.isOwnedBy(metadata.getOwnerUUID(), playerUUID)) {
            SimplySpeakers.LOGGER.warn("Player {} tried to delete audio {} owned by {}", playerUUID, audioId, metadata.getOwnerUUID());
            return false;
        }

        Path filePath = getAudioFilePath(audioId);
        if (filePath != null) {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                SimplySpeakers.LOGGER.error("Failed to delete audio file {}", audioId, e);
                return false;
            }
        }

        manifest.remove(audioId);
        saveManifest();

        // Cascade delete: stop & clear any active speaker states referencing this audio
        Map<String, com.nstut.simplyspeakers.SpeakerState> states = ServerSpeakerRegistry.findStatesWithAudioId(audioId);
        for (com.nstut.simplyspeakers.SpeakerState state : states.values()) {
            state.setAudioId("");
            state.setAudioFilename("");
            state.setPlaying(false);
            state.setPlaybackStartTick(-1);
        }
        ServerSpeakerRegistry.markDirty();
        broadcastDeletedAudioState(server, states);

        return true;
    }

    private void broadcastDeletedAudioState(MinecraftServer server, Map<String, com.nstut.simplyspeakers.SpeakerState> affected) {
        if (server == null) return;
        for (var level : server.getAllLevels()) {
            String prefix = level.dimension().location() + "/";
            for (String fullKey : affected.keySet()) {
                if (!fullKey.startsWith(prefix)) continue;
                String stateKey = fullKey.substring(prefix.length());
                String speakerId = stateKey.startsWith("net_") ? stateKey.substring(4) : "";
                Set<BlockPos> positions = ServerSpeakerRegistry.getSpeakerPositions(level, stateKey);
                if (positions.isEmpty()) {
                    if (!stateKey.startsWith("net_")) continue;
                    boolean looping = affected.get(fullKey).isLooping();
                    SpeakerStateUpdatePacketS2C packet = new SpeakerStateUpdatePacketS2C(speakerId, "stop", "", "", -1, looping);
                    for (ServerPlayer target : level.players()) PacketRegistries.CHANNEL.sendToPlayer(target, packet);
                    continue;
                }
                for (BlockPos pos : positions) {
                    boolean looping = affected.get(fullKey).isLooping();
                    SpeakerStateUpdatePacketS2C packet = new SpeakerStateUpdatePacketS2C(pos, speakerId, "stop", "", "", -1, looping);
                    for (ServerPlayer target : level.players()) PacketRegistries.CHANNEL.sendToPlayer(target, packet);
                }
            }
        }
    }

    public List<AudioFileMetadata> getAudioListForPlayer(String playerUUID) {
        return AudioOwnership.ownedBy(manifest.values(), AudioFileMetadata::getOwnerUUID, playerUUID);
    }

    public void sendAudioFile(ServerPlayer player, String audioId) {
        AudioFileMetadata metadata = manifest.get(audioId);
        String playerId = player.getUUID().toString();
        String grantKey = playerId + ":" + audioId;
        boolean owner = metadata != null && AudioOwnership.isOwnedBy(metadata.getOwnerUUID(), playerId);
        Long grantExpiry = playbackGrants.get(grantKey);
        long now = System.currentTimeMillis();
        if (grantExpiry != null && grantExpiry < now) playbackGrants.remove(grantKey, grantExpiry);
        if (!owner && (grantExpiry == null || grantExpiry < System.currentTimeMillis())) return;
        String transferKey = player.getUUID() + ":" + audioId;
        activeDownloads.tryStart(transferKey, () -> {
            Path filePath = this.getAudioFilePath(audioId);
            if (filePath == null || !Files.exists(filePath)) {
                activeDownloads.release(transferKey);
                return;
            }
            MinecraftServer server = player.getServer();
            audioFileExecutor.execute(() -> sendAudioFileAsync(player, server, audioId, filePath, transferKey));
        });
    }

    public void grantPlaybackDownload(ServerPlayer player, String audioId) {
        long now = System.currentTimeMillis();
        if (now >= nextPlaybackGrantCleanupTime) {
            nextPlaybackGrantCleanupTime = now + 60_000L;
            playbackGrants.entrySet().removeIf(entry -> entry.getValue() < now);
        }
        playbackGrants.put(player.getUUID() + ":" + audioId, now + 120_000L);
    }

    private void sendAudioFileAsync(ServerPlayer player, MinecraftServer server, String audioId, Path filePath, String transferKey) {
        try {
            long fileSize = Files.size(filePath);
            if (fileSize > Config.MAX_FILE_SIZE) {
                activeDownloads.release(transferKey);
                SimplySpeakers.LOGGER.warn("Refusing to send audio {} because it is {} bytes, over hard limit", audioId, fileSize);
                return;
            }

            ChunkedFileTransfer.streamFilePaced(filePath, MAX_CHUNK_SIZE, 5L, (chunk, isLast) -> {
                if (server != null) {
                    server.execute(() -> PacketRegistries.CHANNEL.sendToPlayer(player, new SendAudioFilePacketS2C(audioId, chunk, isLast)));
                }
                if (isLast) {
                    activeDownloads.release(transferKey);
                }
            });
        } catch (IOException e) {
            activeDownloads.release(transferKey);
            SimplySpeakers.LOGGER.error("Failed to stream audio file for download", e);
        }
    }


    /**
     * Replaces the manifest entry for an audio id with updated library
     * metadata (display name, category, tags, ...) and persists immediately.
     */
    public void updateAudioMetadata(String audioId, AudioFileMetadata updated) {
        if (audioId == null || audioId.isEmpty() || updated == null) return;
        manifest.put(audioId, updated);
        saveManifest();
    }

    public Map<String, AudioFileMetadata> getManifest() {
        return Collections.unmodifiableMap(manifest);
    }

    private static class UploadSession {
        private final UUID transactionId;
        private final String fileName;
        private final long declaredFileSize;
        private final BlockPos blockPos;
        private final String ownerUUID;
        private final Path tempFilePath;
        private final OutputStream outputStream;
        private long receivedSize = 0;
        private volatile long lastActivityTime;

        public UploadSession(UUID transactionId, String fileName, long declaredFileSize, BlockPos blockPos, String ownerUUID, Path tempFilePath) throws IOException {
            this.transactionId = transactionId;
            this.fileName = fileName;
            this.declaredFileSize = declaredFileSize;
            this.blockPos = blockPos;
            this.ownerUUID = ownerUUID;
            this.tempFilePath = tempFilePath;
            this.outputStream = Files.newOutputStream(tempFilePath);
            this.lastActivityTime = System.currentTimeMillis();
        }

        public synchronized void appendChunk(byte[] data) throws IOException {
            outputStream.write(data);
            receivedSize += data.length;
        }

        public boolean isComplete() {
            return receivedSize >= declaredFileSize;
        }

        public synchronized void close() throws IOException {
            outputStream.flush();
            outputStream.close();
        }

        public synchronized void cleanup() {
            try {
                outputStream.close();
            } catch (IOException ignored) {}
            try {
                Files.deleteIfExists(tempFilePath);
            } catch (IOException ignored) {}
        }
    }
}
