package com.nstut.simplyspeakers.client;

import net.minecraft.core.BlockPos;
import org.lwjgl.openal.AL10;
import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.client.screens.SpeakerScreen;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.audio.PlaybackOffset;
import com.nstut.simplyspeakers.audio.UploadProgressLogger;
import com.nstut.simplyspeakers.audio.SpatialAudioCalculator;
import com.nstut.simplyspeakers.audio.AudioGain;
import com.nstut.simplyspeakers.network.RequestAudioFilePacketC2S;
import com.nstut.simplyspeakers.network.RequestAudioListPacketC2S;
import com.nstut.simplyspeakers.network.UploadAudioDataPacketC2S;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.ArrayList;

import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.UUID;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.DecoderException;

public class ClientAudioPlayer {

    private static final File CACHE_DIR = new File(Minecraft.getInstance().gameDirectory, "simply_speakers_cache");
    private static final Map<String, StreamingAudioResource> networkResources = new ConcurrentHashMap<>();
    private static final Map<BlockPos, String> posToNetworkKey = new ConcurrentHashMap<>();
    private static final Map<String, Set<BlockPos>> networkToPositions = new ConcurrentHashMap<>();
    private static final Map<UUID, UploadProcess> activeUploads = new ConcurrentHashMap<>();
    private static final Map<String, DownloadProcess> activeDownloads = new ConcurrentHashMap<>();
    private static final Map<String, PlayRequest> pendingPlays = new ConcurrentHashMap<>();
    private static final Map<String, AudioFileMetadata> audioList = new ConcurrentHashMap<>();
    private static final int NUM_BUFFERS = 3;
    private static final int BUFFER_SIZE_SECONDS = 1;

    private static class StreamingAudioResource {
        final String networkKey;
        final int sourceID;
        final int[] bufferIDs; // Array of buffer IDs
        final Thread streamingThread; // The thread handling buffer refills
        final AtomicBoolean stopFlag = new AtomicBoolean(false); // Flag to signal thread termination

        StreamingAudioResource(String networkKey, int sourceID, int[] bufferIDs, Thread streamingThread) {
            this.networkKey = networkKey;
            this.sourceID = sourceID;
            this.bufferIDs = bufferIDs;
            this.streamingThread = streamingThread;
        }

        // Method to signal the streaming thread to stop and clean up resources
        void stopAndCleanup() {
            stopFlag.set(true); // Signal the thread to stop
            if (streamingThread != null && streamingThread.isAlive()) {
                streamingThread.interrupt(); // Interrupt if sleeping/waiting
                // PERFORMANCE FIX: Use a separate cleanup thread to avoid blocking main thread during world save
                Thread cleanupThread = new Thread(() -> {
                    try {
                        // Wait for streaming thread to finish, but with timeout to prevent hanging
                        streamingThread.join(500); // Reduced timeout to prevent save delays
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    // Schedule OpenAL cleanup on main thread with minimal operations
                    Minecraft.getInstance().tell(() -> cleanupOpenALResources());
                }, SimplySpeakers.MOD_ID + "-cleanup-" + networkKey);
                cleanupThread.setDaemon(true);
                cleanupThread.start();
            } else {
                // If no streaming thread, cleanup immediately but asynchronously
                Minecraft.getInstance().tell(() -> cleanupOpenALResources());
            }
        }
        
        // Separate method for OpenAL cleanup with error handling and timeout protection
        private void cleanupOpenALResources() {
            try {
                if (AL10.alIsSource(sourceID)) {
                    // Quick cleanup without extensive buffer operations that could hang
                    AL10.alSourceStop(sourceID);
                    AL10.alSourcei(sourceID, AL10.AL_BUFFER, 0); // Detach buffer pointer
                    AL10.alDeleteSources(sourceID);
                    AL10.alDeleteBuffers(bufferIDs);
                    SimplySpeakers.LOGGER.debug("Fast cleanup completed for source {} (network {})", sourceID, networkKey);
                } else {
                    SimplySpeakers.LOGGER.warn("Source {} (network {}) already invalid, skipping cleanup.", sourceID, networkKey);
                }
            } catch (Exception e) {
                SimplySpeakers.LOGGER.error("Error during OpenAL cleanup for source {} (network {})", sourceID, networkKey, e);
                // Continue cleanup despite errors to prevent resource leaks
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
                    return "net_" + id;
                }
            } else if (blockEntity instanceof com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity proxy) {
                String id = proxy.getSpeakerId();
                if (id != null && !id.trim().isEmpty()) {
                    return "net_" + id;
                }
            }
        }
        return "pos_" + pos.asLong();
    }

