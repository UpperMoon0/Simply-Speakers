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
import com.nstut.simplyspeakers.network.PacketRegistries;
import com.nstut.simplyspeakers.network.RespondUploadAudioPacketS2C;
import com.nstut.simplyspeakers.network.SendAudioFilePacketS2C;
import com.nstut.simplyspeakers.network.SendAudioListPacketS2C;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayInputStream;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AudioFileManager {
    private static final String AUDIO_DIR_NAME = "simply_speakers_audios";
    private static final String MANIFEST_FILE_NAME = "audio_manifest.json";
    private static final int MAX_CHUNK_SIZE = 1024 * 32; // 32 KB
    private static final Map<UUID, UploadState> activeUploads = new ConcurrentHashMap<>();
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

    public AudioFileMetadata saveFile(InputStream inputStream, String originalFilename) throws IOException {
        if (!validateFile(originalFilename)) {
            throw new IOException("Invalid file type: " + originalFilename);
        }

        String uuid = UUID.randomUUID().toString();
        String extension = FilenameUtils.getExtension(originalFilename);
        Path filePath = audioDirPath.resolve(uuid + (extension.isEmpty() ? "" : "." + extension));

        Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);

        AudioFileMetadata metadata = new AudioFileMetadata(uuid, originalFilename);
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
            PacketRegistries.CHANNEL.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, false, 0, Component.literal("File is too large.")));
            SimplySpeakers.LOGGER.warn("Upload rejected for transaction ID: " + transactionId + ". File size " + fileSize + " exceeds limit of " + Config.maxUploadSize);
            return;
        }

        activeUploads.put(transactionId, new UploadState(fileName, fileSize, blockPos));
        PacketRegistries.CHANNEL.sendToPlayer(player, new RespondUploadAudioPacketS2C(transactionId, true, MAX_CHUNK_SIZE, Component.literal("Upload approved")));
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
            try {
                AudioFileMetadata metadata = this.saveFile(new ByteArrayInputStream(state.getCombinedData()), state.fileName);
                SimplySpeakers.LOGGER.info("File saved successfully for transaction ID: " + transactionId + ". Metadata: " + metadata.getUuid());
                PacketRegistries.CHANNEL.sendToPlayer(player, new AcknowledgeUploadPacketS2C(transactionId, true, Component.literal("File uploaded successfully: " + metadata.getOriginalFilename()), state.getBlockPos()));
            } catch (IOException e) {
                SimplySpeakers.LOGGER.error("Failed to save uploaded file for transaction ID: " + transactionId, e);
                PacketRegistries.CHANNEL.sendToPlayer(player, new AcknowledgeUploadPacketS2C(transactionId, false, Component.literal("Failed to save file on server."), state.getBlockPos()));
            }
        }
    }

    public void sendAudioList(ServerPlayer player, BlockPos blockPos) {
        List<AudioFileMetadata> audioList = new ArrayList<>(this.getManifest().values());
        PacketRegistries.CHANNEL.sendToPlayer(player, new SendAudioListPacketS2C(audioList));
    }

    public void sendAudioFile(ServerPlayer player, String audioId) {
        Path filePath = this.getAudioFilePath(audioId);
        if (filePath == null || !Files.exists(filePath)) {
            // Handle file not found
            return;
        }

        try {
            byte[] fileData = Files.readAllBytes(filePath);
            int offset = 0;
            while (offset < fileData.length) {
                int length = Math.min(MAX_CHUNK_SIZE, fileData.length - offset);
                byte[] chunk = new byte[length];
                System.arraycopy(fileData, offset, chunk, 0, length);
                offset += length;
                boolean isLast = offset >= fileData.length;
                PacketRegistries.CHANNEL.sendToPlayer(player, new SendAudioFilePacketS2C(audioId, chunk, isLast));
            }
        } catch (IOException e) {
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
        private final List<byte[]> chunks = new ArrayList<>();
        private long receivedSize = 0;

        public UploadState(String fileName, long fileSize, BlockPos blockPos) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.blockPos = blockPos;
        }

        public void addData(byte[] data) {
            chunks.add(data);
            receivedSize += data.length;
        }

        public boolean isComplete() {
            return receivedSize >= fileSize;
        }

        public byte[] getCombinedData() {
            byte[] combined = new byte[(int) fileSize];
            int offset = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, combined, offset, chunk.length);
                offset += chunk.length;
            }
            return combined;
        }

        public BlockPos getBlockPos() {
            return blockPos;
        }
    }
}