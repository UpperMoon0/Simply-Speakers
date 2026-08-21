package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.audio.AudioGain;
import com.nstut.simplyspeakers.audio.IncrementalAudioDecoders;
import com.nstut.simplyspeakers.audio.PlaybackOffset;
import com.nstut.simplyspeakers.audio.SpatialAudioCalculator;
import com.nstut.simplyspeakers.audio.UploadProgressLogger;
import com.nstut.simplyspeakers.client.screens.SpeakerScreen;
import com.nstut.simplyspeakers.client.compat.sable.ClientSpeakerSpatialResolver;
import com.nstut.simplyspeakers.network.RequestAudioFilePacketC2S;
import com.nstut.simplyspeakers.network.RequestAudioListPacketC2S;
import com.nstut.simplyspeakers.network.UploadAudioDataPacketC2S;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientAudioPlayer {

    private static final File CACHE_DIR = new File(Minecraft.getInstance().gameDirectory, "simply_speakers_cache");
    private static final Map<String, StreamingAudioResource> networkResources = new ConcurrentHashMap<>();
    private static final Map<BlockPos, String> posToNetworkKey = new ConcurrentHashMap<>();
    private static final Map<String, Set<BlockPos>> networkToPositions = new ConcurrentHashMap<>();
    private static final Map<BlockPos, EmitterData> cachedEmitters = new ConcurrentHashMap<>();
    private static final Map<UUID, UploadProcess> activeUploads = new ConcurrentHashMap<>();
    private static final Map<UUID, Thread> activeUploadWorkers = new ConcurrentHashMap<>();
    private static final Map<String, DownloadProcess> activeDownloads = new ConcurrentHashMap<>();
    private static final Map<String, List<PlayRequest>> pendingPlays = new ConcurrentHashMap<>();
    private static final Map<String, AudioFileMetadata> audioList = new ConcurrentHashMap<>();
    private static final int NUM_BUFFERS = 3;
    private static final int BUFFER_SIZE_SECONDS = 1;
    private static final int MISSING_BLOCK_ENTITY_GRACE_TICKS = 40;

    private static class EmitterData {
        final BlockPos localPosition;
        volatile int maxRange;
        volatile float maxVolume;
        volatile float audioDropoff;
        int missingBlockEntityTicks;

        EmitterData(BlockPos localPosition, int maxRange, float maxVolume, float audioDropoff) {
            this.localPosition = localPosition.immutable();
            this.maxRange = maxRange;
            this.maxVolume = maxVolume;
            this.audioDropoff = audioDropoff;
        }
    }

    private static class StreamingAudioResource {
        final String networkKey;
        final int sourceID;
        final int[] bufferIDs;
        final Thread streamingThread;
        final AtomicBoolean stopFlag = new AtomicBoolean(false);
        final AtomicBoolean isLooping = new AtomicBoolean(false);

        StreamingAudioResource(String networkKey, int sourceID, int[] bufferIDs, Thread streamingThread, boolean initialLooping) {
            this.networkKey = networkKey;
            this.sourceID = sourceID;
            this.bufferIDs = bufferIDs;
            this.streamingThread = streamingThread;
            this.isLooping.set(initialLooping);
        }

        void stopAndCleanup() {
            stopFlag.set(true);
            if (streamingThread != null && streamingThread.isAlive()) {
                streamingThread.interrupt();
                Thread cleanupThread = new Thread(() -> {
                    try {
                        streamingThread.join(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    Minecraft.getInstance().tell(this::cleanupOpenALResources);
                }, SimplySpeakers.MOD_ID + "-cleanup-" + networkKey);
                cleanupThread.setDaemon(true);
                cleanupThread.start();
            } else {
                Minecraft.getInstance().tell(this::cleanupOpenALResources);
            }
        }

        private void cleanupOpenALResources() {
            try {
                if (AL10.alIsSource(sourceID)) {
                    AL10.alSourceStop(sourceID);
                    AL10.alSourcei(sourceID, AL10.AL_BUFFER, 0);
                    AL10.alDeleteSources(sourceID);
                    AL10.alDeleteBuffers(bufferIDs);
                    SimplySpeakers.LOGGER.debug("Cleanup completed for source {} (network {})", sourceID, networkKey);
                }
            } catch (Exception e) {
                SimplySpeakers.LOGGER.error("Error during OpenAL cleanup for source {} (network {})", sourceID, networkKey, e);
            }
        }
    }

    public static String resolveNetworkKey(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            net.minecraft.world.level.block.entity.BlockEntity blockEntity = mc.level.getBlockEntity(pos);
            if (blockEntity instanceof com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity speaker) {
                String id = speaker.getSpeakerId();
                if (id != null && !id.trim().isEmpty()) {
                    return "net_" + id.trim();
                }
            } else if (blockEntity instanceof com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity proxy) {
                String id = proxy.getSpeakerId();
                if (id != null && !id.trim().isEmpty()) {
                    return "net_" + id.trim();
                }
            }
        }
        return "pos_" + pos.asLong();
    }

    public static void play(BlockPos pos, String speakerId, AudioFileMetadata metadata, float startPositionSeconds, boolean isLooping, int maxRange, float maxVolume, float audioDropoff) {
        String networkKey = (speakerId != null && !speakerId.trim().isEmpty())
                ? "net_" + speakerId.trim()
                : "pos_" + pos.asLong();

        SimplySpeakers.LOGGER.debug("CLIENT: play called for pos: {}, speakerId: '{}', networkKey: {}, audioId: {}, start: {}s, looping: {}, range: {}, volume: {}, dropoff: {}",
                pos, speakerId, networkKey, metadata.getUuid(), startPositionSeconds, isLooping, maxRange, maxVolume, audioDropoff);

        cachedEmitters.put(pos, new EmitterData(pos, maxRange, maxVolume, audioDropoff));

        String oldKey = posToNetworkKey.put(pos, networkKey);
        if (oldKey != null && !oldKey.equals(networkKey)) {
            Set<BlockPos> oldPositions = networkToPositions.get(oldKey);
            if (oldPositions != null) {
                oldPositions.remove(pos);
                if (oldPositions.isEmpty()) {
                    networkToPositions.remove(oldKey);
                    StreamingAudioResource oldRes = networkResources.remove(oldKey);
                    if (oldRes != null) {
                        oldRes.stopAndCleanup();
                    }
                }
            }
        }

        networkToPositions.computeIfAbsent(networkKey, k -> ConcurrentHashMap.newKeySet()).add(pos);

        StreamingAudioResource existing = networkResources.get(networkKey);
        if (existing != null && !existing.stopFlag.get() && existing.streamingThread != null && existing.streamingThread.isAlive()) {
            SimplySpeakers.LOGGER.debug("CLIENT: Network {} already actively streaming. Attached pos {} without duplicate stream.", networkKey, pos);
            updateSpeakerVolumes();
            return;
        }

        if (!CACHE_DIR.exists()) {
            CACHE_DIR.mkdirs();
        }

        String extension = com.google.common.io.Files.getFileExtension(metadata.getOriginalFilename());
        File cachedFile = new File(CACHE_DIR, metadata.getUuid() + (extension.isEmpty() ? "" : "." + extension));
        if (cachedFile.exists()) {
            ClientCacheManager.recordAccess(cachedFile);
            SimplySpeakers.LOGGER.debug("CLIENT: Cached file found for {}. Playing from file.", metadata.getUuid());
            playFromFile(networkKey, pos, cachedFile.getAbsolutePath(), startPositionSeconds, isLooping);
        } else {
            SimplySpeakers.LOGGER.info("CLIENT: Cached file not found for {}. Requesting from server.", metadata.getUuid());
            pendingPlays.computeIfAbsent(metadata.getUuid(), k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(new PlayRequest(pos, networkKey, startPositionSeconds, isLooping));
            requestFileFromServer(metadata.getUuid(), metadata.getOriginalFilename());
        }
    }

    public static void setLooping(String networkKey, boolean looping) {
        StreamingAudioResource res = networkResources.get(networkKey);
        if (res != null) {
            res.isLooping.set(looping);
            SimplySpeakers.LOGGER.debug("CLIENT: Updated live loop state for network {} to {}", networkKey, looping);
        }
    }

    public static void play(BlockPos pos, String speakerId, AudioFileMetadata metadata, float startPositionSeconds, boolean isLooping) {
        play(pos, speakerId, metadata, startPositionSeconds, isLooping, Config.speakerRange, 1.0f, 1.0f);
    }

    public static void play(BlockPos pos, AudioFileMetadata metadata, float startPositionSeconds, boolean isLooping) {
        play(pos, null, metadata, startPositionSeconds, isLooping, Config.speakerRange, 1.0f, 1.0f);
    }

    private static void playFromFile(String networkKey, BlockPos pos, String filePath, float startPositionSeconds, boolean isLooping) {
        SimplySpeakers.LOGGER.debug("CLIENT: playFromFile: networkKey={}, pos={}, filePath={}, start={}, isLooping={}",
                networkKey, pos, filePath, startPositionSeconds, isLooping);
        Minecraft.getInstance().tell(() -> {
            try {
                StreamingAudioResource existing = networkResources.get(networkKey);
                if (existing != null && !existing.stopFlag.get() && existing.streamingThread != null && existing.streamingThread.isAlive()) {
                    SimplySpeakers.LOGGER.debug("CLIENT: Stream already active for networkKey={}", networkKey);
                    return;
                }

                int sourceID = AL10.alGenSources();
                int[] bufferIDs = new int[NUM_BUFFERS];
                AL10.alGenBuffers(bufferIDs);

                AL10.alSource3f(sourceID, AL10.AL_POSITION, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f);
                AL10.alSourcef(sourceID, AL10.AL_ROLLOFF_FACTOR, 0.0f);
                AL10.alSourcef(sourceID, AL10.AL_GAIN, 0.0f);
                AL10.alSourcei(sourceID, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);

                Thread streamingThread = new Thread(() -> streamAudioData(networkKey, sourceID, bufferIDs, filePath, startPositionSeconds, isLooping),
                        SimplySpeakers.MOD_ID + "-stream-" + networkKey);
                streamingThread.setDaemon(true);

                StreamingAudioResource resource = new StreamingAudioResource(networkKey, sourceID, bufferIDs, streamingThread, isLooping);
                networkResources.put(networkKey, resource);
                streamingThread.start();

                updateSpeakerVolumes();
            } catch (Exception e) {
                SimplySpeakers.LOGGER.error("CLIENT: Failed to start audio playback for network {}", networkKey, e);
            }
        });
    }

    private static long skipFully(InputStream in, long n) throws IOException {
        long remaining = n;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                break;
            }
            remaining -= skipped;
        }
        return n - remaining;
    }

    private static void streamAudioData(String networkKey, int sourceID, int[] bufferIDs, String filePath, float startPositionSeconds, boolean isLooping) {
        StreamingAudioResource resource = networkResources.get(networkKey);
        boolean continueStreaming = true;

        while (continueStreaming) {
            if (resource == null || resource.sourceID != sourceID) {
                break;
            }

            AudioInputStream pcmAudioStream = null;
            boolean initialDataLoaded = false;
            boolean playbackCompletedSuccessfully = false;

            try {
                File audioFile = new File(filePath);
                if (!audioFile.exists()) {
                    SimplySpeakers.LOGGER.error("Streaming thread ERROR: Audio file not found: {} for network {}", filePath, networkKey);
                    resource.stopFlag.set(true);
                    break;
                }

                pcmAudioStream = IncrementalAudioDecoders.openPcmStream(audioFile);
                if (pcmAudioStream == null) {
                    resource.stopFlag.set(true);
                    break;
                }

                AudioFormat format = pcmAudioStream.getFormat();
                if (startPositionSeconds > 0 && continueStreaming) {
                    float frameRate = format.getFrameRate();
                    int frameSize = format.getFrameSize();

                    if (frameRate > 0 && frameSize > 0) {
                        long framesToSkip = PlaybackOffset.frameOffset(
                                startPositionSeconds,
                                isLooping,
                                pcmAudioStream.getFrameLength(),
                                frameRate);
                        long bytesToSkip = framesToSkip * frameSize;
                        if (bytesToSkip > 0) {
                            skipFully(pcmAudioStream, bytesToSkip);
                        }
                    }
                    startPositionSeconds = 0;
                }

                boolean playbackAttempted = false;
                boolean endOfStream = false;

                int alFormat = AL10.AL_FORMAT_MONO16;
                int bufferSizeBytes = (int) (format.getFrameRate() * format.getFrameSize() * BUFFER_SIZE_SECONDS);
                byte[] bufferData = new byte[bufferSizeBytes];

                for (int i = 0; i < NUM_BUFFERS; i++) {
                    if (resource.stopFlag.get() || Thread.currentThread().isInterrupted()) {
                        continueStreaming = false;
                        break;
                    }

                    int bytesRead = pcmAudioStream.read(bufferData, 0, bufferData.length);
                    if (bytesRead <= 0) {
                        endOfStream = true;
                        break;
                    }

                    ByteBuffer alBuffer = ByteBuffer.allocateDirect(bytesRead).order(ByteOrder.nativeOrder());
                    alBuffer.put(bufferData, 0, bytesRead).flip();

                    AL10.alBufferData(bufferIDs[i], alFormat, alBuffer, (int) format.getSampleRate());
                    AL10.alSourceQueueBuffers(sourceID, bufferIDs[i]);
                    initialDataLoaded = true;

                    if (!playbackAttempted) {
                        AL10.alSourcePlay(sourceID);
                        playbackAttempted = true;
                    }
                }
                if (!continueStreaming) break;

                if (!playbackAttempted && initialDataLoaded) {
                    if (!resource.stopFlag.get() && !Thread.currentThread().isInterrupted()) {
                        int queued = AL10.alGetSourcei(sourceID, AL10.AL_BUFFERS_QUEUED);
                        if (queued > 0 && AL10.alGetSourcei(sourceID, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                            AL10.alSourcePlay(sourceID);
                            playbackAttempted = true;
                        }
                    }
                }

                if (!playbackAttempted) {
                    boolean currentlyLooping = resource != null ? resource.isLooping.get() : isLooping;
                    if (!currentlyLooping) resource.stopFlag.set(true);
                    continueStreaming = currentlyLooping;
                    break;
                }

                while (playbackAttempted && !resource.stopFlag.get() && !Thread.currentThread().isInterrupted()) {
                    int buffersProcessed = AL10.alGetSourcei(sourceID, AL10.AL_BUFFERS_PROCESSED);

                    for (int i = 0; i < buffersProcessed; i++) {
                        int bufferID = AL10.alSourceUnqueueBuffers(sourceID);
                        if (!endOfStream) {
                            int bytesRead = pcmAudioStream.read(bufferData, 0, bufferData.length);
                            if (bytesRead > 0) {
                                ByteBuffer alBuffer = ByteBuffer.allocateDirect(bytesRead).order(ByteOrder.nativeOrder());
                                alBuffer.put(bufferData, 0, bytesRead).flip();
                                AL10.alBufferData(bufferID, alFormat, alBuffer, (int) format.getSampleRate());
                                AL10.alSourceQueueBuffers(sourceID, bufferID);
                            } else {
                                endOfStream = true;
                            }
                        }
                    }
                    if (resource.stopFlag.get() || Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    int queuedBuffers = AL10.alGetSourcei(sourceID, AL10.AL_BUFFERS_QUEUED);
                    if (endOfStream) {
                        SimplySpeakers.LOGGER.debug("Draining queued audio before restart for source {}", sourceID);
                    }
                    if (endOfStream && queuedBuffers == 0) {
                        playbackCompletedSuccessfully = true;
                        break;
                    }

                    if (AL10.alGetSourcei(sourceID, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING && initialDataLoaded) {
                        if (queuedBuffers > 0) {
                            AL10.alSourcePlay(sourceID);
                        }
                    }

                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        resource.stopFlag.set(true);
                        break;
                    }
                }

                if (resource.stopFlag.get() || Thread.currentThread().isInterrupted()) {
                    continueStreaming = false;
                } else if (playbackCompletedSuccessfully) {
                    boolean currentlyLooping = resource != null ? resource.isLooping.get() : isLooping;
                    if (currentlyLooping) {
                        SimplySpeakers.LOGGER.debug("Audio track finished for {}. Looping enabled, restarting.", networkKey);
                        if (AL10.alIsSource(sourceID)) {
                            AL10.alSourceStop(sourceID);
                            int queued = AL10.alGetSourcei(sourceID, AL10.AL_BUFFERS_QUEUED);
                            if (queued > 0) AL10.alSourceUnqueueBuffers(sourceID, new int[queued]);
                        }
                        playbackCompletedSuccessfully = false;
                        initialDataLoaded = false;
                        // The outer while loop will re-initialize
                    } else {
                        resource.stopFlag.set(true);
                        continueStreaming = false;
                    }
                } else {
                    boolean currentlyLooping = resource != null ? resource.isLooping.get() : isLooping;
                    if (!currentlyLooping) {
                        resource.stopFlag.set(true);
                    }
                    continueStreaming = currentlyLooping && !resource.stopFlag.get();
                }

            } catch (UnsupportedAudioFileException | IOException e) {
                SimplySpeakers.LOGGER.error("Streaming thread error for network {} with file {}", networkKey, filePath, e);
                if (resource != null) resource.stopFlag.set(true);
                continueStreaming = false;
            } catch (Exception e) {
                SimplySpeakers.LOGGER.error("Critical error in streaming thread for network {}", networkKey, e);
                if (resource != null) resource.stopFlag.set(true);
                continueStreaming = false;
            } finally {
                if (pcmAudioStream != null) {
                    try {
                        pcmAudioStream.close();
                    } catch (IOException ignored) {}
                }
                if (!continueStreaming && resource != null && !resource.stopFlag.get()) {
                    resource.stopFlag.set(true);
                }
            }

            if (resource != null && resource.stopFlag.get()) {
                continueStreaming = false;
            }
            if (Thread.currentThread().isInterrupted()) {
                continueStreaming = false;
                if (resource != null) resource.stopFlag.set(true);
            }
        }

        // Clean up when thread finishes naturally (e.g. non-looping track reached EOF)
        if (resource != null) {
            networkResources.remove(networkKey, resource);
            resource.stopAndCleanup();
        }
    }

    public static void stop(BlockPos pos) {
        for (List<PlayRequest> requests : pendingPlays.values()) {
            requests.removeIf(req -> req.pos.equals(pos));
        }

        cachedEmitters.remove(pos);
        String networkKey = posToNetworkKey.remove(pos);
        if (networkKey != null) {
            Set<BlockPos> positions = networkToPositions.get(networkKey);
            if (positions != null) {
                positions.remove(pos);
                if (positions.isEmpty()) {
                    networkToPositions.remove(networkKey);
                    StreamingAudioResource resource = networkResources.remove(networkKey);
                    if (resource != null) {
                        resource.stopAndCleanup();
                    }
                } else {
                    updateSpeakerVolumes();
                }
            }
        }
    }

    public static void stopNetwork(String networkKey) {
        for (List<PlayRequest> requests : pendingPlays.values()) {
            requests.removeIf(req -> networkKey.equals(req.networkKey));
        }
        Set<BlockPos> positions = networkToPositions.remove(networkKey);
        if (positions != null) {
            for (BlockPos pos : positions) {
                posToNetworkKey.remove(pos, networkKey);
                cachedEmitters.remove(pos);
            }
        }
        StreamingAudioResource resource = networkResources.remove(networkKey);
        if (resource != null) resource.stopAndCleanup();
    }

    public static void stopAll() {
        pendingPlays.clear();
        for (DownloadProcess download : activeDownloads.values()) {
            download.cleanup();
        }
        activeDownloads.clear();
        for (Thread worker : activeUploadWorkers.values()) {
            try {
                worker.interrupt();
            } catch (Exception ignored) {}
        }
        activeUploadWorkers.clear();
        activeUploads.clear();

        List<StreamingAudioResource> resourcesToStop = new ArrayList<>(networkResources.values());
        cachedEmitters.clear();
        posToNetworkKey.clear();
        networkToPositions.clear();
        networkResources.clear();

        if (!resourcesToStop.isEmpty()) {
            Thread batchCleanupThread = new Thread(() -> {
                for (StreamingAudioResource resource : resourcesToStop) {
                    try {
                        if (resource != null) {
                            resource.stopAndCleanup();
                        }
                    } catch (Exception ignored) {}
                }
            }, SimplySpeakers.MOD_ID + "-batch-cleanup");
            batchCleanupThread.setDaemon(true);
            batchCleanupThread.start();
        }
    }

    /** Refreshes block-entity-backed settings and membership at client tick rate. */
    public static void updateEmitterState() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        for (Map.Entry<String, StreamingAudioResource> entry : new ArrayList<>(networkResources.entrySet())) {
            String networkKey = entry.getKey();
            StreamingAudioResource resource = entry.getValue();
            if (resource == null || resource.stopFlag.get()) continue;

            Set<BlockPos> positions = networkToPositions.get(networkKey);
            if (positions == null || positions.isEmpty()) {
                resource.stopAndCleanup();
                networkResources.remove(networkKey);
                continue;
            }

            List<BlockPos> deadPositions = new ArrayList<>();

            for (BlockPos speakerPos : positions) {
                EmitterData data = cachedEmitters.get(speakerPos);
                if (mc.level.hasChunkAt(speakerPos)) {
                    net.minecraft.world.level.block.entity.BlockEntity blockEntity = mc.level.getBlockEntity(speakerPos);
                    if (blockEntity instanceof com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity speakerBlockEntity) {
                        data = cachedEmitters.computeIfAbsent(speakerPos, p ->
                                new EmitterData(p, speakerBlockEntity.getMaxRange(), speakerBlockEntity.getMaxVolume(), speakerBlockEntity.getAudioDropoff()));
                        data.maxVolume = speakerBlockEntity.getMaxVolume();
                        data.maxRange = Math.min(speakerBlockEntity.getMaxRange(), Config.speakerRange);
                        data.audioDropoff = speakerBlockEntity.getAudioDropoff();
                        data.missingBlockEntityTicks = 0;
                    } else if (blockEntity instanceof com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity proxySpeakerBlockEntity) {
                        data = cachedEmitters.computeIfAbsent(speakerPos, p ->
                                new EmitterData(p, proxySpeakerBlockEntity.getMaxRange(), proxySpeakerBlockEntity.getMaxVolume(), proxySpeakerBlockEntity.getAudioDropoff()));
                        data.maxVolume = proxySpeakerBlockEntity.getMaxVolume();
                        data.maxRange = Math.min(proxySpeakerBlockEntity.getMaxRange(), Config.speakerRange);
                        data.audioDropoff = proxySpeakerBlockEntity.getAudioDropoff();
                        data.missingBlockEntityTicks = 0;
                    } else if (data != null && ++data.missingBlockEntityTicks >= MISSING_BLOCK_ENTITY_GRACE_TICKS) {
                        deadPositions.add(speakerPos);
                    }
                }
            }

            for (BlockPos dead : deadPositions) {
                positions.remove(dead);
                posToNetworkKey.remove(dead);
                cachedEmitters.remove(dead);
            }
        }
    }

    /** Updates only render-pose transforms, blend math, and OpenAL state each world frame. */
    public static void updateSpatialAudio() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || networkResources.isEmpty()) return;

        Vec3 listenerPosition = mc.gameRenderer.getMainCamera().getPosition();
        float masterVolume = mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER);
        float recordVolume = mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.RECORDS);

        for (Map.Entry<String, StreamingAudioResource> entry : networkResources.entrySet()) {
            String networkKey = entry.getKey();
            StreamingAudioResource resource = entry.getValue();
            if (resource == null || resource.stopFlag.get()) continue;

            Set<BlockPos> positions = networkToPositions.get(networkKey);
            if (positions == null || positions.isEmpty()) continue;

            double weightedX = 0.0;
            double weightedY = 0.0;
            double weightedZ = 0.0;
            float totalWeight = 0.0f;
            float maxGain = 0.0f;
            Vec3 firstResolvedPosition = null;

            for (BlockPos speakerPos : positions) {
                EmitterData emitter = cachedEmitters.get(speakerPos);
                if (emitter == null) continue;
                Vec3 renderPosition = ClientSpeakerSpatialResolver.resolveRender(mc.level, emitter.localPosition);
                if (renderPosition == null) continue;
                if (firstResolvedPosition == null) firstResolvedPosition = renderPosition;

                double distance = renderPosition.distanceTo(listenerPosition);
                float gain = SpatialAudioCalculator.calculateDistanceGain(
                        distance, emitter.maxRange, emitter.maxVolume, emitter.audioDropoff);
                if (gain <= 0.0f) continue;
                weightedX += renderPosition.x * gain;
                weightedY += renderPosition.y * gain;
                weightedZ += renderPosition.z * gain;
                totalWeight += gain;
                maxGain = Math.max(maxGain, gain);
            }

            float finalGain = AudioGain.applyGameVolume(maxGain, masterVolume, recordVolume);
            try {
                if (AL10.alIsSource(resource.sourceID)) {
                    if (totalWeight > 0.0f) {
                        AL10.alSource3f(resource.sourceID, AL10.AL_POSITION,
                                (float) (weightedX / totalWeight),
                                (float) (weightedY / totalWeight),
                                (float) (weightedZ / totalWeight));
                    } else if (firstResolvedPosition != null) {
                        AL10.alSource3f(resource.sourceID, AL10.AL_POSITION,
                                (float) firstResolvedPosition.x,
                                (float) firstResolvedPosition.y,
                                (float) firstResolvedPosition.z);
                    }
                    AL10.alSourcef(resource.sourceID, AL10.AL_GAIN, finalGain);
                }
            } catch (Exception e) {
                SimplySpeakers.LOGGER.error("Error setting spatial audio for source {}", resource.sourceID, e);
            }
        }
    }

    /** Retained for packet handlers that need an immediate full refresh. */
    public static void updateSpeakerVolumes() {
        updateEmitterState();
        updateSpatialAudio();
    }

    public static UUID startUpload(File file) {
        UUID transactionId = UUID.randomUUID();
        SimplySpeakers.LOGGER.debug("Starting upload process for file: {} with transaction ID: {}", file.getName(), transactionId);
        activeUploads.put(transactionId, new UploadProcess(file));
        return transactionId;
    }

    public static void handleUploadResponse(UUID transactionId, boolean allowed, int maxChunkSize, Component message) {
        UploadProcess process = activeUploads.get(transactionId);
        if (process == null) {
            return;
        }

        if (allowed) {
            process.start(transactionId, maxChunkSize);
        } else {
            activeUploads.remove(transactionId);
            Screen currentScreen = Minecraft.getInstance().screen;
            if (currentScreen instanceof SpeakerScreen) {
                ((SpeakerScreen) currentScreen).setStatusMessage(message);
            }
        }
    }

    public static void handleUploadAcknowledgement(UUID transactionId, boolean success, Component message, BlockPos blockPos) {
        if (success) {
            NetworkManager.sendToServer(new RequestAudioListPacketC2S(blockPos));
        }
        activeUploads.remove(transactionId);
        Screen currentScreen = Minecraft.getInstance().screen;
        if (currentScreen instanceof SpeakerScreen) {
            ((SpeakerScreen) currentScreen).setStatusMessage(message);
        }
    }

    private static void requestFileFromServer(String audioId, String filename) {
        if (activeDownloads.containsKey(audioId)) {
            return;
        }
        try {
            activeDownloads.put(audioId, new DownloadProcess(audioId, filename));
            NetworkManager.sendToServer(new RequestAudioFilePacketC2S(audioId));
        } catch (IOException e) {
            SimplySpeakers.LOGGER.error("Failed to initialize download process for {}", audioId, e);
        }
    }

    public static void clearAudioList() {
        audioList.clear();
        pendingPlays.clear();
    }

    public static void setAudioList(List<AudioFileMetadata> newAudioList) {
        audioList.clear();
        for (AudioFileMetadata audio : newAudioList) {
            audioList.put(audio.getUuid(), audio);
        }
    }

    public static void handleAudioFileChunk(String audioId, byte[] data, boolean isLast) {
        DownloadProcess process = activeDownloads.get(audioId);
        if (process == null) {
            return;
        }

        try {
            process.addData(data);
            if (isLast) {
                process.complete();
                activeDownloads.remove(audioId);
            }
        } catch (IOException e) {
            SimplySpeakers.LOGGER.error("Failed writing download chunk for {}", audioId, e);
            process.cleanup();
            activeDownloads.remove(audioId);
        }
    }

    private static class UploadProcess {
        private final File file;

        public UploadProcess(File file) {
            this.file = file;
        }

        public void start(UUID transactionId, int chunkSize) {
            Thread uploadThread = new Thread(() -> {
                try {
                    long totalLength = file.length();
                    UploadProgressLogger.logStart(SimplySpeakers.LOGGER, transactionId, totalLength);
                    try (InputStream in = new FileInputStream(file)) {
                        byte[] buffer = new byte[chunkSize];
                        int read;
                        long offset = 0;
                        while ((read = in.read(buffer)) > 0) {
                            if (Thread.currentThread().isInterrupted()) {
                                break;
                            }
                            byte[] chunk = new byte[read];
                            System.arraycopy(buffer, 0, chunk, 0, read);
                            UploadProgressLogger.logChunk(SimplySpeakers.LOGGER, transactionId, offset, read, totalLength);
                            NetworkManager.sendToServer(new UploadAudioDataPacketC2S(transactionId, chunk));
                            offset += read;
                            try {
                                Thread.sleep(5);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    } catch (IOException e) {
                        SimplySpeakers.LOGGER.error("Failed to stream file for upload: {}", file.getName(), e);
                    }
                } finally {
                    activeUploadWorkers.remove(transactionId);
                    activeUploads.remove(transactionId);
                }
            }, SimplySpeakers.MOD_ID + "-upload-" + transactionId);
            uploadThread.setDaemon(true);
            activeUploadWorkers.put(transactionId, uploadThread);
            uploadThread.start();
        }
    }

    private static class DownloadProcess {
        private final String audioId;
        private final String filename;
        private final File partFile;
        private final OutputStream dataStream;

        public DownloadProcess(String audioId, String filename) throws IOException {
            this.audioId = audioId;
            this.filename = filename;
            if (!CACHE_DIR.exists()) {
                CACHE_DIR.mkdirs();
            }
            this.partFile = new File(CACHE_DIR, audioId + ".part");
            this.dataStream = new FileOutputStream(partFile);
        }

        public synchronized void addData(byte[] data) throws IOException {
            dataStream.write(data);
        }

        public synchronized void cleanup() {
            try {
                dataStream.close();
            } catch (IOException ignored) {}
            try {
                Files.deleteIfExists(partFile.toPath());
            } catch (IOException ignored) {}
        }

        public synchronized void complete() {
            try {
                dataStream.flush();
                dataStream.close();

                String extension = com.google.common.io.Files.getFileExtension(filename);
                File cachedFile = new File(CACHE_DIR, audioId + (extension.isEmpty() ? "" : "." + extension));

                try {
                    Files.move(partFile.toPath(), cachedFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(partFile.toPath(), cachedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                ClientCacheManager.recordAccess(cachedFile);
                ClientCacheManager.enforceBudget(CACHE_DIR);

                List<PlayRequest> requests = pendingPlays.remove(audioId);
                if (requests != null && !requests.isEmpty()) {
                    Map<String, PlayRequest> requestsByNetwork = new LinkedHashMap<>();
                    for (PlayRequest req : requests) {
                        String currentNetworkKey = posToNetworkKey.get(req.pos);
                        if (req.networkKey != null && req.networkKey.equals(currentNetworkKey)) {
                            requestsByNetwork.putIfAbsent(req.networkKey, req);
                        }
                    }

                    for (Map.Entry<String, PlayRequest> entry : requestsByNetwork.entrySet()) {
                        String netKey = entry.getKey();
                        PlayRequest req = entry.getValue();
                        boolean liveLooping = ClientSpeakerRegistry.getLooping(netKey, req.isLooping);
                        playFromFile(netKey, req.pos, cachedFile.getAbsolutePath(), req.startPositionSeconds, liveLooping);
                    }
                }
            } catch (IOException e) {
                SimplySpeakers.LOGGER.error("Failed completing download for audio {}", audioId, e);
                cleanup();
            }
        }
    }

    private static class PlayRequest {
        final BlockPos pos;
        final String networkKey;
        final float startPositionSeconds;
        final boolean isLooping;

        PlayRequest(BlockPos pos, String networkKey, float startPositionSeconds, boolean isLooping) {
            this.pos = pos;
            this.networkKey = networkKey;
            this.startPositionSeconds = startPositionSeconds;
            this.isLooping = isLooping;
        }
    }
}