    public static void play(BlockPos pos, AudioFileMetadata metadata, float startPositionSeconds, boolean isLooping) {
        String networkKey = resolveNetworkKey(pos);
        SimplySpeakers.LOGGER.debug("CLIENT: play called for pos: {}, networkKey: {}, audioId: {}, start: {}s, looping: {}",
                pos, networkKey, metadata.getUuid(), startPositionSeconds, isLooping);

        // Disassociate pos from any previously registered network key if changed
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
            SimplySpeakers.LOGGER.debug("CLIENT: Cached file found for {}. Playing from file.", metadata.getUuid());
            playFromFile(networkKey, pos, cachedFile.getAbsolutePath(), startPositionSeconds, isLooping);
        } else {
            SimplySpeakers.LOGGER.info("CLIENT: Cached file not found for {}. Requesting from server.", metadata.getUuid());
            pendingPlays.put(metadata.getUuid(), new PlayRequest(pos, startPositionSeconds, isLooping));
            requestFileFromServer(metadata.getUuid(), metadata.getOriginalFilename());
        }
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
                SimplySpeakers.LOGGER.debug("CLIENT: Generated OpenAL source {} and {} buffers for network {}", sourceID, NUM_BUFFERS, networkKey);

                AL10.alSource3f(sourceID, AL10.AL_POSITION, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f);
                AL10.alSourcef(sourceID, AL10.AL_ROLLOFF_FACTOR, 0.0f);
                // Avoid a full-volume frame before the first settings/category-volume update.
                AL10.alSourcef(sourceID, AL10.AL_GAIN, 0.0f);
                AL10.alSourcei(sourceID, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);

                Thread streamingThread = new Thread(() -> streamAudioData(networkKey, sourceID, bufferIDs, filePath, startPositionSeconds, isLooping),
                        SimplySpeakers.MOD_ID + "-stream-" + networkKey);
                streamingThread.setDaemon(true);

                StreamingAudioResource resource = new StreamingAudioResource(networkKey, sourceID, bufferIDs, streamingThread);
                networkResources.put(networkKey, resource);
                streamingThread.start();
                SimplySpeakers.LOGGER.debug("CLIENT: Started streaming thread for source {} on network {}", sourceID, networkKey);

