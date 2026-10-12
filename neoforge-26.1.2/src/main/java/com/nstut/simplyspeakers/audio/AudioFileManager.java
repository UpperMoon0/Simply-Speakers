package com.nstut.simplyspeakers.audio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.network.AcknowledgeUploadPacketS2C;
import com.nstut.simplyspeakers.network.RespondUploadAudioPacketS2C;
import com.nstut.simplyspeakers.network.SendAudioFilePacketS2C;
import com.nstut.simplyspeakers.network.SendAudioListPacketS2C;
import com.nstut.simplyspeakers.network.SpeakerStateUpdatePacketS2C;
import com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.io.InputStream;
import com.nstut.packetweave.api.BoundedFileReceiver;
import com.nstut.packetweave.api.ChunkedTransfer;
import com.nstut.packetweave.api.TransferId;
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
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("simplyspeakers");
    private static final String AUDIO_DIR_NAME = "simply_speakers_audios";
    private static final String MANIFEST_FILE_NAME = "audio_manifest.json";
    private static final int MAX_CHUNK_SIZE = 32000;
    private static final int MAX_CONCURRENT_UPLOADS = 5;
    private static final long UPLOAD_TIMEOUT_MS = 60_000L;

    private volatile boolean stopped;
    private final BoundedFileReceiver uploadReceiver = new BoundedFileReceiver(
            MAX_CHUNK_SIZE, Config.MAX_FILE_SIZE, MAX_CONCURRENT_UPLOADS, 1, Duration.ofMillis(UPLOAD_TIMEOUT_MS));
    private final Map<UUID, UploadSession> activeUploads = new ConcurrentHashMap<>();
    private final TransferRequestCoordinator<String> activeDownloads =
            new TransferRequestCoordinator<>(Duration.ofSeconds(30));
    private final ExecutorService audioFileExecutor =
            java.util.concurrent.Executors.newFixedThreadPool(2, task -> {
                Thread thread = new Thread(task, "Simply Speakers Audio File");
                thread.setDaemon(true);
                return thread;
            });
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
            LOGGER.error("Failed to create audio directory", e);
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
            LOGGER.error("Failed to load audio manifest, quarantining corrupt manifest", e);
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
            LOGGER.error("Failed to save audio manifest", e);
            try {
                Files.deleteIfExists(tmpPath);
            } catch (IOException ignored) {}
        }
    }

    public synchronized void shutdown() {
        stopped = true;
        uploadReceiver.close();
        activeUploads.clear();
        activeDownloads.clear();
        audioFileExecutor.shutdownNow();
        LOGGER.info("AudioFileManager shut down cleanly.");
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
                LOGGER.warn("MP3 frame validation failed for {}: {}", filePath, e.getMessage());
                return false;
            }
        }
        if (!extension.equals("wav")) return false;
        try (InputStream in = Files.newInputStream(filePath);
             java.io.BufferedInputStream bin = new java.io.BufferedInputStream(in)) {
            javax.sound.sampled.AudioFileFormat format = javax.sound.sampled.AudioSystem.getAudioFileFormat(bin);
            return format != null && format.getFormat() != null && format.getFormat().getSampleRate() > 0;
        } catch (Exception e) {
            LOGGER.warn("Audio header validation failed for {}: {}", filePath, e.getMessage());
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

    public void cancelUploads(UUID owner) {
        uploadReceiver.cancelOwner(owner);
        activeUploads.entrySet().removeIf(entry -> entry.getValue().ownerUUID.equals(owner.toString()));
    }

    public void handleUploadRequest(ServerPlayer player, BlockPos blockPos, UUID transactionId, String fileName, long fileSize) {
        if (stopped) return;
        LOGGER.debug("Handling upload request for transaction ID: {}", transactionId);

        if (Config.disableUpload) {
            NetworkManager.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0, Component.literal("Audio uploads are disabled on this server.")));
            return;
        }

        if (fileSize <= 0 || fileSize > Config.maxUploadSize || fileSize > Config.MAX_FILE_SIZE) {
            NetworkManager.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0, Component.literal("File size is invalid or exceeds maximum allowed upload size.")));
            return;
        }

        String playerUUID = player.getUUID().toString();
        boolean alreadyUploading = activeUploads.values().stream()
                .anyMatch(s -> playerUUID.equals(s.ownerUUID));
        if (alreadyUploading) {
            NetworkManager.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0, Component.literal("You already have an active upload in progress.")));
            return;
        }

        if (!validateFile(fileName)) {
            NetworkManager.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0, Component.literal("Invalid file type. Only MP3 and WAV files are supported.")));
            return;
        }

        if (activeUploads.containsKey(transactionId) || activeUploads.size() >= MAX_CONCURRENT_UPLOADS) {
            NetworkManager.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0, Component.literal("Server is currently busy with other uploads. Please try again shortly.")));
            return;
        }

        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        Path tmpPath = audioDirPath.resolve(transactionId.toString() + ".tmp");
        UploadSession session = new UploadSession(transactionId, fileName, fileSize, blockPos, player.getUUID().toString(), tmpPath);
        activeUploads.put(transactionId, session);
        com.nstut.simplyspeakers.testing.LiveUploadServerProbe.begin(transactionId, fileName);
        boolean admitted = uploadReceiver.begin(session.key, tmpPath, fileSize, new BoundedFileReceiver.Listener() {
            public void committed(long bytes, boolean complete) {
                com.nstut.simplyspeakers.testing.LiveUploadServerProbe.committed(transactionId, bytes);
                if (stopped || activeUploads.get(transactionId) != session) {
                    session.cleanup();
                    uploadReceiver.cancel(session.key);
                    return;
                }
                server.execute(() -> {
                    if (!stopped)
                        NetworkManager.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, true, 0, Component.literal("Chunk received"), bytes));
                });
                if (complete) {
                    try { finishUpload(player, server, transactionId, session); }
                    finally { activeUploads.remove(transactionId, session); }
                }
            }
            public void failed(Exception error) {
                activeUploads.remove(transactionId, session);
                LOGGER.warn("Upload {} failed: {}", transactionId, error.getMessage());
                server.execute(() -> {
                    if (!stopped) NetworkManager.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0,
                            Component.literal("Upload cancelled, timed out, or failed. Please retry.")));
                });
            }
        });
        if (!admitted) {
            activeUploads.remove(transactionId, session);
            NetworkManager.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0, Component.literal("Server upload capacity is full.")));
            return;
        }
        NetworkManager.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, true, MAX_CHUNK_SIZE, Component.literal("Upload approved")));
    }

    public void handleUploadData(ServerPlayer player, UUID transactionId, int index, byte[] data) {
        UploadSession session = activeUploads.get(transactionId);
        if (session == null || !session.ownerUUID.equals(player.getUUID().toString())) return;
        if (stopped || Config.disableUpload || session.declaredFileSize > Config.maxUploadSize ||
                !uploadReceiver.accept(session.key, index, data)) {
            uploadReceiver.cancel(session.key);
            activeUploads.remove(transactionId, session);
            NetworkManager.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0,
                    Component.literal("Upload rejected: invalid chunk, size limit, or server busy.")));
        }
    }

    private void finishUpload(ServerPlayer player, MinecraftServer server, UUID transactionId, UploadSession session) {
        try {

            if (!validateAudioContent(session.tempFilePath, session.fileName)) {
                session.cleanup();
                LOGGER.warn("Rejecting upload {}: invalid or corrupt audio content", transactionId);
                if (server != null) {
                    server.execute(() -> NetworkManager.sendToPlayer(player, new AcknowledgeUploadPacketS2C(transactionId, false, Component.literal("Uploaded file is corrupt or not a valid audio file."), session.blockPos)));
                }
                return;
            }

            if (stopped || activeUploads.get(transactionId) != session) { session.cleanup(); return; }
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

            com.nstut.simplyspeakers.testing.LiveUploadServerProbe.completed(transactionId, true);
            LOGGER.info("Upload completed for transaction {}. Saved as {} (duration: {}s)", transactionId, uuid, durationSeconds);
            if (server != null) {
                server.execute(() -> NetworkManager.sendToPlayer(player, new AcknowledgeUploadPacketS2C(transactionId, true, Component.literal("File uploaded successfully: " + metadata.getOriginalFilename()), session.blockPos)));
            }
        } catch (Exception e) {
            session.cleanup();
            LOGGER.error("Failed to complete upload for transaction {}", transactionId, e);
            if (server != null) {
                server.execute(() -> NetworkManager.sendToPlayer(player, new AcknowledgeUploadPacketS2C(transactionId, false, Component.literal("Failed to save uploaded file on server."), session.blockPos)));
            }
        }
    }

    public void sendAudioList(ServerPlayer player, BlockPos blockPos) {
        List<AudioFileMetadata> audioList = getAudioListForPlayer(player.getUUID().toString());
        NetworkManager.sendToPlayer(player, new SendAudioListPacketS2C(audioList));
    }

    public boolean deleteAudioFile(String audioId, String playerUUID, MinecraftServer server) {
        AudioFileMetadata metadata = manifest.get(audioId);
        if (metadata == null) {
            return false;
        }

        if (!AudioOwnership.isOwnedBy(metadata.getOwnerUUID(), playerUUID)) {
            LOGGER.warn("Player {} tried to delete audio {} owned by {}", playerUUID, audioId, metadata.getOwnerUUID());
            return false;
        }

        Path filePath = getAudioFilePath(audioId);
        if (filePath != null) {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                LOGGER.error("Failed to delete audio file {}", audioId, e);
                return false;
            }
        }

        manifest.remove(audioId);
        saveManifest();

        com.nstut.simplyspeakers.speakers.ServerSpeakerControlService.removeDeletedAudio(server, audioId);
        return true;
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
            MinecraftServer server = player.level().getServer();
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
                LOGGER.warn("Refusing to send audio {} because it is {} bytes, over hard limit", audioId, fileSize);
                return;
            }

            if (server == null) return;
            ChunkedTransfer.streamFile(filePath, MAX_CHUNK_SIZE, Config.MAX_FILE_SIZE,
                    ChunkedTransfer.onExecutor(server::execute, Duration.ofSeconds(30), (index, total, chunk) -> {
                        NetworkManager.sendToPlayer(player, new SendAudioFilePacketS2C(audioId, chunk, index == total - 1));
                    }));
        } catch (IOException e) {
            activeDownloads.release(transferKey);
            LOGGER.error("Failed to stream audio file for download", e);
        } finally {
            activeDownloads.release(transferKey);
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

    /** Audio metadata only; PacketWeave owns receive state and the file writer. */
    private static class UploadSession {
        private final TransferId key;
        private final String fileName;
        private final long declaredFileSize;
        private final BlockPos blockPos;
        private final String ownerUUID;
        private final Path tempFilePath;
        UploadSession(UUID transactionId, String fileName, long size, BlockPos blockPos, String ownerUUID, Path path) {
            this.key = new TransferId("simplyspeakers", UUID.fromString(ownerUUID), transactionId);
            this.fileName = fileName; this.declaredFileSize = size; this.blockPos = blockPos;
            this.ownerUUID = ownerUUID; this.tempFilePath = path;
        }
        // Invoked exclusively on the PacketWeave worker during audio validation.
        void cleanup() {
            try { Files.deleteIfExists(tempFilePath); } catch (IOException ignored) { }
        }
    }
}
