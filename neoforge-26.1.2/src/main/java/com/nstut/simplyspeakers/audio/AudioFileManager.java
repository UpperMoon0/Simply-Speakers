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
    private static final int MAX_CHUNK_SIZE = com.nstut.packetweave.api.OutboundTransfers.CHUNK_BYTES;
    private static final int MAX_CONCURRENT_UPLOADS = 5;
    private static final long UPLOAD_TIMEOUT_MS = 60_000L;

    private volatile boolean stopped;
    // Lifecycle transitions never hold a filesystem lock or perform disk I/O.
    private final Object uploadLifecycle = new Object();
    private final ManifestState manifestState;
    private final Object manifestPersistence;
    private final BoundedFileReceiver uploadReceiver = new BoundedFileReceiver(
            MAX_CHUNK_SIZE, Config.MAX_FILE_SIZE, MAX_CONCURRENT_UPLOADS, 1, Duration.ofMillis(UPLOAD_TIMEOUT_MS));
    private final Map<UUID, UploadSession> activeUploads = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path audioDirPath;
    private final Path manifestPath;
    private final Map<String, AudioFileMetadata> manifest;

    // A replacement manager for the same world must observe accepted commits still
    // draining from its predecessor. Weak values retain no abandoned world's catalog.
    private static final Map<Path, java.lang.ref.WeakReference<ManifestState>> WORLD_MANIFESTS = new java.util.HashMap<>();
    private static synchronized ManifestState manifestState(Path root) {
        WORLD_MANIFESTS.entrySet().removeIf(entry -> entry.getValue().get() == null);
        java.lang.ref.WeakReference<ManifestState> reference = WORLD_MANIFESTS.get(root);
        ManifestState state = reference == null ? null : reference.get();
        if (state == null) {
            state = new ManifestState();
            WORLD_MANIFESTS.put(root, new java.lang.ref.WeakReference<>(state));
        }
        return state;
    }
    private static final class ManifestState {
        final Map<String, AudioFileMetadata> entries = new ConcurrentHashMap<>();
        final Object initialization = new Object();
        volatile boolean initialized;
    }
    private final Map<String, Long> playbackGrants = new ConcurrentHashMap<>();
    private volatile long nextPlaybackGrantCleanupTime;

    public AudioFileManager(Path worldSavePath) {
        this.audioDirPath = worldSavePath.resolve(AUDIO_DIR_NAME);
        this.manifestPath = audioDirPath.resolve(MANIFEST_FILE_NAME);
        this.manifestState = manifestState(audioDirPath.toAbsolutePath().normalize());
        this.manifestPersistence = manifestState;
        this.manifest = manifestState.entries;
        // Initialization and later persistence use different locks: reopening a world
        // never waits for an old manager's blocked accepted publication to finish.
        synchronized (manifestState.initialization) {
            if (!manifestState.initialized) {
                try {
                    Files.createDirectories(audioDirPath);
                    loadManifest();
                    manifestState.initialized = true;
                } catch (IOException error) {
                    LOGGER.error("Failed to create audio directory", error);
                }
            }
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
                    if (meta != null && !meta.isRemote() && meta.getDurationSeconds() <= 0.0f) {
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

    private void saveManifest() {
        synchronized (manifestPersistence) {
            try { persistManifest(new java.util.HashMap<>(manifest)); }
            catch (IOException error) { LOGGER.error("Failed to save audio manifest", error); }
        }
    }

    // Caller serializes manifest snapshots. Upload failures must propagate to PacketWeave,
    // which retains ownership of the unpublished file and retries cleanup when needed.
    void persistManifest(Map<String, AudioFileMetadata> snapshot) throws IOException {
        Path tmpPath = audioDirPath.resolve(MANIFEST_FILE_NAME + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(tmpPath)) { GSON.toJson(snapshot, writer); }
            try {
                Files.move(tmpPath, manifestPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException error) {
                Files.move(tmpPath, manifestPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException error) {
            try { Files.deleteIfExists(tmpPath); }
            catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    public void shutdown() {
        synchronized (uploadLifecycle) {
            if (stopped) return;
            stopped = true;
            for (UploadSession session : activeUploads.values()) cancelUpload(session);
            // A commit already claimed before shutdown is allowed to finish. No later
            // commit can start. This does not wait for blocked validation or filesystem I/O.
            uploadReceiver.drainAndClose();
        }
        LOGGER.info("AudioFileManager stopped admission; accepted commits are draining.");
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
        if (metadata == null || metadata.isRemote()) {
            return null;
        }
        String extension = FilenameUtils.getExtension(metadata.getOriginalFilename());
        return audioDirPath.resolve(uuid + (extension.isEmpty() ? "" : "." + extension));
    }

    public void cancelUploads(UUID owner) {
        synchronized (uploadLifecycle) {
            for (UploadSession session : activeUploads.values()) {
                if (session.ownerUUID.equals(owner.toString())) cancelUpload(session);
            }
        }
    }

    private boolean cancelUpload(UploadSession session) {
        synchronized (uploadLifecycle) {
            if (session.committing) return false; // The atomic terminal claim already won.
            activeUploads.remove(session.key.transaction(), session);
            uploadReceiver.cancel(session.key);
            return true;
        }
    }

    UploadSession createUploadSession(UUID transactionId, String fileName, long fileSize,
                                      BlockPos blockPos, UUID owner) {
        synchronized (uploadLifecycle) {
            if (stopped || activeUploads.containsKey(transactionId) || activeUploads.size() >= MAX_CONCURRENT_UPLOADS
                    || activeUploads.values().stream().anyMatch(s -> s.ownerUUID.equals(owner.toString()))) return null;
            // The manifest is the publication boundary. A unique unpublished path needs
            // no rename, so PacketWeave owns the same cleanup path on every failure.
            String audioId = UUID.randomUUID().toString();
            Path path = audioDirPath.resolve(audioId + "." + FilenameUtils.getExtension(fileName));
            UploadSession session = new UploadSession(transactionId, fileName, fileSize, blockPos,
                    owner.toString(), path, audioId);
            activeUploads.put(transactionId, session);
            return session;
        }
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
        UploadSession session = createUploadSession(transactionId, fileName, fileSize, blockPos, player.getUUID());
        if (session == null) return;
        com.nstut.simplyspeakers.testing.LiveUploadServerProbe.begin(transactionId, fileName);
        boolean admitted = uploadReceiver.begin(session.key, session.tempFilePath, fileSize, new BoundedFileReceiver.Listener() {
            public void committed(long bytes, boolean complete) throws Exception {
                com.nstut.simplyspeakers.testing.LiveUploadServerProbe.committed(transactionId, bytes);
                if (stopped || activeUploads.get(transactionId) != session) {
                    throw new IOException("Upload cancelled before validation");
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
            if (!cancelUpload(session)) return;
            NetworkManager.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0,
                    Component.literal("Upload rejected: invalid chunk, size limit, or server busy.")));
        }
    }

    private void finishUpload(ServerPlayer player, MinecraftServer server, UUID transactionId, UploadSession session) throws IOException {
        AudioFileMetadata metadata = publishUpload(session);
        // Publication has succeeded. A stopped/rejecting notification executor cannot
        // retroactively fail the receiver and delete a file referenced by the manifest.
        try {
            com.nstut.simplyspeakers.testing.LiveUploadServerProbe.completed(transactionId, true);
            LOGGER.info("Upload completed for transaction {}. Saved as {}", transactionId, metadata.getUuid());
            server.execute(() -> {
                if (!stopped) NetworkManager.sendToPlayer(player, new AcknowledgeUploadPacketS2C(transactionId, true,
                        Component.literal("File uploaded successfully: " + metadata.getOriginalFilename()), session.blockPos));
            });
        } catch (RuntimeException error) {
            LOGGER.warn("Upload {} was saved, but completion notification failed", transactionId, error);
        }
    }

    AudioFileMetadata publishUpload(UploadSession session) throws IOException {
        if (!validateAudioContent(session.tempFilePath, session.fileName))
            throw new IOException("Uploaded file is corrupt or not a valid audio file");
        float duration = AudioDurationCalculator.calculateDurationSeconds(session.tempFilePath);
        beforeUploadCommit();
        synchronized (uploadLifecycle) {
            if (stopped || session.committing || activeUploads.get(session.key.transaction()) != session)
                throw new IOException("Upload cancelled before publication");
            session.committing = true;
        }
        AudioFileMetadata metadata = new AudioFileMetadata(session.audioId, session.fileName, session.ownerUUID, duration);
        synchronized (manifestPersistence) {
            Map<String, AudioFileMetadata> snapshot = new java.util.HashMap<>(manifest);
            snapshot.put(session.audioId, metadata);
            persistManifest(snapshot); // Failure keeps the file unpublished and propagates to its receiver.
            manifest.put(session.audioId, metadata);
        }
        return metadata;
    }

    // Package boundary for deterministic validation-versus-disconnect/shutdown regression tests.
    void beforeUploadCommit() throws IOException { }

    /** Saves a URL bookmark; imports never fetch audio or change playback. */
    public AudioFileMetadata addRemoteAudio(String url, String title, String ownerUUID) throws IOException {
        if (!Config.isRemoteStreamingAllowed()) throw new IllegalArgumentException("Remote audio is disabled");
        UUID.fromString(ownerUUID);
        String source=url == null ? "" : url.trim();
        String name=title == null ? "" : title.trim();
        if (source.length()>256 || !StreamTracks.hasSupportedExtension(source)
                || !StreamTracks.isRemoteStreamUrlAllowed(source,false))
            throw new IllegalArgumentException("Invalid direct audio URL");
        if (name.length()>128 || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid audio name");
        if (name.isEmpty()) {
            String path=java.net.URI.create(source).getPath();
            name=path.substring(path.lastIndexOf('/')+1);
            if (name.length()>128) name=name.substring(0,128);
        }
        synchronized (manifestPersistence) {
            if (stopped) throw new IOException("Audio library is closed");
            var owned=getAudioListForPlayer(ownerUUID);
            for (var entry:owned) if (source.equals(entry.getRemoteUrl())) return entry;
            if (owned.stream().filter(AudioFileMetadata::isRemote).count()>=256)
                throw new IllegalArgumentException("Remote audio library is full");
            var entry=new AudioFileMetadata(UUID.randomUUID().toString(),name,ownerUUID).withRemoteUrl(source);
            var snapshot=new java.util.HashMap<>(manifest);snapshot.put(entry.getUuid(),entry);
            persistManifest(snapshot);
            manifest.put(entry.getUuid(),entry);
            return entry;
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

        if (metadata.isRemote()) {
            synchronized (manifestPersistence) {
                var snapshot=new java.util.HashMap<>(manifest);snapshot.remove(audioId);
                try { persistManifest(snapshot); }
                catch(IOException error) { LOGGER.error("Failed to remove remote library entry",error);return false; }
                manifest.remove(audioId);
            }
            // Removing a bookmark does not delete its source or interrupt saved playback copies.
            return true;
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

    public void sendAudioFile(ServerPlayer player, String audioId, UUID transferId) {
        AudioFileMetadata metadata = manifest.get(audioId);
        String playerId = player.getUUID().toString();
        Long grantExpiry = playbackGrants.get(playerId + ":" + audioId);
        if (metadata == null || (!AudioOwnership.isOwnedBy(metadata.getOwnerUUID(), playerId)
                && (grantExpiry == null || grantExpiry < System.currentTimeMillis()))) return;
        MinecraftServer server = player.level().getServer();
        if (server == null || stopped) return;
        Path path = getAudioFilePath(audioId);
        if (path == null) return;
        boolean admitted = com.nstut.packetweave.api.OutboundTransfers.shared().start(player, transferId, path,
                Config.MAX_FILE_SIZE, server::execute, (index, total, chunk) -> {
                    if (stopped || server.getPlayerList().getPlayer(player.getUUID()) != player)
                        throw new IOException("Player disconnected during audio transfer");
                    NetworkManager.sendToPlayer(player, new SendAudioFilePacketS2C(
                            audioId, transferId, index, chunk, index == total - 1));
                }, failure -> {
                    if (failure != null) server.execute(() -> {
                        if (server.getPlayerList().getPlayer(player.getUUID()) == player)
                            NetworkManager.sendToPlayer(player, new SendAudioFilePacketS2C(audioId, transferId, -1, new byte[0], true));
                    });
                });
        if (!admitted) NetworkManager.sendToPlayer(player, new SendAudioFilePacketS2C(audioId, transferId, -1, new byte[0], true));
    }

    public void grantPlaybackDownload(ServerPlayer player, String audioId) {
        long now = System.currentTimeMillis();
        if (now >= nextPlaybackGrantCleanupTime) {
            nextPlaybackGrantCleanupTime = now + 60_000L;
            playbackGrants.entrySet().removeIf(entry -> entry.getValue() < now);
        }
        playbackGrants.put(player.getUUID() + ":" + audioId, now + 120_000L);
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
    static final class UploadSession {
        final TransferId key;
        private final String fileName;
        private final long declaredFileSize;
        private final BlockPos blockPos;
        private final String ownerUUID;
        final Path tempFilePath;
        final String audioId;
        boolean committing; // Guarded by uploadLifecycle.
        UploadSession(UUID transactionId, String fileName, long size, BlockPos blockPos, String ownerUUID, Path path, String audioId) {
            this.key = new TransferId("simplyspeakers", UUID.fromString(ownerUUID), transactionId);
            this.fileName = fileName; this.declaredFileSize = size; this.blockPos = blockPos;
            this.ownerUUID = ownerUUID; this.tempFilePath = path; this.audioId = audioId;
        }
    }
}
