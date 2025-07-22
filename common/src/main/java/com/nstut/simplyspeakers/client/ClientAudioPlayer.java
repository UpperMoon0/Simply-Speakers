package com.nstut.simplyspeakers.client;

import net.minecraft.core.BlockPos;
import org.lwjgl.openal.AL10;
import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.network.PacketRegistries;
import com.nstut.simplyspeakers.network.RequestAudioFilePacketC2S;
import com.nstut.simplyspeakers.network.RequestAudioListPacketC2S;
import com.nstut.simplyspeakers.network.UploadAudioDataPacketC2S;
import net.minecraft.client.Minecraft;
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
import java.nio.file.Path;
import java.util.UUID;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.DecoderException;

public class ClientAudioPlayer {

    private static final File CACHE_DIR = new File(Minecraft.getInstance().gameDirectory, "simply_speakers_cache");
    private static final Map<BlockPos, StreamingAudioResource> speakerResources = new ConcurrentHashMap<>();
    private static final Map<UUID, UploadProcess> activeUploads = new ConcurrentHashMap<>();
    private static final Map<String, DownloadProcess> activeDownloads = new ConcurrentHashMap<>();
    private static final int NUM_BUFFERS = 3;
    private static final int BUFFER_SIZE_SECONDS = 1;

    private static class StreamingAudioResource {
        final int sourceID;
        final int[] bufferIDs; // Array of buffer IDs
        // REMOVED: final AudioInputStream audioStream;
        final Thread streamingThread; // The thread handling buffer refills
        final AtomicBoolean stopFlag = new AtomicBoolean(false); // Flag to signal thread termination
        final BlockPos position;
        StreamingAudioResource(int sourceID, int[] bufferIDs, Thread streamingThread, BlockPos pos, boolean isLooping) { // AudioInputStream and AudioFormat removed
            this.sourceID = sourceID;
            this.bufferIDs = bufferIDs;
            this.streamingThread = streamingThread;
            this.position = pos; // Store position
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
                }, "SimplySpeakers-Cleanup-" + position.toString());
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
                    System.out.println("[SimplySpeakers] Fast cleanup completed for source " + sourceID + " at " + position);
                } else {
                    System.out.println("[SimplySpeakers] Source " + sourceID + " at " + position + " already invalid, skipping cleanup.");
                }
            } catch (Exception e) {
                System.err.println("[SimplySpeakers] Error during OpenAL cleanup for source " + sourceID + " at " + position + ": " + e.getMessage());
                // Continue cleanup despite errors to prevent resource leaks
            }
        }
    }

    public static void play(BlockPos pos, AudioFileMetadata metadata, float startPositionSeconds, boolean isLooping) {
        stop(pos);

        if (!CACHE_DIR.exists()) {
            CACHE_DIR.mkdirs();
        }

        File cachedFile = new File(CACHE_DIR, metadata.getUuid());
        if (cachedFile.exists()) {
            playFromFile(pos, cachedFile.getAbsolutePath(), startPositionSeconds, isLooping);
        } else {
            requestFileFromServer(metadata.getUuid());
        }
    }

    private static void playFromFile(BlockPos pos, String filePath, float startPositionSeconds, boolean isLooping) {
        Minecraft.getInstance().tell(() -> {
            try {
                int sourceID = AL10.alGenSources();
                int[] bufferIDs = new int[NUM_BUFFERS];
                AL10.alGenBuffers(bufferIDs);

                AL10.alSource3f(sourceID, AL10.AL_POSITION, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f);
                AL10.alSourcef(sourceID, AL10.AL_ROLLOFF_FACTOR, 0.0f);
                AL10.alSourcef(sourceID, AL10.AL_GAIN, 1.0f);
                AL10.alSourcei(sourceID, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);

                Thread streamingThread = new Thread(() -> streamAudioData(pos, sourceID, bufferIDs, filePath, startPositionSeconds, isLooping),
                        "SimplySpeakers Streaming Thread - " + pos);
                streamingThread.setDaemon(true);

                StreamingAudioResource resource = new StreamingAudioResource(sourceID, bufferIDs, streamingThread, pos, isLooping);
                speakerResources.put(pos, resource);
                streamingThread.start();
            } catch (Exception e) {
                e.printStackTrace();
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
    private static void streamAudioData(BlockPos pos, int sourceID, int[] bufferIDs, String filePath, float startPositionSeconds, boolean isLooping) {
        StreamingAudioResource resource = speakerResources.get(pos);

        // Loop control
        boolean continueStreaming = true;

        while (continueStreaming) { // Outer loop for restarting playback if isLooping is true
            if (resource == null || resource.sourceID != sourceID) {
                System.err.println("[SimplySpeakers] Streaming thread for " + pos + " (source " + sourceID + ") found resource mismatch or missing. Aborting outer loop.");
                continueStreaming = false; // Exit outer loop
                break; // Exit while(continueStreaming)
            }

            AudioInputStream pcmAudioStream = null;
            boolean initialDataLoaded = false; // Declare here for visibility in finally
            boolean playbackCompletedSuccessfully = false; // Flag to indicate if the current playback cycle finished without early stop

            try {
                File audioFile = new File(filePath);
                if (!audioFile.exists()) {
                    System.err.println("[SimplySpeakers] Streaming thread ERROR: Audio file not found: " + filePath + " for " + pos);
                    resource.stopFlag.set(true); // Signal stop for this attempt
                    continueStreaming = false; // Do not loop if file not found
                    break; // Exit while(continueStreaming)
                }

                System.out.println("[SimplySpeakers] Streaming thread for " + pos + ": Attempting to get PCM stream for " + filePath + (isLooping ? " (Looping)" : ""));
                pcmAudioStream = getPcmAudioStream(audioFile);
                
                if (pcmAudioStream == null) {
                    System.err.println("[SimplySpeakers] Streaming thread ERROR: Could not get PCM audio stream for: " + filePath + " for " + pos);
                    resource.stopFlag.set(true);
                    continueStreaming = false;
                    break;
                }
                System.out.println("[SimplySpeakers] Streaming thread for " + pos + ": Successfully got PCM stream.");

                AudioFormat format = pcmAudioStream.getFormat();
                // Only apply startPositionSeconds on the very first playback, not on loops
                if (startPositionSeconds > 0 && continueStreaming) { // Check continueStreaming to ensure this is the first attempt if looping
                    long bytesToSkip = 0;
                    float frameRate = format.getFrameRate();
                    int frameSize = format.getFrameSize();

                    if (frameRate > 0 && frameSize > 0) {
                        long framesToSkip = (long) (startPositionSeconds * frameRate);
                        bytesToSkip = framesToSkip * frameSize;
                        System.out.println("[SimplySpeakers] Streaming thread for " + pos + ": Calculated skip: " + bytesToSkip + " bytes for " + startPositionSeconds + "s");
                    } else {
                        System.err.println("[SimplySpeakers] Streaming thread WARNING for " + pos + ": Invalid format for seeking: " + format);
                    }

                    if (bytesToSkip > 0) {
                        System.out.println("[SimplySpeakers] Streaming thread for " + pos + ": Attempting to skip " + bytesToSkip + " bytes.");
                        long skipped = skipFully(pcmAudioStream, bytesToSkip);
                        if (skipped < bytesToSkip) {
                            System.err.println("[SimplySpeakers] Streaming thread WARNING for " + pos + ": Could only skip " + skipped + "/" + bytesToSkip + " bytes. Reached EOF or error.");
                            resource.stopFlag.set(true);
                            continueStreaming = false;
                            break;
                        }
                        System.out.println("[SimplySpeakers] Streaming thread for " + pos + ": Successfully skipped " + skipped + " bytes.");
                    }
                    startPositionSeconds = 0; // Reset for subsequent loops
                }


                boolean playbackAttempted = false;
                initialDataLoaded = false;

                int alFormat = getOpenALFormat(format);
                if (alFormat == -1) {
                    System.err.println("[SimplySpeakers] Streaming thread ERROR for " + pos + ": Unsupported audio format for OpenAL: " + format);
                    resource.stopFlag.set(true);
                    continueStreaming = false;
                    break;
                }
                int bufferSizeBytes = (int) (format.getFrameRate() * format.getFrameSize() * BUFFER_SIZE_SECONDS);
                byte[] bufferData = new byte[bufferSizeBytes];

                // Initial buffer filling
                for (int i = 0; i < NUM_BUFFERS; i++) {
                    if (resource.stopFlag.get() || Thread.currentThread().isInterrupted()) {
                        System.out.println("[SimplySpeakers] Streaming thread for " + pos + ": Stop signal or interrupt during initial buffering.");
                        continueStreaming = false; // Stop looping if interrupted
                        break; // Break from initial buffer filling
                    }

                    int bytesRead = pcmAudioStream.read(bufferData, 0, bufferData.length);
                    if (bytesRead <= 0) {
                        System.out.println("[SimplySpeakers] Streaming thread for " + pos + ": EOF or read error during initial buffering. Bytes read: " + bytesRead);
                        // Don't set stopFlag.set(true) here if looping, let the outer loop decide
                        break; // Break from initial buffer filling, will check isLooping later
                    }

                    ByteBuffer alBuffer = ByteBuffer.allocateDirect(bytesRead).order(ByteOrder.nativeOrder());
                    alBuffer.put(bufferData, 0, bytesRead).flip();

                    AL10.alBufferData(bufferIDs[i], alFormat, alBuffer, (int) format.getSampleRate());
                    AL10.alSourceQueueBuffers(sourceID, bufferIDs[i]);
                    initialDataLoaded = true;

                    if (!playbackAttempted) {
                        AL10.alSourcePlay(sourceID);
                        System.out.println("[SimplySpeakers] Streaming thread for " + pos + ": Started playback after queuing first/initial buffer (ID: " + bufferIDs[i] + ").");
                        playbackAttempted = true;
                    }
                }
                if (!continueStreaming) break; // If interrupted during initial fill, exit outer loop

                if (!playbackAttempted && initialDataLoaded) {
                    if (!resource.stopFlag.get() && !Thread.currentThread().isInterrupted()) {
                        int queued = AL10.alGetSourcei(sourceID, AL10.AL_BUFFERS_QUEUED);
                        if (queued > 0 && AL10.alGetSourcei(sourceID, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                            AL10.alSourcePlay(sourceID);
                            System.out.println("[SimplySpeakers] Streaming thread for " + pos + ": Started playback (post-initial loop check).");
                            playbackAttempted = true;
                        }
                    }
                }

                if (!playbackAttempted && initialDataLoaded) { // If still not playing but data was loaded (e.g. very short file)
                     System.out.println("[SimplySpeakers] Streaming thread for " + pos + ": Playback not started but initial data loaded. EOF likely reached.");
                } else if (!playbackAttempted) {
                    System.out.println("[SimplySpeakers] Streaming thread for " + pos + ": Playback not attempted (no initial data or error). Not entering main streaming loop for this iteration.");
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
                        int bytesRead = pcmAudioStream.read(bufferData, 0, bufferData.length);

                        if (bytesRead > 0) {
                            ByteBuffer alBuffer = ByteBuffer.allocateDirect(bytesRead).order(ByteOrder.nativeOrder());
                            alBuffer.put(bufferData, 0, bytesRead).flip();
                            AL10.alBufferData(bufferID, alFormat, alBuffer, (int) format.getSampleRate());
                            AL10.alSourceQueueBuffers(sourceID, bufferID);
                        } else { // EOF reached during streaming
                            System.out.println("[SimplySpeakers] EOF in streaming loop for " + pos + ". Buffer " + bufferID + " not re-queued. Bytes read: " + bytesRead);
                            // Don't set resource.stopFlag.set(true) here if looping. Let the outer logic handle it.
                            playbackCompletedSuccessfully = true; // Mark that this cycle finished normally (EOF)
                            break; // Exit inner streaming loop
                        }
                    }
                    if (playbackCompletedSuccessfully || resource.stopFlag.get() || Thread.currentThread().isInterrupted()) {
                        break; // Exit inner streaming loop
                    }

                    if (AL10.alGetSourcei(sourceID, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING && initialDataLoaded) {
                         int queuedBuffers = AL10.alGetSourcei(sourceID, AL10.AL_BUFFERS_QUEUED);
                         if (queuedBuffers > 0) {
                            System.out.println("[SimplySpeakers] Source " + sourceID + " at " + pos + " stopped but has queued buffers. Restarting playback.");
                            AL10.alSourcePlay(sourceID);
                         } else if (!resource.stopFlag.get()) {
                            System.out.println("[SimplySpeakers] Buffer underrun for source " + sourceID + " at " + pos + ". Waiting for more data.");
                            // If underrun and no more data is coming (EOF was hit in read), this might lead to stop.
                         }
                    }

                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.out.println("[SimplySpeakers] Streaming thread for " + pos + " interrupted during sleep.");
                        resource.stopFlag.set(true); // Ensure stop on interrupt
                        break; // Exit inner streaming loop
                    }
                } // End of inner while loop (current playback cycle)

                // After inner loop finishes (either by EOF, stopFlag, or interrupt)
                if (resource.stopFlag.get() || Thread.currentThread().isInterrupted()) {
                    continueStreaming = false; // Do not loop if explicitly stopped or interrupted
                } else if (playbackCompletedSuccessfully) { // EOF reached for this cycle
                    if (isLooping) {
                        System.out.println("[SimplySpeakers] Audio track finished for " + pos + ". Looping enabled, restarting.");
                        // Clean up OpenAL source state for restart, but keep buffers
                        Minecraft.getInstance().tell(() -> {
                            if (AL10.alIsSource(sourceID)) {
                                AL10.alSourceStop(sourceID);
                                int processed = AL10.alGetSourcei(sourceID, AL10.AL_BUFFERS_PROCESSED);
                                if (processed > 0) AL10.alSourceUnqueueBuffers(sourceID, new int[processed]);
                                int queued = AL10.alGetSourcei(sourceID, AL10.AL_BUFFERS_QUEUED);
                                if (queued > 0) AL10.alSourceUnqueueBuffers(sourceID, new int[queued]);
                                AL10.alSourcei(sourceID, AL10.AL_BUFFER, 0); // Detach any buffer pointer
                            }
                        });
                        // The outer while loop will re-initialize pcmAudioStream and start over.
                        // startPositionSeconds is already 0 for loops.
                        playbackCompletedSuccessfully = false; // Reset for next loop iteration
                        initialDataLoaded = false; // Reset for next loop iteration
                        // continueStreaming remains true
                    } else {
                        System.out.println("[SimplySpeakers] Audio track finished for " + pos + ". Looping disabled.");
                        resource.stopFlag.set(true); // Set stop flag as playback is complete and not looping
                        continueStreaming = false;
                    }
                } else { // Inner loop exited for other reasons (e.g. no initial data, error before main loop)
                    System.out.println("[SimplySpeakers] Streaming for " + pos + " ended without reaching EOF (e.g. no data, early stop). Looping: " + isLooping);
                    if (!isLooping) {
                        resource.stopFlag.set(true);
                    }
                    continueStreaming = isLooping && !resource.stopFlag.get(); // Only continue if looping and not explicitly stopped
                }

            } catch (UnsupportedAudioFileException e) {
                System.err.println("[SimplySpeakers] Streaming thread ERROR for " + pos + ": Unsupported audio file format for " + filePath + ". " + e.getMessage());
                if (resource != null) resource.stopFlag.set(true);
                continueStreaming = false; // Do not loop on this error
            } catch (IOException e) {
                System.err.println("[SimplySpeakers] Streaming thread IO ERROR for " + pos + " with file " + filePath + ": " + e.getMessage());
                if (resource != null) resource.stopFlag.set(true);
                continueStreaming = false; // Do not loop on IO error
            } catch (Exception e) {
                System.err.println("[SimplySpeakers] Critical error in streaming thread for " + pos + " (source " + sourceID + "): " + e.getMessage());
                e.printStackTrace();
                if (resource != null) resource.stopFlag.set(true);
                continueStreaming = false; // Do not loop on critical error
            } finally {
                System.out.println("[SimplySpeakers] End of one streaming cycle for " + pos + " (source " + sourceID + "). Looping: " + isLooping + ", ContinueStreaming: " + continueStreaming + ", StopFlag: " + (resource != null ? resource.stopFlag.get() : "null_resource"));
                if (pcmAudioStream != null) {
                    try {
                        // PERFORMANCE FIX: Close stream quickly without blocking operations
                        pcmAudioStream.close();
                        System.out.println("[SimplySpeakers] AudioInputStream closed by streaming thread for " + pos + " after a cycle.");
                    } catch (IOException e) {
                        // Don't log full stack trace to avoid console spam during batch cleanup
                        System.err.println("[SimplySpeakers] Error closing audioStream for " + pos + ": " + e.getMessage());
                    }
                }
                // If not looping and this cycle ended (or error), ensure stopFlag is set.
                // If looping, the outer loop will decide.
                if (!continueStreaming && resource != null && !resource.stopFlag.get()) {
                    // This case handles when continueStreaming becomes false due to non-looping EOF or an error that prevents looping.
                    System.out.println("[SimplySpeakers] Streaming thread for " + pos + " setting stopFlag in finally as looping is not continuing.");
                    resource.stopFlag.set(true);
                } else if (resource != null && !resource.stopFlag.get() && !initialDataLoaded && !isLooping) {
                    // Original condition: if no data loaded and not looping, set stop.
                    System.out.println("[SimplySpeakers] Streaming thread for " + pos + " setting stopFlag in finally due to no data loaded and not looping.");
                    resource.stopFlag.set(true);
                }
            } // End of try-catch-finally for one playback cycle

            if (resource != null && resource.stopFlag.get()) {
                 System.out.println("[SimplySpeakers] Resource stopFlag is true for " + pos + ". Breaking outer streaming loop.");
                 continueStreaming = false; // Ensure outer loop terminates if stopFlag was set
            }
             if (Thread.currentThread().isInterrupted()){
                System.out.println("[SimplySpeakers] Thread for " + pos + " is interrupted. Breaking outer streaming loop.");
                continueStreaming = false;
                if(resource != null) resource.stopFlag.set(true);
            }

        } // End of while(continueStreaming) loop

        System.out.println("[SimplySpeakers] Streaming thread fully finished for " + pos + " (source " + sourceID + ").");
        // Final cleanup is handled by stopAndCleanup when resource is removed or stopAll is called.
        // If the loop finishes because stopFlag was set (e.g. by stop(pos) externally),
        // the resource.stopAndCleanup() will eventually be called.
        // If it finishes due to non-looping EOF, stopFlag is set, and cleanup will occur.
    }

    // Updated stop method
    public static void stop(BlockPos pos) {
        StreamingAudioResource resource = speakerResources.remove(pos);
        if (resource != null) {
            resource.stopAndCleanup();
            System.out.println("[SimplySpeakers] Stopped audio for speaker at " + pos);
        }
    }

    // Optimized stopAll method for fast world save performance
    public static void stopAll() {
         System.out.println("[SimplySpeakers] Stopping all playback...");
         // Create a copy of resources to avoid ConcurrentModificationException
         List<Map.Entry<BlockPos, StreamingAudioResource>> resourcesToStop = new ArrayList<>(speakerResources.entrySet());
         System.out.println("[SimplySpeakers] Found " + resourcesToStop.size() + " active speakers to stop.");
         
         // PERFORMANCE FIX: Clear the map immediately to prevent new operations during cleanup
         speakerResources.clear();
         
         // Batch cleanup using a single background thread to prevent blocking world save
         if (!resourcesToStop.isEmpty()) {
             Thread batchCleanupThread = new Thread(() -> {
                 for (Map.Entry<BlockPos, StreamingAudioResource> entry : resourcesToStop) {
                     try {
                         StreamingAudioResource resource = entry.getValue();
                         if (resource != null) {
                             resource.stopAndCleanup();
                         }
                     } catch (Exception e) {
                         System.err.println("[SimplySpeakers] Error stopping speaker at " + entry.getKey() + ": " + e.getMessage());
                     }
                 }
                 System.out.println("[SimplySpeakers] Batch cleanup completed for " + resourcesToStop.size() + " speakers.");
             }, "SimplySpeakers-BatchCleanup");
             batchCleanupThread.setDaemon(true);
             batchCleanupThread.start();
         }
         
         System.out.println("[SimplySpeakers] Initiated fast shutdown for all playback.");
    }

    // Added back updateSpeakerVolumes method
    public static void updateSpeakerVolumes() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || speakerResources.isEmpty()) {
            return;
        }

        Vec3 playerPos = player.position();
        double maxRange = Config.speakerRange; // Make sure Config.speakerRange is accessible
        double maxRangeSq = maxRange * maxRange;

        List<Map.Entry<BlockPos, StreamingAudioResource>> entries = new ArrayList<>(speakerResources.entrySet());

        for (Map.Entry<BlockPos, StreamingAudioResource> entry : entries) {
            BlockPos speakerPos = entry.getKey();
            StreamingAudioResource resource = entry.getValue();

            if (resource == null || resource.stopFlag.get()) {
                continue;
            }

            Vec3 speakerCenterPos = new Vec3(speakerPos.getX() + 0.5, speakerPos.getY() + 0.5, speakerPos.getZ() + 0.5);
            double distSq = playerPos.distanceToSqr(speakerCenterPos);

            float gain = 1.0f;

            if (distSq >= maxRangeSq) {
                gain = 0.0f;
            } else if (distSq > 0) {
                double distance = Math.sqrt(distSq);
                gain = (float) Math.pow(1.0 - (distance / maxRange), 2.0);
                gain = Math.max(0.0f, Math.min(1.0f, gain));
            }

            final float finalGain = gain;
            mc.tell(() -> {
                 StreamingAudioResource currentResource = speakerResources.get(speakerPos);
                 if (currentResource != null && currentResource.sourceID == resource.sourceID && !currentResource.stopFlag.get()) {
                    try {
                        if (AL10.alIsSource(resource.sourceID)) { // Check if source is still valid
                            AL10.alSourcef(resource.sourceID, AL10.AL_GAIN, finalGain);
                            int error = AL10.alGetError();
                            if (error != AL10.AL_NO_ERROR) {
                                System.err.println("[SimplySpeakers] OpenAL error setting gain for source " + resource.sourceID + ": " + AL10.alGetString(error));
                            }
                        } else {
                             // System.out.println("[SimplySpeakers] Source " + resource.sourceID + " no longer valid when trying to set gain.");
                        }
                    } catch (Exception e) {
                        System.err.println("[SimplySpeakers] Error setting gain for source " + resource.sourceID + ": " + e.getMessage());
                    }
                 }
            });
        }
    }

// --- Audio Decoding Logic (Moved to helper method) ---
    private static AudioInputStream getPcmAudioStream(File audioFile) throws UnsupportedAudioFileException, IOException {
        String filePath = audioFile.getPath();
        // pcmInputStream and pcmFormat are declared later, specific to each branch

        if (filePath.toLowerCase().endsWith(".mp3")) {
            System.out.println("[SimplySpeakers] Decoding MP3: " + filePath);
            try (InputStream fileStream = new FileInputStream(audioFile);
                 ByteArrayOutputStream pcmOutputStream = new ByteArrayOutputStream()) {

                Bitstream bitstream = new Bitstream(fileStream);
                Decoder decoder = new Decoder();
                // Obuffer obuffer = new javazoom.jl.decoder.Obuffer(); // Correct instantiation if needed, but Decoder typically manages its own output buffer
                // decoder.setObuffer(obuffer); // Usually not needed as Decoder creates its own SampleBuffer

                Header frame;
                int frameCount = 0;
                float effectiveSampleRate = -1;
                int effectiveChannels = -1;

                while ((frame = bitstream.readFrame()) != null) {
                    if (frameCount == 0) { // First frame, capture format details
                        effectiveSampleRate = frame.frequency();
                        effectiveChannels = (frame.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
                        if (effectiveSampleRate <= 0 || effectiveChannels <= 0) {
                            throw new IOException("Failed to get valid sample rate or channels from first MP3 frame: " + filePath);
                        }
                    }
                    // The decoder.decodeFrame method takes the header and the bitstream.
                    // It returns a SampleBuffer.
                    SampleBuffer outputBuffer = (SampleBuffer) decoder.decodeFrame(frame, bitstream);
                    short[] pcmShorts = outputBuffer.getBuffer();
                    int samplesRead = outputBuffer.getBufferLength();

                    // Convert short[] to byte[] (Little Endian for PCM)
                    byte[] pcmBytes = shortsToBytesLE(pcmShorts, samplesRead);
                    pcmOutputStream.write(pcmBytes);
                    
                    bitstream.closeFrame(); // Important to advance the stream
                    frameCount++;
                }

                if (frameCount == 0 || effectiveSampleRate <= 0 || effectiveChannels <= 0) {
                    throw new IOException("No MP3 frames decoded or invalid format for: " + filePath);
                }

                AudioFormat pcmFormat = new AudioFormat(effectiveSampleRate, 16, effectiveChannels, true, false); // true for signed, false for big-endian (PCM is usually LE)
                byte[] pcmData = pcmOutputStream.toByteArray();
                ByteArrayInputStream pcmByteStream = new ByteArrayInputStream(pcmData);
                AudioInputStream pcmInputStream = new AudioInputStream(pcmByteStream, pcmFormat, pcmData.length / pcmFormat.getFrameSize());
                System.out.println("[SimplySpeakers] MP3 decoded to PCM format: " + pcmFormat);
                return pcmInputStream;

            } catch (BitstreamException | DecoderException e) {
                throw new IOException("Failed to decode MP3 using JLayer for: " + filePath, e);
            }
        } else {
            // Standard WAV/OGG handling
            System.out.println("[SimplySpeakers] Reading non-MP3: " + filePath);
            AudioInputStream initialStream = AudioSystem.getAudioInputStream(audioFile);
            AudioFormat initialFormat = initialStream.getFormat();
            System.out.println("[SimplySpeakers] Initial format: " + initialFormat);

            // Define target PCM format (16-bit signed LE)
            AudioFormat targetPcmFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                initialFormat.getSampleRate(),
                16, // 16-bit
                initialFormat.getChannels(),
                initialFormat.getChannels() * 2, // Frame size: channels * bytes_per_sample (16-bit = 2 bytes)
                initialFormat.getSampleRate(), // Frame rate
                false // Little-endian
            );

            // Check if conversion is needed
            if (!initialFormat.matches(targetPcmFormat)) {
                System.out.println("[SimplySpeakers] Converting to target PCM format: " + targetPcmFormat);
                if (AudioSystem.isConversionSupported(targetPcmFormat, initialFormat)) {
                    AudioInputStream convertedStream = AudioSystem.getAudioInputStream(targetPcmFormat, initialStream);
                    System.out.println("[SimplySpeakers] Converted to PCM format: " + convertedStream.getFormat());
                    return convertedStream;
                } else {
                    initialStream.close();
                    throw new UnsupportedAudioFileException("Conversion to PCM_SIGNED 16-bit Little Endian not supported for: " + filePath + " from format " + initialFormat);
                }
            } else {
                System.out.println("[SimplySpeakers] Audio is already in target PCM format.");
                return initialStream; // Already in a suitable PCM format
            }
        }
    }


    // Helper method to convert short array (PCM) to byte array (Little Endian)
    private static byte[] shortsToBytesLE(short[] shorts, int count) {
        byte[] bytes = new byte[count * 2]; // Each short is 2 bytes
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts, 0, count);
        return bytes;
    }


    private static int getOpenALFormat(AudioFormat format) {
        // Determine OpenAL format based on audio format details
        int openALFormat = -1;

        if (format.getChannels() == 1) {
            openALFormat = AL10.AL_FORMAT_MONO16;
        } else if (format.getChannels() == 2) {
            openALFormat = AL10.AL_FORMAT_STEREO16;
        } else {
            System.err.println("[SimplySpeakers] Unsupported number of channels for OpenAL: " + format.getChannels());
        }

        return openALFormat;
    }

    public static UUID startUpload(File file) {
        UUID transactionId = UUID.randomUUID();
        SimplySpeakers.LOGGER.info("Starting upload process for file: " + file.getName() + " with transaction ID: " + transactionId);
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
            SimplySpeakers.LOGGER.info("Upload approved for transaction ID: " + transactionId + ". Starting data transfer.");
            process.start(transactionId, maxChunkSize);
        } else {
            SimplySpeakers.LOGGER.error("Upload denied for transaction ID: " + transactionId + ". Reason: " + message.getString());
            activeUploads.remove(transactionId);
            // TODO: Display error message to user
        }
    }

    public static void handleUploadAcknowledgement(UUID transactionId, boolean success, Component message, BlockPos blockPos) {
        if (success) {
            SimplySpeakers.LOGGER.info("Upload acknowledged for transaction ID: " + transactionId);
            PacketRegistries.CHANNEL.sendToServer(new RequestAudioListPacketC2S(blockPos));
        } else {
            SimplySpeakers.LOGGER.error("Upload failed for transaction ID: " + transactionId + ". Reason: " + message.getString());
        }
        activeUploads.remove(transactionId);
        // TODO: Display message to user
    }

    private static void requestFileFromServer(String audioId) {
        if (activeDownloads.containsKey(audioId)) {
            return; // Already downloading
        }
        activeDownloads.put(audioId, new DownloadProcess(audioId));
        PacketRegistries.CHANNEL.sendToServer(new RequestAudioFilePacketC2S(audioId));
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
                SimplySpeakers.LOGGER.info("Starting to send file data for transaction ID: " + transactionId + ". Total size: " + fileData.length);
                new Thread(() -> {
                    int offset = 0;
                    while (offset < fileData.length) {
                        int length = Math.min(chunkSize, fileData.length - offset);
                        byte[] chunk = new byte[length];
                        System.arraycopy(fileData, offset, chunk, 0, length);
                        SimplySpeakers.LOGGER.info("Sending chunk for transaction ID: " + transactionId + ". Offset: " + offset + ", Length: " + length);
                        PacketRegistries.CHANNEL.sendToServer(new UploadAudioDataPacketC2S(transactionId, chunk));
                        offset += length;
                        try {
                            Thread.sleep(10); // Small delay to avoid overwhelming the network
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    SimplySpeakers.LOGGER.info("Finished sending file data for transaction ID: " + transactionId);
                }).start();
            } catch (IOException e) {
                SimplySpeakers.LOGGER.error("Failed to read file for upload: " + file.getName(), e);
            }
        }
    }

    private static class DownloadProcess {
        private final String audioId;
        private final ByteArrayOutputStream dataStream = new ByteArrayOutputStream();

        public DownloadProcess(String audioId) {
            this.audioId = audioId;
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
            Path path = new File(CACHE_DIR, audioId).toPath();
            try {
                Files.write(path, dataStream.toByteArray());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
