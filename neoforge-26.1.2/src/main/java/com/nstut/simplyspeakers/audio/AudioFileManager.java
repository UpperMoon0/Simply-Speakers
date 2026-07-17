package com.nstut.simplyspeakers.audio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import com.nstut.simplyspeakers.network.AcknowledgeUploadPacketS2C;
import com.nstut.simplyspeakers.network.RespondUploadAudioPacketS2C;
import com.nstut.simplyspeakers.network.SendAudioFilePacketS2C;
import com.nstut.simplyspeakers.network.SendAudioListPacketS2C;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class AudioFileManager {
    private static final String AUDIO_DIR_NAME = "simply_speakers_audios";
    private static final String MANIFEST_FILE_NAME = "audio_manifest.json";
    private static final int MAX_CHUNK_SIZE = 32000;
    private static final Map<UUID, UploadState> activeUploads = new ConcurrentHashMap<>();
    private static final TransferRequestCoordinator<String> activeDownloads =
            new TransferRequestCoordinator<>(Duration.ofSeconds(30));
    private static final ExecutorService AUDIO_FILE_EXECUTOR =
            ChunkedFileTransfer.newDaemonFixedThreadPool(2, "Simply Speakers Audio File");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path audioDirPath;
    private final Path manifestPath;
    private final Map<String, AudioFileMetadata> manifest = new HashMap<>();

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

    private void loadManifest() {
        if (!Files.exists(manifestPath)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(manifestPath)) {
            Type type = new TypeToken<Map<String, AudioFileMetadata>>() {
            }.getType();
            Map<String, AudioFileMetadata> loadedManifest = GSON.fromJson(reader, type);
            if (loadedManifest != null) {
                manifest.putAll(loadedManifest);
            }
        } catch (IOException e) {
            SimplySpeakers.LOGGER.error("Failed to load audio manifest", e);
        }
    }

    private void saveManifest() {
        try (Writer writer = Files.newBufferedWriter(manifestPath)) {
            GSON.toJson(manifest, writer);
        } catch (IOException e) {
            SimplySpeakers.LOGGER.error("Failed to save audio manifest", e);
        }
    }

    public boolean validateFile(String filename) {
        String extension = FilenameUtils.getExtension(filename).toLowerCase();
        return extension.equals("mp3") || extension.equals("wav");
    }

    public AudioFileMetadata saveFile(InputStream inputStream, String originalFilename, String ownerUUID) throws IOException {
        if (!validateFile(originalFilename)) {
            throw new IOException("Invalid file type: " + originalFilename);
        }

        String uuid = UUID.randomUUID().toString();
        String extension = FilenameUtils.getExtension(originalFilename);
        Path filePath = audioDirPath.resolve(uuid + (extension.isEmpty() ? "" : "." + extension));

        Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);

        AudioFileMetadata metadata = new AudioFileMetadata(uuid, originalFilename, ownerUUID);
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

    public void handleUploadRequest(ServerPlayer player, BlockPos blockPos, UUID transactionId, String fileName, long fileSize) {
        SimplySpeakers.LOGGER.info("Handling upload request for transaction ID: " + transactionId);

        if (fileSize > Config.maxUploadSize) {
            NetworkManager.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0, Component.literal("File is too large.")));
            SimplySpeakers.LOGGER.warn("Upload rejected for transaction ID: " + transactionId + ". File size " + fileSize + " exceeds limit of " + Config.maxUploadSize);
            return;
        }

        // Validate file extension before approving upload
        if (!validateFile(fileName)) {
            NetworkManager.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0, Component.literal("Invalid file type. Only MP3 and WAV files are supported.")));
            SimplySpeakers.LOGGER.warn("Upload rejected for transaction ID: " + transactionId + ". Invalid file type: " + fileName);
            return;
        }

        activeUploads.put(transactionId, new UploadState(fileName, fileSize, blockPos, player.getUUID().toString()));
        NetworkManager.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, true, MAX_CHUNK_SIZE, Component.literal("Upload approved")));
        SimplySpeakers.LOGGER.info("Upload approved for transaction ID: " + transactionId + ". Sent response to client.");
    }

    public void handleUploadData(ServerPlayer player, UUID transactionId, byte[] data) {
        UploadState state = activeUploads.get(transactionId);
        if (state == null) {
            SimplySpeakers.LOGGER.warn("Received upload data for unknown transaction ID: " + transactionId);
            return;
        }

        SimplySpeakers.LOGGER.info("Received data chunk for transaction ID: " + transactionId + ". Size: " + data.length);
        state.addData(data);

        if (state.isComplete()) {
            SimplySpeakers.LOGGER.info("Upload complete for transaction ID: " + transactionId + ". Saving file.");
            activeUploads.remove(transactionId);
            MinecraftServer server = player.level().getServer();
            AUDIO_FILE_EXECUTOR.execute(() -> finishUpload(player, server, transactionId, state));
        }
    }

    private void finishUpload(ServerPlayer player, MinecraftServer server, UUID transactionId, UploadState state) {
        try {
            AudioFileMetadata metadata = this.saveUploadedFile(state);
            SimplySpeakers.LOGGER.info("File saved successfully for transaction ID: " + transactionId + ". Metadata: " + metadata.getUuid());
            server.execute(() -> NetworkManager.sendToPlayer(player, new AcknowledgeUploadPacketS2C(transactionId, true, Component.literal("File uploaded successfully: " + metadata.getOriginalFilename()), state.getBlockPos())));
        } catch (IOException e) {
            SimplySpeakers.LOGGER.error("Failed to save uploaded file for transaction ID: " + transactionId, e);
            String errorMessage = "Failed to save file on server.";
            if (e.getMessage() != null && e.getMessage().startsWith("Invalid file type")) {
                errorMessage = "Invalid file type. Only MP3 and WAV files are supported.";
            }
            String finalErrorMessage = errorMessage;
            server.execute(() -> NetworkManager.sendToPlayer(player, new AcknowledgeUploadPacketS2C(transactionId, false, Component.literal(finalErrorMessage), state.getBlockPos())));
        }
    }

    private AudioFileMetadata saveUploadedFile(UploadState state) throws IOException {
        if (!validateFile(state.fileName)) {
            throw new IOException("Invalid file type: " + state.fileName);
        }

        String uuid = UUID.randomUUID().toString();
        String extension = FilenameUtils.getExtension(state.fileName);
        Path filePath = audioDirPath.resolve(uuid + (extension.isEmpty() ? "" : "." + extension));

        ChunkedFileTransfer.writeChunks(filePath, state.chunks);

        AudioFileMetadata metadata = new AudioFileMetadata(uuid, state.fileName, state.ownerUUID);
        manifest.put(uuid, metadata);
        saveManifest();

        return metadata;
    }

    public void sendAudioList(ServerPlayer player, BlockPos blockPos) {
        List<AudioFileMetadata> audioList = getAudioListForPlayer(player.getUUID().toString());
        NetworkManager.sendToPlayer(player, new SendAudioListPacketS2C(audioList));
    }

    public boolean deleteAudioFile(String audioId, String playerUUID) {
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
        return true;
    }

    public List<AudioFileMetadata> getAudioListForPlayer(String playerUUID) {
        return AudioOwnership.ownedBy(manifest.values(), AudioFileMetadata::getOwnerUUID, playerUUID);
    }

    public void sendAudioFile(ServerPlayer player, String audioId) {
        String transferKey = player.getUUID() + ":" + audioId;
        activeDownloads.tryStart(transferKey, () -> {
            Path filePath = this.getAudioFilePath(audioId);
            if (filePath == null || !Files.exists(filePath)) {
                activeDownloads.release(transferKey);
                return;
            }
            MinecraftServer server = player.level().getServer();
            AUDIO_FILE_EXECUTOR.execute(() -> sendAudioFileAsync(player, server, audioId, filePath, transferKey));
        });
    }

    private void sendAudioFileAsync(ServerPlayer player, MinecraftServer server, String audioId, Path filePath, String transferKey) {
        try {
            long fileSize = Files.size(filePath);
            if (fileSize > Config.maxUploadSize) {
                activeDownloads.release(transferKey);
                SimplySpeakers.LOGGER.warn("Refusing to send audio {} because it is {} bytes, over the configured limit of {} bytes", audioId, fileSize, Config.maxUploadSize);
                return;
            }

            ChunkedFileTransfer.streamFile(filePath, MAX_CHUNK_SIZE, (chunk, isLast) -> {
                server.execute(() -> NetworkManager.sendToPlayer(player, new SendAudioFilePacketS2C(audioId, chunk, isLast)));
            });
        } catch (IOException e) {
            activeDownloads.release(transferKey);
            SimplySpeakers.LOGGER.error("Failed to read audio file for sending", e);
        }
    }

    public Map<String, AudioFileMetadata> getManifest() {
        return manifest;
    }

    private static class UploadState {
        private final String fileName;
        private final long fileSize;
        private final BlockPos blockPos;
        private final String ownerUUID;
        private final List<byte[]> chunks = new ArrayList<>();
        private long receivedSize = 0;

        public UploadState(String fileName, long fileSize, BlockPos blockPos, String ownerUUID) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.blockPos = blockPos;
            this.ownerUUID = ownerUUID;
        }

        public void addData(byte[] data) {
            chunks.add(data);
            receivedSize += data.length;
        }

        public boolean isComplete() {
            return receivedSize >= fileSize;
        }

        public BlockPos getBlockPos() {
            return blockPos;
        }
    }
}