                updateSpeakerVolumes();
            } catch (Exception e) {
                SimplySpeakers.LOGGER.error("CLIENT: Failed to start audio playback for network {}", networkKey, e);
            }
        });
    }

    // Helper method to ensure all requested bytes are skipped (remains unchanged)
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

    // Core streaming logic executed in a separate thread
    private static void streamAudioData(String networkKey, int sourceID, int[] bufferIDs, String filePath, float startPositionSeconds, boolean isLooping) {
        SimplySpeakers.LOGGER.debug("STREAMER [{}]: Thread started. Network: {}, File: {}, Start: {}s, Looping: {}",
                sourceID, networkKey, filePath, startPositionSeconds, isLooping);
        StreamingAudioResource resource = networkResources.get(networkKey);

        // Loop control
        boolean continueStreaming = true;

        while (continueStreaming) { // Outer loop for restarting playback if isLooping is true
            if (resource == null || resource.sourceID != sourceID) {
                SimplySpeakers.LOGGER.error("STREAMER [{}]: Aborting. Resource mismatch or missing for network {}. Current resource sourceID: {}",
                        sourceID, networkKey, resource == null ? "null" : resource.sourceID);
                continueStreaming = false; // Exit outer loop
                break; // Exit while(continueStreaming)
            }

            AudioInputStream pcmAudioStream = null;
            boolean initialDataLoaded = false; // Declare here for visibility in finally
            boolean playbackCompletedSuccessfully = false; // Flag to indicate if the current playback cycle finished without early stop

            try {
                File audioFile = new File(filePath);
                if (!audioFile.exists()) {
                    SimplySpeakers.LOGGER.error("Streaming thread ERROR: Audio file not found: {} for network {}", filePath, networkKey);
                    resource.stopFlag.set(true); // Signal stop for this attempt
                    continueStreaming = false; // Do not loop if file not found
                    break; // Exit while(continueStreaming)
                }

                SimplySpeakers.LOGGER.debug("Streaming thread for network {}: Attempting to get PCM stream for {} {}",
                        networkKey, filePath, (isLooping ? "(Looping)" : ""));
                pcmAudioStream = getPcmAudioStream(audioFile);
                
                if (pcmAudioStream == null) {
                    SimplySpeakers.LOGGER.error("Streaming thread ERROR: Could not get PCM audio stream for: {} for network {}", filePath, networkKey);
                    resource.stopFlag.set(true);
                    continueStreaming = false;
                    break;
                }
                SimplySpeakers.LOGGER.debug("Streaming thread for network {}: Successfully got PCM stream.", networkKey);

                AudioFormat format = pcmAudioStream.getFormat();
                // Only apply startPositionSeconds on the very first playback, not on loops
                if (startPositionSeconds > 0 && continueStreaming) { // Check continueStreaming to ensure this is the first attempt if looping
                    long bytesToSkip = 0;
                    float frameRate = format.getFrameRate();
                    int frameSize = format.getFrameSize();

                    if (frameRate > 0 && frameSize > 0) {
                        long framesToSkip = PlaybackOffset.frameOffset(
                                startPositionSeconds,
                                isLooping,
                                pcmAudioStream.getFrameLength(),
                                frameRate);
                        bytesToSkip = framesToSkip * frameSize;
                        SimplySpeakers.LOGGER.debug("Streaming thread for network {}: Calculated skip: {} bytes for {}s",
                                networkKey, bytesToSkip, startPositionSeconds);
                    } else {
                        SimplySpeakers.LOGGER.warn("Streaming thread WARNING for network {}: Invalid format for seeking: {}",
                                networkKey, format);
                    }

                    if (bytesToSkip > 0) {
                        SimplySpeakers.LOGGER.debug("Streaming thread for network {}: Attempting to skip {} bytes.",
                                networkKey, bytesToSkip);
                        long skipped = skipFully(pcmAudioStream, bytesToSkip);
                        if (skipped < bytesToSkip) {
                            SimplySpeakers.LOGGER.warn("Streaming thread WARNING for network {}: Could only skip {}/{} bytes. Reached EOF or error.",
                                    networkKey, skipped, bytesToSkip);
                            resource.stopFlag.set(true);
                            continueStreaming = false;
                            break;
                        }
                        SimplySpeakers.LOGGER.debug("Streaming thread for network {}: Successfully skipped {} bytes.", networkKey, skipped);
                    }
                    startPositionSeconds = 0; // Reset for subsequent loops
                }


                boolean playbackAttempted = false;
                initialDataLoaded = false;
                boolean endOfStream = false;

                int alFormat = getOpenALFormat(format);
                if (alFormat == -1) {
                    SimplySpeakers.LOGGER.error("Streaming thread ERROR for network {}: Unsupported audio format for OpenAL: {}",
                            networkKey, format);
                    resource.stopFlag.set(true);
                    continueStreaming = false;
                    break;
                }
                int bufferSizeBytes = (int) (format.getFrameRate() * format.getFrameSize() * BUFFER_SIZE_SECONDS);
                byte[] bufferData = new byte[bufferSizeBytes];

                // Initial buffer filling
                for (int i = 0; i < NUM_BUFFERS; i++) {
                    if (resource.stopFlag.get() || Thread.currentThread().isInterrupted()) {
                        SimplySpeakers.LOGGER.debug("Streaming thread for network {}: Stop signal or interrupt during initial buffering.", networkKey);
                        continueStreaming = false; // Stop looping if interrupted
                        break; // Break from initial buffer filling
                    }

                    int bytesRead = pcmAudioStream.read(bufferData, 0, bufferData.length);
                    if (bytesRead <= 0) {
                        SimplySpeakers.LOGGER.debug("Streaming thread for network {}: EOF or read error during initial buffering. Bytes read: {}",
                                networkKey, bytesRead);
                        endOfStream = true;
                        break; // Break from initial buffer filling, will check isLooping later
                    }

                    ByteBuffer alBuffer = ByteBuffer.allocateDirect(bytesRead).order(ByteOrder.nativeOrder());
                    alBuffer.put(bufferData, 0, bytesRead).flip();

                    AL10.alBufferData(bufferIDs[i], alFormat, alBuffer, (int) format.getSampleRate());
                    AL10.alSourceQueueBuffers(sourceID, bufferIDs[i]);
                    initialDataLoaded = true;

                    if (!playbackAttempted) {
                        AL10.alSourcePlay(sourceID);
                        SimplySpeakers.LOGGER.debug("Streaming thread for network {}: Started playback after queuing first/initial buffer (ID: {}).",
                                networkKey, bufferIDs[i]);
                        playbackAttempted = true;
                    }
                }
                if (!continueStreaming) break; // If interrupted during initial fill, exit outer loop

                if (!playbackAttempted && initialDataLoaded) {
                    if (!resource.stopFlag.get() && !Thread.currentThread().isInterrupted()) {
                        int queued = AL10.alGetSourcei(sourceID, AL10.AL_BUFFERS_QUEUED);
                        if (queued > 0 && AL10.alGetSourcei(sourceID, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                            AL10.alSourcePlay(sourceID);
                            SimplySpeakers.LOGGER.debug("Streaming thread for network {}: Started playback (post-initial loop check).", networkKey);
                            playbackAttempted = true;
                        }
                    }
                }

                if (!playbackAttempted && initialDataLoaded) { // If still not playing but data was loaded (e.g. very short file)
                     SimplySpeakers.LOGGER.debug("Streaming thread for network {}: Playback not started but initial data loaded. EOF likely reached.", networkKey);
                } else if (!playbackAttempted) {
                    SimplySpeakers.LOGGER.debug("Streaming thread for network {}: Playback not attempted (no initial data or error). Not entering main streaming loop for this iteration.", networkKey);
                    // If no data loaded at all, and not looping, then stop. If looping, the outer loop will handle.
                    if (!isLooping) resource.stopFlag.set(true);
                    continueStreaming = isLooping; // Continue to next iteration only if looping
                    break; // Break from current try-catch, to re-evaluate outer loop
                }


                // Main streaming loop for current playback cycle
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
                                SimplySpeakers.LOGGER.debug("EOF in streaming loop for {}. Draining queued audio before restart.", networkKey);
                                endOfStream = true;
                            }
                        }
                    }
                    if (resource.stopFlag.get() || Thread.currentThread().isInterrupted()) {
                        break; // Exit inner streaming loop
                    }

                    int queuedBuffers = AL10.alGetSourcei(sourceID, AL10.AL_BUFFERS_QUEUED);
                    if (endOfStream && queuedBuffers == 0) {
                        playbackCompletedSuccessfully = true;
                        break;
                    }

                    if (AL10.alGetSourcei(sourceID, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING && initialDataLoaded) {
                         if (queuedBuffers > 0) {
                            SimplySpeakers.LOGGER.debug("Source {} on network {} stopped but has queued buffers. Restarting playback.", sourceID, networkKey);
                            AL10.alSourcePlay(sourceID);
                         } else if (!resource.stopFlag.get()) {
                            SimplySpeakers.LOGGER.warn("Buffer underrun for source {} on network {}. Waiting for more data.", sourceID, networkKey);
                         }
                    }

                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        SimplySpeakers.LOGGER.debug("Streaming thread for network {} interrupted during sleep.", networkKey);
                        resource.stopFlag.set(true); // Ensure stop on interrupt
                        break; // Exit inner streaming loop
                    }
                } // End of inner while loop (current playback cycle)

                // After inner loop finishes (either by EOF, stopFlag, or interrupt)
                if (resource.stopFlag.get() || Thread.currentThread().isInterrupted()) {
                    continueStreaming = false; // Do not loop if explicitly stopped or interrupted
                } else if (playbackCompletedSuccessfully) { // EOF reached for this cycle
                    if (isLooping) {
                        SimplySpeakers.LOGGER.debug("Audio track finished for {}. Looping enabled, restarting.", networkKey);
                        // Reset before the next cycle can queue new data. Deferring this
                        // races the next cycle and can remove its newly queued buffers.
                        if (AL10.alIsSource(sourceID)) {
                            AL10.alSourceStop(sourceID);
                            int queued = AL10.alGetSourcei(sourceID, AL10.AL_BUFFERS_QUEUED);
                            if (queued > 0) AL10.alSourceUnqueueBuffers(sourceID, new int[queued]);
                        }
                        // The outer while loop will re-initialize pcmAudioStream and start over.
                        // startPositionSeconds is already 0 for loops.
                        playbackCompletedSuccessfully = false; // Reset for next loop iteration
                        initialDataLoaded = false; // Reset for next loop iteration
                        // continueStreaming remains true
                    } else {
                        SimplySpeakers.LOGGER.debug("Audio track finished for {}. Looping disabled.", networkKey);
                        resource.stopFlag.set(true); // Set stop flag as playback is complete and not looping
                        continueStreaming = false;
                    }
                } else { // Inner loop exited for other reasons (e.g. no initial data, error before main loop)
                    SimplySpeakers.LOGGER.debug("Streaming for network {} ended without reaching EOF (e.g. no data, early stop). Looping: {}", networkKey, isLooping);
                    if (!isLooping) {
                        resource.stopFlag.set(true);
                    }
                    continueStreaming = isLooping && !resource.stopFlag.get(); // Only continue if looping and not explicitly stopped
                }

            } catch (UnsupportedAudioFileException e) {
                SimplySpeakers.LOGGER.error("Streaming thread ERROR for network {}: Unsupported audio file format for {}.", networkKey, filePath, e);
                if (resource != null) resource.stopFlag.set(true);
                continueStreaming = false; // Do not loop on this error
            } catch (IOException e) {
                SimplySpeakers.LOGGER.error("Streaming thread IO ERROR for network {} with file {}", networkKey, filePath, e);
                if (resource != null) resource.stopFlag.set(true);
                continueStreaming = false; // Do not loop on IO error
            } catch (Exception e) {
                SimplySpeakers.LOGGER.error("Critical error in streaming thread for network {} (source {})", networkKey, sourceID, e);
                if (resource != null) resource.stopFlag.set(true);
                continueStreaming = false; // Do not loop on critical error
            } finally {
                SimplySpeakers.LOGGER.trace("End of one streaming cycle for network {} (source {}). Looping: {}, ContinueStreaming: {}, StopFlag: {}",
                        networkKey, sourceID, isLooping, continueStreaming, (resource != null ? resource.stopFlag.get() : "null_resource"));
                if (pcmAudioStream != null) {
                    try {
                        // PERFORMANCE FIX: Close stream quickly without blocking operations
                        pcmAudioStream.close();
                        SimplySpeakers.LOGGER.debug("AudioInputStream closed by streaming thread for network {} after a cycle.", networkKey);
                    } catch (IOException e) {
                        // Don't log full stack trace to avoid console spam during batch cleanup
                        SimplySpeakers.LOGGER.warn("Error closing audioStream for network {}: {}", networkKey, e.getMessage());
                    }
                }
                // If not looping and this cycle ended (or error), ensure stopFlag is set.
                // If looping, the outer loop will decide.
                if (!continueStreaming && resource != null && !resource.stopFlag.get()) {
                    // This case handles when continueStreaming becomes false due to non-looping EOF or an error that prevents looping.
                    SimplySpeakers.LOGGER.trace("Streaming thread for network {} setting stopFlag in finally as looping is not continuing.", networkKey);
                    resource.stopFlag.set(true);
                } else if (resource != null && !resource.stopFlag.get() && !initialDataLoaded && !isLooping) {
                    // Original condition: if no data loaded and not looping, set stop.
                    SimplySpeakers.LOGGER.trace("Streaming thread for network {} setting stopFlag in finally due to no data loaded and not looping.", networkKey);
                    resource.stopFlag.set(true);
                }
            } // End of try-catch-finally for one playback cycle

            if (resource != null && resource.stopFlag.get()) {
                 SimplySpeakers.LOGGER.debug("Resource stopFlag is true for network {}. Breaking outer streaming loop.", networkKey);
                 continueStreaming = false; // Ensure outer loop terminates if stopFlag was set
            }
             if (Thread.currentThread().isInterrupted()){
                SimplySpeakers.LOGGER.debug("Thread for network {} is interrupted. Breaking outer streaming loop.", networkKey);
                continueStreaming = false;
                if(resource != null) resource.stopFlag.set(true);
            }

        } // End of while(continueStreaming) loop

        SimplySpeakers.LOGGER.debug("Streaming thread fully finished for network {} (source {}).", networkKey, sourceID);
    }

    // Stop method for specific speaker block position
    public static void stop(BlockPos pos) {
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
                        SimplySpeakers.LOGGER.debug("Stopped audio for network {} (last speaker at {} stopped)", networkKey, pos);
                    }
                } else {
                    SimplySpeakers.LOGGER.debug("Removed speaker at {} from network {} ({} speakers remain)", pos, networkKey, positions.size());
                    updateSpeakerVolumes();
                }
            }
        }
    }

    // Optimized stopAll method for fast world save performance
    public static void stopAll() {
        SimplySpeakers.LOGGER.debug("Stopping all playback... networkResources size: {}", networkResources.size());
        List<StreamingAudioResource> resourcesToStop = new ArrayList<>(networkResources.values());
        posToNetworkKey.clear();
        networkToPositions.clear();
        networkResources.clear();

        if (!resourcesToStop.isEmpty()) {
            Thread batchCleanupThread = new Thread(() -> {
                for (StreamingAudioResource resource : resourcesToStop) {
                    try {
                        if (resource != null) {
                            SimplySpeakers.LOGGER.debug("Stopping network {} with source {}", resource.networkKey, resource.sourceID);
                            resource.stopAndCleanup();
                        }
                    } catch (Exception e) {
                        SimplySpeakers.LOGGER.error("Error stopping resource {}", resource != null ? resource.networkKey : "null", e);
                    }
                }
                SimplySpeakers.LOGGER.debug("Batch cleanup completed for {} networks.", resourcesToStop.size());
            }, SimplySpeakers.MOD_ID + "-batch-cleanup");
            batchCleanupThread.setDaemon(true);
            batchCleanupThread.start();
        }

        SimplySpeakers.LOGGER.debug("Initiated fast shutdown for all playback.");
    }

    // Virtual Multi-Point spatial volume updater
    public static void updateSpeakerVolumes() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        if (networkResources.isEmpty()) {
            return;
        }
        SimplySpeakers.LOGGER.debug("[VolumeUpdate] Updating volumes for {} active networks", networkResources.size());

        Vec3 playerPos = player.position();
        float masterVolume = mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER);
        float recordVolume = mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.RECORDS);

        for (Map.Entry<String, StreamingAudioResource> entry : new ArrayList<>(networkResources.entrySet())) {
            String networkKey = entry.getKey();
            StreamingAudioResource resource = entry.getValue();

            if (resource == null || resource.stopFlag.get()) {
                continue;
            }

            Set<BlockPos> positions = networkToPositions.get(networkKey);
            if (positions == null || positions.isEmpty()) {
                resource.stopAndCleanup();
                networkResources.remove(networkKey);
                continue;
            }

            List<SpatialAudioCalculator.SpeakerEmitter> emitters = new ArrayList<>();
            List<BlockPos> deadPositions = new ArrayList<>();

            for (BlockPos speakerPos : positions) {
                net.minecraft.world.level.block.entity.BlockEntity blockEntity = mc.level.getBlockEntity(speakerPos);
                float maxVolume;
                int maxRange;
                float audioDropoff;

                if (blockEntity instanceof com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity speakerBlockEntity) {
                    maxVolume = speakerBlockEntity.getMaxVolume();
                    maxRange = speakerBlockEntity.getMaxRange();
                    audioDropoff = speakerBlockEntity.getAudioDropoff();
                } else if (blockEntity instanceof com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity proxySpeakerBlockEntity) {
                    maxVolume = proxySpeakerBlockEntity.getMaxVolume();
                    maxRange = proxySpeakerBlockEntity.getMaxRange();
                    audioDropoff = proxySpeakerBlockEntity.getAudioDropoff();
                } else {
                    if (mc.level.hasChunkAt(speakerPos)) {
                        deadPositions.add(speakerPos);
                    }
                    continue;
                }

                emitters.add(new SpatialAudioCalculator.SpeakerEmitter(
                        speakerPos.getX() + 0.5,
                        speakerPos.getY() + 0.5,
                        speakerPos.getZ() + 0.5,
                        maxRange,
                        maxVolume,
                        audioDropoff
                ));
            }

            for (BlockPos dead : deadPositions) {
                positions.remove(dead);
                posToNetworkKey.remove(dead);
            }

            if (emitters.isEmpty()) {
                if (positions.isEmpty()) {
                    resource.stopAndCleanup();
                    networkResources.remove(networkKey);
                    continue;
                }
                mc.tell(() -> {
                    if (AL10.alIsSource(resource.sourceID)) {
                        AL10.alSourcef(resource.sourceID, AL10.AL_GAIN, 0.0f);
                    }
                });
                continue;
            }

            SpatialAudioCalculator.VirtualEmitterResult result =
                    SpatialAudioCalculator.calculateVirtualEmitter(playerPos.x, playerPos.y, playerPos.z, emitters);

            final float finalGain = AudioGain.applyGameVolume(result.maxGain(), masterVolume, recordVolume);
            final float posX = (float) result.x();
            final float posY = (float) result.y();
            final float posZ = (float) result.z();

            mc.tell(() -> {
                StreamingAudioResource currentResource = networkResources.get(networkKey);
                if (currentResource != null && currentResource.sourceID == resource.sourceID && !currentResource.stopFlag.get()) {
                    try {
                        if (AL10.alIsSource(resource.sourceID)) {
                            AL10.alSource3f(resource.sourceID, AL10.AL_POSITION, posX, posY, posZ);
                            AL10.alSourcef(resource.sourceID, AL10.AL_GAIN, finalGain);
                            int error = AL10.alGetError();
                            if (error != AL10.AL_NO_ERROR) {
                                SimplySpeakers.LOGGER.error("OpenAL error setting spatial audio for source {}: {}",
                                        resource.sourceID, AL10.alGetString(error));
                            }
                        }
                    } catch (Exception e) {
                        SimplySpeakers.LOGGER.error("Error setting spatial audio for source {}", resource.sourceID, e);
                    }
                }
            });
        }
    }

    // --- Audio Decoding Logic ---
    private static AudioInputStream getPcmAudioStream(File audioFile) throws UnsupportedAudioFileException, IOException {
        String filePath = audioFile.getPath();
        if (filePath.toLowerCase().endsWith(".mp3")) {
            SimplySpeakers.LOGGER.debug("Using JLayer for MP3: {}", filePath);
            try (FileInputStream fis = new FileInputStream(audioFile)) {
                Bitstream bitstream = new Bitstream(fis);
                Decoder decoder = new Decoder();
                ByteArrayOutputStream pcmOutputStream = new ByteArrayOutputStream();

                Header frame;
                int frameCount = 0;
                int effectiveSampleRate = -1;
                int effectiveChannels = -1;

                while ((frame = bitstream.readFrame()) != null) {
                    if (frameCount == 0) { // First frame, capture format details
                        effectiveSampleRate = frame.frequency();
                        effectiveChannels = (frame.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
                        if (effectiveSampleRate <= 0 || effectiveChannels <= 0) {
                            throw new IOException("Failed to get valid sample rate or channels from first MP3 frame: " + filePath);
                        }
                    }
                    SampleBuffer outputBuffer = (SampleBuffer) decoder.decodeFrame(frame, bitstream);
                    short[] pcmShorts = outputBuffer.getBuffer();
                    int samplesRead = outputBuffer.getBufferLength();

                    byte[] pcmBytes = shortsToBytesLE(pcmShorts, samplesRead);
                    pcmOutputStream.write(pcmBytes);
                    
                    bitstream.closeFrame(); // Important to advance the stream
                    frameCount++;
                }

                if (frameCount == 0 || effectiveSampleRate <= 0 || effectiveChannels <= 0) {
                    throw new IOException("No MP3 frames decoded or invalid format for: " + filePath);
                }

                AudioFormat pcmFormat = new AudioFormat(effectiveSampleRate, 16, effectiveChannels, true, false);
                byte[] pcmData = pcmOutputStream.toByteArray();
                ByteArrayInputStream pcmByteStream = new ByteArrayInputStream(pcmData);
                AudioInputStream pcmInputStream = new AudioInputStream(pcmByteStream, pcmFormat, pcmData.length / pcmFormat.getFrameSize());
                SimplySpeakers.LOGGER.debug("MP3 decoded to PCM format: {}", pcmFormat);
                return pcmInputStream;

            } catch (BitstreamException | DecoderException e) {
                throw new IOException("Failed to decode MP3 using JLayer for: " + filePath, e);
            }
        } else {
            SimplySpeakers.LOGGER.debug("Reading non-MP3: {}", filePath);
            AudioInputStream initialStream = AudioSystem.getAudioInputStream(audioFile);
            AudioFormat initialFormat = initialStream.getFormat();
            SimplySpeakers.LOGGER.debug("Initial format: {}", initialFormat);

            AudioFormat targetPcmFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                initialFormat.getSampleRate(),
                16,
                initialFormat.getChannels(),
                initialFormat.getChannels() * 2,
                initialFormat.getSampleRate(),
                false
            );

            if (!initialFormat.matches(targetPcmFormat)) {
                SimplySpeakers.LOGGER.debug("Converting to target PCM format: {}", targetPcmFormat);
                if (AudioSystem.isConversionSupported(targetPcmFormat, initialFormat)) {
                    AudioInputStream convertedStream = AudioSystem.getAudioInputStream(targetPcmFormat, initialStream);
                    SimplySpeakers.LOGGER.debug("Converted to PCM format: {}", convertedStream.getFormat());
                    return convertedStream;
                } else {
                    initialStream.close();
                    throw new UnsupportedAudioFileException("Conversion to PCM_SIGNED 16-bit Little Endian not supported for: " + filePath + " from format " + initialFormat);
                }
            } else {
                SimplySpeakers.LOGGER.debug("Audio is already in target PCM format.");
                return initialStream;
            }
        }
    }

    private static byte[] shortsToBytesLE(short[] shorts, int count) {
        byte[] bytes = new byte[count * 2];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts, 0, count);
        return bytes;
    }

    private static int getOpenALFormat(AudioFormat format) {
        int openALFormat = -1;

        if (format.getChannels() == 1) {
            openALFormat = AL10.AL_FORMAT_MONO16;
        } else if (format.getChannels() == 2) {
            openALFormat = AL10.AL_FORMAT_STEREO16;
        } else {
            SimplySpeakers.LOGGER.error("Unsupported number of channels for OpenAL: {}", format.getChannels());
        }

        return openALFormat;
    }

    public static UUID startUpload(File file) {
        UUID transactionId = UUID.randomUUID();
        SimplySpeakers.LOGGER.debug("Starting upload process for file: " + file.getName() + " with transaction ID: " + transactionId);
        activeUploads.put(transactionId, new UploadProcess(file));
        return transactionId;
    }

    public static void handleUploadResponse(UUID transactionId, boolean allowed, int maxChunkSize, Component message) {
        UploadProcess process = activeUploads.get(transactionId);
        if (process == null) {
            SimplySpeakers.LOGGER.warn("Received upload response for unknown transaction ID: " + transactionId);
            return;
        }

        if (allowed) {
            SimplySpeakers.LOGGER.debug("Upload approved for transaction ID: " + transactionId + ". Starting data transfer.");
            process.start(transactionId, maxChunkSize);
        } else {
            SimplySpeakers.LOGGER.error("Upload denied for transaction ID: " + transactionId + ". Reason: " + message.getString());
            activeUploads.remove(transactionId);
            Screen currentScreen = Minecraft.getInstance().screen;
            if (currentScreen instanceof SpeakerScreen) {
                ((SpeakerScreen) currentScreen).setStatusMessage(message);
            }
        }
    }

    public static void handleUploadAcknowledgement(UUID transactionId, boolean success, Component message, BlockPos blockPos) {
        if (success) {
            SimplySpeakers.LOGGER.debug("Upload acknowledged for transaction ID: " + transactionId);
            NetworkManager.sendToServer(new RequestAudioListPacketC2S(blockPos));
        } else {
            SimplySpeakers.LOGGER.error("Upload failed for transaction ID: " + transactionId + ". Reason: " + message.getString());
        }
        activeUploads.remove(transactionId);
        Screen currentScreen = Minecraft.getInstance().screen;
        if (currentScreen instanceof SpeakerScreen) {
            SimplySpeakers.LOGGER.debug("Setting status message: " + message.getString());
            ((SpeakerScreen) currentScreen).setStatusMessage(message);
        }
    }

    private static void requestFileFromServer(String audioId, String filename) {
        if (activeDownloads.containsKey(audioId)) {
            return; // Already downloading
        }
        activeDownloads.put(audioId, new DownloadProcess(audioId, filename));
        NetworkManager.sendToServer(new RequestAudioFilePacketC2S(audioId));
    }

    public static void clearAudioList() {
        audioList.clear();
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

        process.addData(data);

        if (isLast) {
            process.complete();
            activeDownloads.remove(audioId);
        }
    }

    private static class UploadProcess {
        private final File file;
        private byte[] fileData;

        public UploadProcess(File file) {
            this.file = file;
        }

        public void start(UUID transactionId, int chunkSize) {
            try {
                this.fileData = Files.readAllBytes(file.toPath());
                UploadProgressLogger.logStart(SimplySpeakers.LOGGER, transactionId, fileData.length);
                new Thread(() -> {
                    int offset = 0;
                    while (offset < fileData.length) {
                        int length = Math.min(chunkSize, fileData.length - offset);
                        byte[] chunk = new byte[length];
                        System.arraycopy(fileData, offset, chunk, 0, length);
                        UploadProgressLogger.logChunk(SimplySpeakers.LOGGER, transactionId, offset, length, fileData.length);
                        NetworkManager.sendToServer(new UploadAudioDataPacketC2S(transactionId, chunk));
                        offset += length;
                        try {
                            Thread.sleep(10); // Small delay to avoid overwhelming the network
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    SimplySpeakers.LOGGER.debug("Finished sending file data for transaction ID: " + transactionId);
                }).start();
            } catch (IOException e) {
                SimplySpeakers.LOGGER.error("Failed to read file for upload: " + file.getName(), e);
            }
        }
    }

    private static class DownloadProcess {
        private final String audioId;
        private final String filename;
        private final ByteArrayOutputStream dataStream = new ByteArrayOutputStream();

        public DownloadProcess(String audioId, String filename) {
            this.audioId = audioId;
            this.filename = filename;
        }

        public void addData(byte[] data) {
            try {
                dataStream.write(data);
            } catch (IOException e) {
                // Should not happen with ByteArrayOutputStream
            }
        }

        public void complete() {
            if (!CACHE_DIR.exists()) {
                CACHE_DIR.mkdirs();
            }
            String extension = com.google.common.io.Files.getFileExtension(filename);
            File cachedFile = new File(CACHE_DIR, audioId + (extension.isEmpty() ? "" : "." + extension));
            try {
                Files.write(cachedFile.toPath(), dataStream.toByteArray());
                SimplySpeakers.LOGGER.info("CLIENT: Download complete for {}. File saved to cache.", audioId);

                // Check for and handle pending play requests
                PlayRequest pendingPlay = pendingPlays.remove(audioId);
                if (pendingPlay != null) {
                    SimplySpeakers.LOGGER.info("CLIENT: Pending play request found for {}. Initiating playback.", audioId);
                    String networkKey = resolveNetworkKey(pendingPlay.pos);
                    playFromFile(networkKey, pendingPlay.pos, cachedFile.getAbsolutePath(), pendingPlay.startPositionSeconds, pendingPlay.isLooping);
                }
            } catch (IOException e) {
                SimplySpeakers.LOGGER.error("Failed to write cached audio file: {}", cachedFile.toPath(), e);
            }
        }
    }

    private static class PlayRequest {
        final BlockPos pos;
        final float startPositionSeconds;
        final boolean isLooping;

        PlayRequest(BlockPos pos, float startPositionSeconds, boolean isLooping) {
            this.pos = pos;
            this.startPositionSeconds = startPositionSeconds;
            this.isLooping = isLooping;
        }
    }
}
