package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.audio.AudioContextGate;
import com.nstut.simplyspeakers.audio.AudioGain;
import com.nstut.simplyspeakers.audio.IncrementalAudioDecoders;
import com.nstut.simplyspeakers.audio.PlaybackOffset;
import com.nstut.simplyspeakers.audio.PlaybackTimeline;
import com.nstut.simplyspeakers.audio.LoopingSeekRecovery;
import com.nstut.simplyspeakers.audio.SpatialAudioCalculator;
import com.nstut.simplyspeakers.audio.UploadProgressLogger;
import com.nstut.simplyspeakers.client.screens.SpeakerScreen;
import com.nstut.simplyspeakers.network.RequestAudioFilePacketC2S;
import com.nstut.simplyspeakers.network.RemoteStreamEofPacketC2S;
import com.nstut.simplyspeakers.network.RequestAudioListPacketC2S;
import com.nstut.simplyspeakers.network.UploadAudioDataPacketC2S;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
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

    private static final PlaybackMembership<BlockPos> membership = new PlaybackMembership<>();
    private static final AudioContextGate audioContext = new AudioContextGate();
    private static final Map<String, PlaybackIntent> playbackIntents = new ConcurrentHashMap<>();

    /** Logical playback survives device changes; numeric OpenAL resources do not. */
    private static final class PlaybackIntent {
        final AudioFileMetadata metadata;
        final PlaybackTimeline timeline;
        final String fullStateKey;
        final int generation;
        volatile boolean looping;
        volatile long frameLengthHint = -1;

        PlaybackIntent(AudioFileMetadata metadata, float offset, boolean looping, String key, int generation) {
            this.metadata = metadata;
            this.timeline = new PlaybackTimeline(offset);
            this.looping = looping;
            this.fullStateKey = key;
            this.generation = generation;
        }

        float offsetSeconds() {
            return timeline.offsetSeconds();
        }

        boolean matches(AudioFileMetadata other, String key, int generation) {
            return metadata.getUuid().equals(other.getUuid())
                    && java.util.Objects.equals(fullStateKey, key) && this.generation == generation;
        }
    }

    public static long contextEpoch() { return audioContext.epoch(); }
    public static boolean contextAvailable() { return audioContext.isAvailable(); }

    /** Called before vanilla touches the old context, including device-loss reloads. */
    public static void soundContextDestroying() {
        audioContext.suspend(); // Wait only for in-flight AL operations, never decoder/network reads.
        for (StreamingAudioResource resource : new ArrayList<>(networkResources.values())) {
            if (networkResources.remove(resource.networkKey, resource)) resource.stopAndCleanup();
        }
    }

    /** Called only after vanilla successfully initialized its replacement context. */
    public static void soundContextReady() {
        audioContext.resume();
        long epoch = audioContext.epoch();
        Minecraft.getInstance().execute(() -> {
            if (!audioContext.isCurrent(epoch)) return;
            for (var entry : new ArrayList<>(playbackIntents.entrySet())) {
                Set<BlockPos> positions = membership.getPositions(entry.getKey());
                if (!positions.isEmpty()) startIntent(entry.getKey(), positions.iterator().next(), entry.getValue());
                else playbackIntents.remove(entry.getKey(), entry.getValue());
            }
        });
    }

    private static final File CACHE_DIR = new File(Minecraft.getInstance().gameDirectory, "simply_speakers_cache");
    private static final Map<String, StreamingAudioResource> networkResources = new ConcurrentHashMap<>();
    private static final Map<UUID, UploadProcess> activeUploads = new ConcurrentHashMap<>();
    private static final Map<UUID, Thread> activeUploadWorkers = new ConcurrentHashMap<>();
    private static final Map<String, DownloadProcess> activeDownloads = new ConcurrentHashMap<>();
    private static final Map<String, List<PlayRequest>> pendingPlays = new ConcurrentHashMap<>();
    private static final Map<String, AudioFileMetadata> audioList = new ConcurrentHashMap<>();
    private static final int NUM_BUFFERS = 3;
    private static final int BUFFER_SIZE_SECONDS = 1;
    private static final int MAX_REDIRECTS = 3;

    private static final Map<BlockPos, com.nstut.simplyspeakers.audio.DirectionalAudio.Extras> directionalExtras = new ConcurrentHashMap<>();

    private static class StreamingAudioResource {
        final String networkKey;
        final long contextEpoch = audioContext.epoch();
        final PlaybackIntent intent;
        final int sourceID;
        volatile long decodedBytes;
        volatile float startOffsetSeconds;
        final LoopingSeekRecovery loopingSeekRecovery = new LoopingSeekRecovery();
        long seekKnownFrameLength, seekRequestedFrames, seekSkippedFrames;
        long discoveredFrameLength = -1;
        final int[] bufferIDs;
        Thread streamingThread;
        final AtomicBoolean stopFlag = new AtomicBoolean(false);
        final AtomicBoolean cleanupScheduled = new AtomicBoolean(false);
        final AtomicBoolean isLooping = new AtomicBoolean(false);
        /** Server-provided state identity reported back on remote stream EOF; null for local files. */
        final String eofFullStateKey;
        final int playbackGeneration;

        StreamingAudioResource(String networkKey, int sourceID, int[] bufferIDs, Thread streamingThread, boolean initialLooping) {
            this(networkKey, sourceID, bufferIDs, streamingThread, initialLooping, null, 0);
        }

        StreamingAudioResource(String networkKey, int sourceID, int[] bufferIDs, Thread streamingThread, boolean initialLooping,
                String eofFullStateKey, int playbackGeneration) {
            this.networkKey = networkKey;
            this.intent = playbackIntents.get(networkKey);
            this.sourceID = sourceID;
            this.bufferIDs = bufferIDs;
            this.streamingThread = streamingThread;
            this.isLooping.set(initialLooping);
            this.eofFullStateKey = eofFullStateKey;
            this.playbackGeneration = playbackGeneration;
        }

        private final java.util.List<java.io.Closeable> inputs = new java.util.ArrayList<>();

        void checkActive() throws IOException {
            if (!audioContext.isCurrent(contextEpoch) || stopFlag.get() || Thread.currentThread().isInterrupted() || networkResources.get(networkKey) != this)
                throw new java.io.InterruptedIOException("Playback cancelled");
        }

        private <T> T al(java.util.function.Supplier<T> operation) {
            return audioContext.call(contextEpoch, () -> {
                if (stopFlag.get() || networkResources.get(networkKey) != this
                        || (intent != null && playbackIntents.get(networkKey) != intent))
                    throw new AudioContextGate.StaleContextException();
                return operation.get();
            });
        }

        private void al(Runnable operation) { al(() -> { operation.run(); return null; }); }
        int sourceInt(int parameter) { return al(() -> AL10.alGetSourcei(sourceID, parameter)); }
        boolean isSource() { return al(() -> AL10.alIsSource(sourceID)); }
        void playSource() { al(() -> AL10.alSourcePlay(sourceID)); }
        void stopSource() { al(() -> AL10.alSourceStop(sourceID)); }
        void bufferData(int buffer, int format, ByteBuffer data, int rate) { al(() -> AL10.alBufferData(buffer, format, data, rate)); }
        void queueBuffer(int buffer) { al(() -> AL10.alSourceQueueBuffers(sourceID, buffer)); }
        int unqueueBuffer() { return al(() -> AL10.alSourceUnqueueBuffers(sourceID)); }
        void unqueueBuffers(int[] buffers) { al(() -> AL10.alSourceUnqueueBuffers(sourceID, buffers)); }
        void position(float x, float y, float z) { al(() -> AL10.alSource3f(sourceID, AL10.AL_POSITION, x, y, z)); }
        void gain(float gain) { al(() -> AL10.alSourcef(sourceID, AL10.AL_GAIN, gain)); }
        void restartLoopTimeline() {
            al(() -> { if (intent != null) intent.timeline.restartLoop(); });
        }

        /** Decode/skip outside the context lock. A true result requests one bounded reopen. */
        boolean seek(AudioInputStream stream, float offset) throws IOException {
            AudioFormat format = stream.getFormat();
            int frameSize = format.getFrameSize();
            seekKnownFrameLength = stream.getFrameLength();
            if (seekKnownFrameLength <= 0) seekKnownFrameLength = intent == null ? discoveredFrameLength : intent.frameLengthHint;
            seekRequestedFrames = PlaybackOffset.frameOffset(offset, isLooping.get(), seekKnownFrameLength, format.getFrameRate());
            seekSkippedFrames = 0;
            if (frameSize <= 0 || seekRequestedFrames <= 0) return false;
            seekRequestedFrames = Math.min(seekRequestedFrames, Long.MAX_VALUE / frameSize);
            long bytesRequested = seekRequestedFrames * frameSize;
            long skipped = skipFully(stream, bytesRequested);
            checkActive();
            seekSkippedFrames = skipped / frameSize;
            return skipped < bytesRequested && recoverSeekAtEof();
        }

        boolean recoverSeekAtEof() {
            long learned = loopingSeekRecovery.discoverFrameLength(isLooping.get(), seekKnownFrameLength,
                    seekRequestedFrames, seekSkippedFrames, true);
            if (learned <= 0) return false;
            al(() -> {
                discoveredFrameLength = learned;
                if (intent != null) intent.frameLengthHint = learned;
            });
            return true;
        }

        void trackInput(java.io.Closeable input) throws IOException {
            synchronized (inputs) {
                if (!stopFlag.get()) { inputs.add(input); return; }
            }
            input.close();
            throw new java.io.InterruptedIOException("Playback cancelled");
        }

        void closeInputs() {
            java.util.List<java.io.Closeable> closing;
            synchronized (inputs) { closing = new java.util.ArrayList<>(inputs); inputs.clear(); }
            for (var input : closing) {
                try { input.close(); } catch (IOException ignored) {}
            }
        }

        void stopAndCleanup() {
            if (!cleanupScheduled.compareAndSet(false, true)) return;
            stopFlag.set(true);
            var client = Minecraft.getInstance();
            if (streamingThread != null && streamingThread.isAlive()) {
                streamingThread.interrupt();
                // Closing a blocked input and joining must never block the client thread.
                Thread cleanupThread = new Thread(() -> {
                    closeInputs();
                    boolean interrupted = false;
                    while (streamingThread.isAlive()) {
                        try { streamingThread.join(); }
                        catch (InterruptedException e) { interrupted = true; }
                    }
                    // No timeout: numeric OpenAL IDs cannot be reused while the worker can access them.
                    client.execute(this::cleanupOpenALResources);
                    if (interrupted) Thread.currentThread().interrupt();
                }, SimplySpeakers.MOD_ID + "-cleanup-" + networkKey);
                cleanupThread.setDaemon(true);
                cleanupThread.start();
            } else {
                closeInputs();
                client.execute(this::cleanupOpenALResources);
            }
        }

        private void cleanupOpenALResources() {
            try {
                audioContext.run(contextEpoch, () -> {
                    if (AL10.alIsSource(sourceID)) {
                        AL10.alSourceStop(sourceID);
                        AL10.alSourcei(sourceID, AL10.AL_BUFFER, 0);
                        AL10.alDeleteSources(sourceID);
                        AL10.alDeleteBuffers(bufferIDs);
                    }
                });
            } catch (AudioContextGate.StaleContextException ignored) {
                // Vanilla destroyed this epoch. Its numeric IDs may now belong to new sources.
            } catch (Exception e) {
                SimplySpeakers.LOGGER.error("Error during OpenAL cleanup for source {} (network {})", sourceID, networkKey, e);
            }
        }
    }

    record VerificationSnapshot(int sources, int emitters, long decodedBytes, float offset, boolean playing) {}
    /** Read-only diagnostic used by the opt-in playback verification fixture. */
    static VerificationSnapshot verificationSnapshot(String key) {
        StreamingAudioResource resource = networkResources.get(key);
        if (resource == null) return new VerificationSnapshot(0, membership.getPositions(key).size(), 0, 0, false);
        boolean playing = false;
        try { playing = !resource.stopFlag.get() && resource.sourceInt(AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING; }
        catch (AudioContextGate.StaleContextException ignored) {}
        return new VerificationSnapshot(1, membership.getPositions(key).size(), resource.decodedBytes,
                resource.startOffsetSeconds, playing);
    }

    public static String resolveNetworkKey(BlockPos pos) {
        String activeKey = membership.getNetworkKey(pos);
        if (activeKey != null) return activeKey;
        if (ClientPortableSpeakers.isPortableToken(pos)) return "pos_" + pos.asLong();
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
        play(pos, speakerId, metadata, startPositionSeconds, isLooping, maxRange, maxVolume, audioDropoff, null);
    }

    public static void play(BlockPos pos, String speakerId, AudioFileMetadata metadata, float startPositionSeconds, boolean isLooping, int maxRange, float maxVolume, float audioDropoff,
            com.nstut.simplyspeakers.audio.DirectionalAudio.Extras directional) {
        play(pos, speakerId, metadata, startPositionSeconds, isLooping, maxRange, maxVolume, audioDropoff, directional, null, 0);
    }

    /**
     * Full play entry point. {@code remoteFullStateKey} and {@code remotePlaybackGeneration}
     * are the server-assigned playback identity from {@code PlayAudioPacketS2C}; they are
     * carried by the active URL stream and echoed back in the remote stream EOF report.
     */
    public static void play(BlockPos pos, String speakerId, AudioFileMetadata metadata, float startPositionSeconds, boolean isLooping, int maxRange, float maxVolume, float audioDropoff,
            com.nstut.simplyspeakers.audio.DirectionalAudio.Extras directional, String remoteFullStateKey, int remotePlaybackGeneration) {
        String networkKey = (speakerId != null && !speakerId.trim().isEmpty())
                ? "net_" + speakerId.trim()
                : "pos_" + pos.asLong();

        SimplySpeakers.LOGGER.debug("CLIENT: play called for pos: {}, speakerId: '{}', networkKey: {}, audioId: {}, start: {}s, looping: {}, range: {}, volume: {}, dropoff: {}",
                pos, speakerId, networkKey, metadata.getUuid(), startPositionSeconds, isLooping, maxRange, maxVolume, audioDropoff);

        String oldKey = membership.getNetworkKey(pos);
        membership.track(pos, networkKey, new com.nstut.simplyspeakers.SpeakerSettings(maxVolume, Math.min(maxRange, Config.speakerRange), audioDropoff));
        if (directional != null) {
            directionalExtras.put(pos, directional);
        } else {
            directionalExtras.remove(pos);
        }

        if (oldKey != null && !oldKey.equals(networkKey)) {
            if (membership.getPositions(oldKey).isEmpty()) {
                playbackIntents.remove(oldKey);
                StreamingAudioResource oldRes = networkResources.remove(oldKey);
                if (oldRes != null) {
                    oldRes.stopAndCleanup();
                }
            }
        }

        PlaybackIntent intent = playbackIntents.get(networkKey);
        if (intent == null || !intent.matches(metadata, remoteFullStateKey, remotePlaybackGeneration)) {
            intent = new PlaybackIntent(metadata, startPositionSeconds, isLooping, remoteFullStateKey, remotePlaybackGeneration);
            playbackIntents.put(networkKey, intent);
            StreamingAudioResource previous = networkResources.remove(networkKey);
            if (previous != null) previous.stopAndCleanup();
        }
        startIntent(networkKey, pos, intent);
    }

    private static void startIntent(String networkKey, BlockPos pos, PlaybackIntent intent) {
        if (playbackIntents.get(networkKey) != intent || !networkKey.equals(membership.getNetworkKey(pos))) return;
        StreamingAudioResource existing = networkResources.get(networkKey);
        if (existing != null) {
            if (existing.intent == intent && audioContext.isCurrent(existing.contextEpoch)
                    && !existing.stopFlag.get() && existing.streamingThread != null && existing.streamingThread.isAlive()) {
                updateSpeakerVolumes();
                return;
            }
            if (networkResources.remove(networkKey, existing)) existing.stopAndCleanup();
        }
        AudioFileMetadata metadata = intent.metadata;
        if (com.nstut.simplyspeakers.audio.StreamTracks.isHttpAudioUrl(metadata.getUuid())) {
            if (!Config.isRemoteStreamingAllowed()
                    || !com.nstut.simplyspeakers.audio.StreamTracks.isRemoteStreamUrlAllowed(metadata.getUuid(), false)) {
                playbackIntents.remove(networkKey, intent);
                return;
            }
            playFromUrl(networkKey, pos, metadata.getUuid(), intent);
            return;
        }
        if (!CACHE_DIR.exists()) CACHE_DIR.mkdirs();
        String extension = com.google.common.io.Files.getFileExtension(metadata.getOriginalFilename());
        File cachedFile = new File(CACHE_DIR, metadata.getUuid() + (extension.isEmpty() ? "" : "." + extension));
        if (cachedFile.exists()) {
            ClientCacheManager.recordAccess(cachedFile);
            playFromFile(networkKey, pos, cachedFile.getAbsolutePath(), intent);
        } else {
            List<PlayRequest> requests = pendingPlays.computeIfAbsent(metadata.getUuid(), k -> Collections.synchronizedList(new ArrayList<>()));
            synchronized (requests) {
                if (requests.stream().noneMatch(request -> request.intent == intent && request.pos.equals(pos)))
                    requests.add(new PlayRequest(pos, networkKey, intent));
            }
            requestFileFromServer(metadata.getUuid(), metadata.getOriginalFilename());
        }
    }

    public static void setLooping(String networkKey, boolean looping) {
        PlaybackIntent intent = playbackIntents.get(networkKey);
        if (intent != null) intent.looping = looping;
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

    private static void playFromFile(String networkKey, BlockPos pos, String filePath, PlaybackIntent intent) {
        queueSource(networkKey, pos, intent, false, filePath);
    }

    private static void queueSource(String networkKey, BlockPos pos, PlaybackIntent intent, boolean remote, String input) {
        long epoch = audioContext.epoch();
        Minecraft.getInstance().execute(() -> {
            try {
                audioContext.run(epoch, () -> {
                    if (playbackIntents.get(networkKey) != intent) return;
                    // The restore/download scan and this allocation are separate client tasks.
                    // Its chosen emitter may have detached while the same network is still active.
                    BlockPos activePos = networkKey.equals(membership.getNetworkKey(pos)) ? pos
                            : membership.getPositions(networkKey).stream()
                                    .filter(candidate -> networkKey.equals(membership.getNetworkKey(candidate)))
                                    .findFirst().orElse(null);
                    if (activePos == null) return;
                    StreamingAudioResource existing = networkResources.get(networkKey);
                    if (existing != null && existing.intent == intent && !existing.stopFlag.get()
                            && existing.streamingThread != null && existing.streamingThread.isAlive()) return;
                    int sourceID = AL10.alGenSources();
                    int[] bufferIDs = new int[NUM_BUFFERS];
                    AL10.alGenBuffers(bufferIDs);
                    AL10.alSource3f(sourceID, AL10.AL_POSITION, activePos.getX() + 0.5f, activePos.getY() + 0.5f, activePos.getZ() + 0.5f);
                    AL10.alSourcef(sourceID, AL10.AL_ROLLOFF_FACTOR, 0.0f);
                    AL10.alSourcef(sourceID, AL10.AL_GAIN, 0.0f);
                    AL10.alSourcei(sourceID, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
                    StreamingAudioResource resource = new StreamingAudioResource(networkKey, sourceID, bufferIDs, null,
                            intent.looping, remote ? intent.fullStateKey : null, intent.generation);
                    resource.startOffsetSeconds = intent.offsetSeconds();
                    Thread worker = new Thread(() -> {
                        if (remote) streamUrlAudioData(resource, input, resource.startOffsetSeconds);
                        else streamAudioData(resource, input, resource.startOffsetSeconds);
                    }, SimplySpeakers.MOD_ID + (remote ? "-url-stream-" : "-stream-") + networkKey);
                    worker.setDaemon(true);
                    resource.streamingThread = worker;
                    networkResources.put(networkKey, resource);
                    worker.start();
                });
                updateSpeakerVolumes();
            } catch (AudioContextGate.StaleContextException ignored) {
                // A queued start from an old epoch cannot resurrect or touch new IDs.
            } catch (Exception e) {
                SimplySpeakers.LOGGER.error("CLIENT: Failed to start audio playback for network {}", networkKey, e);
            }
        });
    }

    private static long skipFully(InputStream in, long n) throws IOException {
        long remaining = n;
        byte[] discard = new byte[8192];
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                int read = in.read(discard, 0, (int) Math.min(discard.length, remaining));
                if (read < 0) break;
                if (read == 0) throw new IOException("Audio decoder made no progress while seeking");
                skipped = read;
            }
            remaining -= skipped;
        }
        return n - remaining;
    }

    private static void streamAudioData(StreamingAudioResource resource, String filePath, float startPositionSeconds) {
        boolean isLooping = resource.isLooping.get();
        String networkKey = resource.networkKey;
        int sourceID = resource.sourceID;
        int[] bufferIDs = resource.bufferIDs;
        boolean continueStreaming = true;

        while (continueStreaming) {
            if (networkResources.get(networkKey) != resource || resource.stopFlag.get() || Thread.currentThread().isInterrupted()) {
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

                resource.trackInput(pcmAudioStream);
                resource.checkActive();
                AudioFormat format = pcmAudioStream.getFormat();
                // Decoder/open delay belongs to the authoritative timeline too.
                if (resource.intent != null) startPositionSeconds = resource.intent.offsetSeconds();
                resource.startOffsetSeconds = startPositionSeconds;
                if (resource.seek(pcmAudioStream, startPositionSeconds)) continue;
                startPositionSeconds = 0;

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
                    resource.checkActive();
                    if (bytesRead <= 0) {
                        endOfStream = true;
                        break;
                    }

                    ByteBuffer alBuffer = ByteBuffer.allocateDirect(bytesRead).order(ByteOrder.nativeOrder());
                    alBuffer.put(bufferData, 0, bytesRead).flip();

                    resource.checkActive();
                    resource.bufferData(bufferIDs[i], alFormat, alBuffer, (int) format.getSampleRate());
                    resource.checkActive();
                    resource.queueBuffer(bufferIDs[i]);
                    resource.decodedBytes += bytesRead;
                    initialDataLoaded = true;

                    if (!playbackAttempted) {
                        resource.checkActive();
                        resource.playSource();
                        playbackAttempted = true;
                    }
                }
                if (!continueStreaming) break;

                if (!playbackAttempted && initialDataLoaded) {
                    if (!resource.stopFlag.get() && !Thread.currentThread().isInterrupted()) {
                        resource.checkActive();
                        int queued = resource.sourceInt(AL10.AL_BUFFERS_QUEUED);
                        resource.checkActive();
                        if (queued > 0 && resource.sourceInt(AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                            resource.checkActive();
                            resource.playSource();
                            playbackAttempted = true;
                        }
                    }
                }

                if (!playbackAttempted) {
                    if (resource.recoverSeekAtEof()) continue;
                    // Empty input (or an unsuccessful bounded retry) cannot spin forever.
                    resource.stopFlag.set(true);
                    continueStreaming = false;
                    break;
                }

                while (playbackAttempted && !resource.stopFlag.get() && !Thread.currentThread().isInterrupted()) {
                    resource.checkActive();
                    int buffersProcessed = resource.sourceInt(AL10.AL_BUFFERS_PROCESSED);

                    for (int i = 0; i < buffersProcessed; i++) {
                        resource.checkActive();
                        int bufferID = resource.unqueueBuffer();
                        if (!endOfStream) {
                            int bytesRead = pcmAudioStream.read(bufferData, 0, bufferData.length);
                            resource.checkActive();
                            if (bytesRead > 0) {
                                ByteBuffer alBuffer = ByteBuffer.allocateDirect(bytesRead).order(ByteOrder.nativeOrder());
                                alBuffer.put(bufferData, 0, bytesRead).flip();
                                resource.checkActive();
                                resource.bufferData(bufferID, alFormat, alBuffer, (int) format.getSampleRate());
                                resource.checkActive();
                                resource.queueBuffer(bufferID);
                            } else {
                                endOfStream = true;
                            }
                        }
                    }
                    if (resource.stopFlag.get() || Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    resource.checkActive();
                    int queuedBuffers = resource.sourceInt(AL10.AL_BUFFERS_QUEUED);
                    if (endOfStream) {
                        SimplySpeakers.LOGGER.debug("Draining queued audio before restart for source {}", sourceID);
                    }
                    if (endOfStream && queuedBuffers == 0) {
                        playbackCompletedSuccessfully = true;
                        break;
                    }

                    resource.checkActive();
                    if (resource.sourceInt(AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING && initialDataLoaded) {
                        if (queuedBuffers > 0) {
                            resource.checkActive();
                            resource.playSource();
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
                        resource.checkActive();
                        if (resource.isSource()) {
                            resource.checkActive();
                            resource.stopSource();
                            resource.checkActive();
                            int queued = resource.sourceInt(AL10.AL_BUFFERS_QUEUED);
                            resource.checkActive();
                            if (queued > 0) resource.unqueueBuffers(new int[queued]);
                        }
                        resource.restartLoopTimeline();
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
                if (!resource.stopFlag.get()) SimplySpeakers.LOGGER.error("Streaming thread error for network {} with file {}", networkKey, filePath, e);
                if (resource != null) resource.stopFlag.set(true);
                continueStreaming = false;
            } catch (AudioContextGate.StaleContextException ignored) {
                resource.stopFlag.set(true);
                continueStreaming = false;
            } catch (Exception e) {
                SimplySpeakers.LOGGER.error("Critical error in streaming thread for network {}", networkKey, e);
                if (resource != null) resource.stopFlag.set(true);
                continueStreaming = false;
            } finally {
                resource.closeInputs();
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
        finishResource(resource, false);
    }


    // ------------------------------------------------------------------
    // Internet streams (0.8.x): direct HTTP(S) audio only
    // ------------------------------------------------------------------

    private static void playFromUrl(String networkKey, BlockPos pos, String url, PlaybackIntent intent) {
        queueSource(networkKey, pos, intent, true, url);
    }

    /**
     * Opens the URL stream, revalidating every redirect target. Note: host
     * validation and connection are separate steps, so a hostile DNS server
     * could rebind the name between them (TOCTOU window); see
     * {@link com.nstut.simplyspeakers.audio.StreamTracks}.
     */
    private static AudioInputStream openUrlStream(StreamingAudioResource resource, String url) throws IOException {
        try {
            String currentUrl = url;
            int redirects = 0;
            while (true) {
                resource.checkActive();
                if (!com.nstut.simplyspeakers.audio.StreamTracks.isRemoteStreamUrlAllowed(currentUrl)) {
                    throw new IOException("Stream URL host is not allowed: " + currentUrl);
                }
                java.net.URLConnection connection = new java.net.URL(currentUrl).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(15000);
                if (connection instanceof java.net.HttpURLConnection http) {
                    resource.trackInput(http::disconnect);
                    http.setRequestProperty("Icy-MetaData", "1");
                    http.setRequestProperty("User-Agent", SimplySpeakers.MOD_ID + "/0.8.2");
                    http.setInstanceFollowRedirects(false);
                    int code = http.getResponseCode();
                    if (code >= 300 && code < 400) {
                        String location = http.getHeaderField("Location");
                        http.disconnect();
                        if (location == null || location.isBlank()) {
                            throw new IOException("HTTP " + code + " redirect without Location for stream " + currentUrl);
                        }
                        if (redirects >= MAX_REDIRECTS) {
                            throw new IOException("Too many redirects for stream " + url);
                        }
                        redirects++;
                        java.net.URI base;
                        try {
                            base = new java.net.URI(currentUrl).resolve(location.trim());
                        } catch (Exception e) {
                            throw new IOException("Invalid redirect location for stream " + currentUrl, e);
                        }
                        currentUrl = base.toString();
                        continue;
                    }
                    if (code / 100 != 2) {
                        throw new IOException("HTTP " + code + " for stream " + currentUrl);
                    }
                    InputStream input = connection.getInputStream();
                    resource.trackInput(input);
                    resource.checkActive();
                    return IncrementalAudioDecoders.openPcmStreamFromUrl(input, currentUrl);
                }
                InputStream input = connection.getInputStream();
                resource.trackInput(input);
                resource.checkActive();
                return IncrementalAudioDecoders.openPcmStreamFromUrl(input, currentUrl);
            }
        } catch (javax.sound.sampled.UnsupportedAudioFileException e) {
            throw new IOException("Unsupported internet stream format: " + url, e);
        }
    }

    private static void sendRemoteStreamEofReport(String fullStateKey, int playbackGeneration, String audioId) {
        if (fullStateKey == null || fullStateKey.isEmpty()) {
            // Without the server-provided identity the report cannot be validated.
            SimplySpeakers.LOGGER.debug("CLIENT: Skipping remote stream EOF report without playback identity");
            return;
        }
        try {
            NetworkManager.sendToServer(new RemoteStreamEofPacketC2S(fullStateKey, playbackGeneration, audioId));
        } catch (Exception e) {
            SimplySpeakers.LOGGER.debug("CLIENT: Failed to report remote stream EOF", e);
        }
    }

    private static void streamUrlAudioData(StreamingAudioResource resource, String url, float startPositionSeconds) {
        String networkKey = resource.networkKey;
        int sourceID = resource.sourceID;
        int[] bufferIDs = resource.bufferIDs;
        boolean reportEof = false;

        while (networkResources.get(networkKey) == resource && !resource.stopFlag.get() && !Thread.currentThread().isInterrupted()) {
            AudioInputStream pcm = null;
            boolean completed = false;
            try {
                pcm = openUrlStream(resource, url);
                resource.trackInput(pcm);
                resource.checkActive();
                AudioFormat format = pcm.getFormat();
                float startSeconds = resource.intent == null
                        ? com.nstut.simplyspeakers.audio.StreamTracks.sanitizeStartPosition(startPositionSeconds)
                        : resource.intent.offsetSeconds();
                resource.startOffsetSeconds = startSeconds;
                if (resource.seek(pcm, startSeconds)) continue;
                int alFormat = AL10.AL_FORMAT_MONO16;
                int bufferSizeBytes = Math.max(4096, (int) (format.getFrameRate() * format.getFrameSize() * BUFFER_SIZE_SECONDS));
                byte[] bufferData = new byte[bufferSizeBytes];

                boolean playbackAttempted = false;
                int prefill = 0;
                while (prefill < NUM_BUFFERS && !resource.stopFlag.get()) {
                    int read = pcm.read(bufferData, 0, bufferData.length);
                    resource.checkActive();
                    if (read <= 0) break;
                    ByteBuffer alBuffer = ByteBuffer.allocateDirect(read).order(ByteOrder.nativeOrder());
                    alBuffer.put(bufferData, 0, read).flip();
                    resource.checkActive();
                    resource.bufferData(bufferIDs[prefill], alFormat, alBuffer, (int) format.getSampleRate());
                    resource.checkActive();
                    resource.queueBuffer(bufferIDs[prefill]);
                    resource.decodedBytes += read;
                    prefill++;
                    if (!playbackAttempted && prefill >= 2) {
                        resource.checkActive();
                        resource.playSource();
                        playbackAttempted = true;
                    }
                }
                if (!playbackAttempted && prefill > 0) {
                    resource.checkActive();
                    resource.playSource();
                    playbackAttempted = true;
                }

                if (!playbackAttempted) {
                    if (resource.recoverSeekAtEof()) continue;
                    if (resource.isLooping.get()) resource.stopFlag.set(true); // Empty looping URL: terminate, never busy-reopen.
                    completed = true;
                }
                boolean endOfStream = false;
                while (playbackAttempted && !resource.stopFlag.get() && !Thread.currentThread().isInterrupted()) {
                    resource.checkActive();
                    int processed = resource.sourceInt(AL10.AL_BUFFERS_PROCESSED);
                    for (int i = 0; i < processed; i++) {
                        resource.checkActive();
                        int bufferID = resource.unqueueBuffer();
                        if (!endOfStream) {
                            int read = pcm.read(bufferData, 0, bufferData.length);
                            resource.checkActive();
                            if (read > 0) {
                                ByteBuffer alBuffer = ByteBuffer.allocateDirect(read).order(ByteOrder.nativeOrder());
                                alBuffer.put(bufferData, 0, read).flip();
                                resource.checkActive();
                                resource.bufferData(bufferID, alFormat, alBuffer, (int) format.getSampleRate());
                                resource.checkActive();
                                resource.queueBuffer(bufferID);
                            } else {
                                endOfStream = true;
                            }
                        }
                    }
                    resource.checkActive();
                    if (endOfStream && resource.sourceInt(AL10.AL_BUFFERS_QUEUED) == 0) {
                        completed = true;
                        break;
                    }
                    resource.checkActive();
                    if (resource.sourceInt(AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING
                            && resource.sourceInt(AL10.AL_BUFFERS_QUEUED) > 0) {
                        resource.checkActive();
                        resource.playSource();
                    }
                    Thread.sleep(50);
                }
                if (!playbackAttempted) completed = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (resource != null) resource.stopFlag.set(true);
            } catch (AudioContextGate.StaleContextException ignored) {
                resource.stopFlag.set(true);
            } catch (Exception e) {
                if (!resource.stopFlag.get()) SimplySpeakers.LOGGER.error("CLIENT: Internet stream error for {}: {}", networkKey, url, e);
                if (resource != null) resource.stopFlag.set(true);
            } finally {
                resource.closeInputs();
            }

            if (completed && !resource.stopFlag.get() && !resource.isLooping.get()) {
                reportEof = true;
            }
            if (completed && resource.isLooping.get() && !resource.stopFlag.get()) {
                try { resource.restartLoopTimeline(); }
                catch (AudioContextGate.StaleContextException ignored) { break; }
                startPositionSeconds = 0.0f;
                continue;
            }
            break;
        }

        finishResource(resource, reportEof);
    }

    private static void finishResource(StreamingAudioResource resource, boolean reportEof) {
        if (resource == null) return;
        boolean removed = networkResources.remove(resource.networkKey, resource);
        if (removed && reportEof && resource.intent != null) {
            Minecraft.getInstance().execute(() -> {
                if (audioContext.isCurrent(resource.contextEpoch)
                        && !membership.getPositions(resource.networkKey).isEmpty()
                        && playbackIntents.remove(resource.networkKey, resource.intent)) {
                    sendRemoteStreamEofReport(resource.eofFullStateKey, resource.playbackGeneration, resource.intent.metadata.getUuid());
                }
            });
        } else if (removed && resource.intent != null) {
            synchronized (audioContext) {
                if (audioContext.isCurrent(resource.contextEpoch)) playbackIntents.remove(resource.networkKey, resource.intent);
            }
        }
        resource.stopAndCleanup();
    }

    public static void stop(BlockPos pos) {
        ClientPortableSpeakers.remove(pos);
        directionalExtras.remove(pos);
        for (List<PlayRequest> requests : pendingPlays.values()) {
            requests.removeIf(req -> req.pos.equals(pos));
        }

        PlaybackMembership.DetachResult result = membership.detach(pos);
        if (result.wasTracked()) {
            if (result.networkEmpty()) {
                playbackIntents.remove(result.networkKey());
                StreamingAudioResource resource = networkResources.remove(result.networkKey());
                if (resource != null) {
                    resource.stopAndCleanup();
                }
            } else {
                updateSpeakerVolumes();
            }
        }
    }

    public static void stopNetwork(String networkKey) {
        playbackIntents.remove(networkKey);
        for (List<PlayRequest> requests : pendingPlays.values()) {
            requests.removeIf(req -> networkKey.equals(req.networkKey));
        }
        for (BlockPos pos : membership.detachNetwork(networkKey)) {
            ClientPortableSpeakers.remove(pos);
            directionalExtras.remove(pos);
        }
        StreamingAudioResource resource = networkResources.remove(networkKey);
        if (resource != null) resource.stopAndCleanup();
    }

    public static void stopAll() {
        playbackIntents.clear();
        ClientPortableSpeakers.clear();
        directionalExtras.clear();
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
        membership.clear();
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

    public static void updateSpeakerVolumes() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        if (networkResources.isEmpty()) {
            return;
        }

        Vec3 playerPos = player.position();
        float masterVolume = mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER);
        float recordVolume = mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.RECORDS);

        for (Map.Entry<String, StreamingAudioResource> entry : new ArrayList<>(networkResources.entrySet())) {
            String networkKey = entry.getKey();
            StreamingAudioResource resource = entry.getValue();

            if (resource == null || resource.stopFlag.get()) {
                continue;
            }

            Set<BlockPos> positions = membership.getPositions(networkKey);
            if (positions.isEmpty()) {
                if (resource.intent != null) playbackIntents.remove(networkKey, resource.intent);
                resource.stopAndCleanup();
                networkResources.remove(networkKey);
                continue;
            }

            List<SpatialAudioCalculator.SpeakerEmitter> emitters = new ArrayList<>();

            for (BlockPos speakerPos : positions) {
                // Packet settings include transient controller gain; block tags can lag.
                com.nstut.simplyspeakers.SpeakerSettings cached = membership.getSettings(speakerPos);
                if (cached != null) {
                    Vec3 renderPosition = resolveEmitterPosition(mc, speakerPos);
                    if (renderPosition == null) continue;
                    com.nstut.simplyspeakers.audio.DirectionalAudio.Extras cone = directionalExtras.get(speakerPos);
                    float effectiveVolume = cached.maxVolume();
                    if (cone != null && cone.directionality() > 0.0f) {
                        double[] facing = resolveEmitterFacing(mc, speakerPos, cone.facingOrdinal());
                        if (facing == null) continue;
                        double[] toListener = com.nstut.simplyspeakers.audio.DirectionalAudio.normalize(
                                playerPos.x - renderPosition.x, playerPos.z - renderPosition.z);
                        float adjusted = SpatialAudioCalculator.calculateDistanceGain(
                                0, 64, cached.maxVolume(), 0.0f,
                                facing[0], facing[1], toListener[0], toListener[1],
                                new SpatialAudioCalculator.ConeSettings(
                                        cone.directionality(), cone.coneAngleDegrees(), cone.rearAttenuation()));
                        effectiveVolume = cached.maxVolume() > 1.0E-6f ? adjusted : 0.0f;
                    }
                    emitters.add(new SpatialAudioCalculator.SpeakerEmitter(
                            renderPosition.x,
                            renderPosition.y,
                            renderPosition.z,
                            cached.maxRange(),
                            effectiveVolume,
                            cached.audioDropoff()
                    ));
                }
            }

            if (emitters.isEmpty()) {
                if (positions.isEmpty()) {
                    resource.stopAndCleanup();
                    networkResources.remove(networkKey);
                    continue;
                }
                mc.execute(() -> {
                    try { if (resource.isSource()) resource.gain(0.0f); }
                    catch (AudioContextGate.StaleContextException ignored) {}
                });
                continue;
            }

            SpatialAudioCalculator.VirtualEmitterResult result =
                    SpatialAudioCalculator.calculateVirtualEmitter(playerPos.x, playerPos.y, playerPos.z, emitters);

            final float finalGain = AudioGain.applyGameVolume(result.maxGain(), masterVolume, recordVolume);
            final float posX = (float) result.x();
            final float posY = (float) result.y();
            final float posZ = (float) result.z();

            mc.execute(() -> {
                StreamingAudioResource currentResource = networkResources.get(networkKey);
                if (currentResource == resource && !resource.stopFlag.get()) {
                    try {
                        if (resource.isSource()) {
                            resource.position(posX, posY, posZ);
                            resource.gain(finalGain);
                        }
                    } catch (AudioContextGate.StaleContextException ignored) {
                    } catch (Exception e) {
                        SimplySpeakers.LOGGER.error("Error setting spatial audio for source {}", resource.sourceID, e);
                    }
                }
            });
        }
    }

    private static Vec3 resolveEmitterPosition(Minecraft mc, BlockPos pos) {
        return ClientPortableSpeakers.isPortableToken(pos)
                ? ClientPortableSpeakers.resolvePosition(pos) : Vec3.atCenterOf(pos);
    }

    private static double[] resolveEmitterFacing(Minecraft mc, BlockPos pos, int ordinal) {
        return ClientPortableSpeakers.isPortableToken(pos)
                ? ClientPortableSpeakers.resolveFacing(pos) : com.nstut.simplyspeakers.audio.DirectionalAudio.facingFromOrdinal(ordinal);
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
                        String currentNetworkKey = membership.getNetworkKey(req.pos);
                        if (req.networkKey != null && req.networkKey.equals(currentNetworkKey)
                                && playbackIntents.get(req.networkKey) == req.intent) {
                            requestsByNetwork.putIfAbsent(req.networkKey, req);
                        }
                    }

                    for (Map.Entry<String, PlayRequest> entry : requestsByNetwork.entrySet()) {
                        String netKey = entry.getKey();
                        PlayRequest req = entry.getValue();
                        req.intent.looping = ClientSpeakerRegistry.getLooping(netKey, req.intent.looping);
                        playFromFile(netKey, req.pos, cachedFile.getAbsolutePath(), req.intent);
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
        final PlaybackIntent intent;

        PlayRequest(BlockPos pos, String networkKey, PlaybackIntent intent) {
            this.pos = pos;
            this.networkKey = networkKey;
            this.intent = intent;
        }
    }
}
