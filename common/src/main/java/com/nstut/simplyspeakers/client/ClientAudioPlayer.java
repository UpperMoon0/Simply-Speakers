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
import com.nstut.simplyspeakers.Config; // Added import
import net.minecraft.client.Minecraft;
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
// JLayer specific imports needed
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.DecoderException;

public class ClientAudioPlayer {

    // Use ConcurrentHashMap for thread safety when accessing from different threads
    // Value is now StreamingAudioResource which holds source, buffers, stream, thread etc.
    private static final Map<BlockPos, StreamingAudioResource> speakerResources = new ConcurrentHashMap<>();
    private static final int NUM_BUFFERS = 3; // Number of buffers to queue for streaming
    private static final int BUFFER_SIZE_SECONDS = 1; // Target buffer size in seconds (adjust as needed)

    // Updated resource class for streaming
    private static class StreamingAudioResource {
        final int sourceID;
        final int[] bufferIDs; // Array of buffer IDs
        // REMOVED: final AudioInputStream audioStream;
        final Thread streamingThread; // The thread handling buffer refills
        final AtomicBoolean stopFlag = new AtomicBoolean(false); // Flag to signal thread termination
        final BlockPos position;

        StreamingAudioResource(int sourceID, int[] bufferIDs, Thread streamingThread, BlockPos pos) { // AudioInputStream and AudioFormat removed
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
                // REMOVED: streamingThread.join(1000); This was causing the lag.
            }
            // The streaming thread is now responsible for closing its AudioInputStream.
            // Schedule OpenAL cleanup on the main thread.
            Minecraft.getInstance().tell(() -> {
                try {
                    if (AL10.alIsSource(sourceID)) { // Check if source exists
                        AL10.alSourceStop(sourceID);
                        // Unqueue all buffers
                        int buffersProcessed = AL10.alGetSourcei(sourceID, AL10.AL_BUFFERS_PROCESSED);
                        if (buffersProcessed > 0) {
                            int[] tempBuffers = new int[buffersProcessed];
                            AL10.alSourceUnqueueBuffers(sourceID, tempBuffers);
                        }
                        int buffersQueued = AL10.alGetSourcei(sourceID, AL10.AL_BUFFERS_QUEUED);
                        if (buffersQueued > 0) {
                            int[] tempBuffers = new int[buffersQueued];
                            AL10.alSourceUnqueueBuffers(sourceID, tempBuffers);
                        }
                        AL10.alSourcei(sourceID, AL10.AL_BUFFER, 0); // Detach buffer pointer

                        // Delete OpenAL resources
                        AL10.alDeleteSources(sourceID);
                        AL10.alDeleteBuffers(bufferIDs);
                        System.out.println("[SimplySpeakers] Async Cleaned up OpenAL source " + sourceID + " and buffers for speaker at " + position);
                    } else {
                        System.out.println("[SimplySpeakers] Async Cleanup: Source " + sourceID + " for speaker at " + position + " already deleted or invalid.");
                        // If source is gone, associated buffers might be too.
                        // Attempting to delete bufferIDs here could error if they are tied to a non-existent source
                        // or if the buffer IDs themselves are no longer valid.
                        // It's generally safer to let OpenAL handle buffer cleanup when a source is deleted.
                    }
                } catch (Exception e) {
                     System.err.println("[SimplySpeakers] Error during async OpenAL cleanup for source " + sourceID + " at " + position + ": " + e.getMessage());
                }
            });
        }
    }

    // Updated play method signature
    public static void play(BlockPos pos, String filePath, float startPositionSeconds) {
        // Check if audio is already playing/managed for this position
        stop(pos);

        if (filePath == null || filePath.trim().isEmpty()) {
            System.err.println("[SimplySpeakers] Audio path is empty for speaker at " + pos + ". Skipping playback.");
            return;
        }

        System.out.println("[SimplySpeakers] Attempting to play audio at " + pos + " from " + startPositionSeconds + "s, path: " + filePath);

        Minecraft.getInstance().tell(() -> {
            try {
                int sourceID = AL10.alGenSources();
                int[] bufferIDs = new int[NUM_BUFFERS];
                AL10.alGenBuffers(bufferIDs); // Generate multiple buffers

                AL10.alSource3f(sourceID, AL10.AL_POSITION, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f);
                AL10.alSourcef(sourceID, AL10.AL_ROLLOFF_FACTOR, 0.0f);
                AL10.alSourcef(sourceID, AL10.AL_GAIN, 1.0f);
                AL10.alSourcei(sourceID, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);

                Thread streamingThread = new Thread(() -> streamAudioData(pos, sourceID, bufferIDs, filePath, startPositionSeconds),
                                                    "SimplySpeakers Streaming Thread - " + pos);
                streamingThread.setDaemon(true);

                StreamingAudioResource resource = new StreamingAudioResource(sourceID, bufferIDs, streamingThread, pos);
                speakerResources.put(pos, resource);
                System.out.println("[SimplySpeakers] Stored streaming resource for pos: " + pos + ". Thread will open/decode audio.");

                streamingThread.start();

            } catch (Exception e) {
                System.err.println("[SimplySpeakers] ERROR: Exception during OpenAL setup on main thread for: " + filePath + " at " + pos);
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
    private static void streamAudioData(BlockPos pos, int sourceID, int[] bufferIDs, String filePath, float startPositionSeconds) {
        StreamingAudioResource resource = speakerResources.get(pos);

        if (resource == null || resource.sourceID != sourceID) {
            System.err.println("[SimplySpeakers] Streaming thread for " + pos + " (source " + sourceID + ") found resource mismatch or missing. Aborting.");
            return;
        }

        AudioInputStream pcmAudioStream = null;
        boolean initialDataLoaded = false; // Declare here for visibility in finally

        try {
            File audioFile = new File(filePath);
            if (!audioFile.exists()) {
                System.err.println("[SimplySpeakers] Streaming thread ERROR: Audio file not found: " + filePath + " for " + pos);
                resource.stopFlag.set(true);
                return;
            }

            System.out.println("[SimplySpeakers] Streaming thread for " + pos + ": Attempting to get PCM stream for " + filePath);
            // Ensure getPcmAudioStream is accessible (it should be if it's a static method in this class)
            pcmAudioStream = getPcmAudioStream(audioFile);
            
            if (pcmAudioStream == null) {
                System.err.println("[SimplySpeakers] Streaming thread ERROR: Could not get PCM audio stream for: " + filePath + " for " + pos);
                resource.stopFlag.set(true);
                return;
            }
            System.out.println("[SimplySpeakers] Streaming thread for " + pos + ": Successfully got PCM stream.");

            AudioFormat format = pcmAudioStream.getFormat();
            if (startPositionSeconds > 0) {
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
                        return;
                    }
                    System.out.println("[SimplySpeakers] Streaming thread for " + pos + ": Successfully skipped " + skipped + " bytes.");
                }
            }

            boolean playbackAttempted = false;
            // initialDataLoaded is already declared above

            // Ensure getOpenALFormat is accessible
            int alFormat = getOpenALFormat(format);
            if (alFormat == -1) {
                System.err.println("[SimplySpeakers] Streaming thread ERROR for " + pos + ": Unsupported audio format for OpenAL: " + format);
                resource.stopFlag.set(true);
                return;
            }
            int bufferSizeBytes = (int) (format.getFrameRate() * format.getFrameSize() * BUFFER_SIZE_SECONDS);
            byte[] bufferData = new byte[bufferSizeBytes];

            for (int i = 0; i < NUM_BUFFERS; i++) {
                if (resource.stopFlag.get() || Thread.currentThread().isInterrupted()) {
                    System.out.println("[SimplySpeakers] Streaming thread for " + pos + ": Stop signal or interrupt during initial buffering.");
                    break;
                }

                int bytesRead = pcmAudioStream.read(bufferData, 0, bufferData.length);
                if (bytesRead <= 0) {
                    System.out.println("[SimplySpeakers] Streaming thread for " + pos + ": EOF or read error during initial buffering. Bytes read: " + bytesRead);
                    resource.stopFlag.set(true);
                    break; 
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

            if (!playbackAttempted) {
                System.out.println("[SimplySpeakers] Streaming thread for " + pos + ": Playback not attempted. Not entering main streaming loop.");
                if (!resource.stopFlag.get() && !initialDataLoaded) { 
                    resource.stopFlag.set(true);
                }
            }

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
                    } else {
                        System.out.println("[SimplySpeakers] EOF or read error in streaming thread for " + pos + ". Buffer " + bufferID + " not re-queued. Bytes read: " + bytesRead);
                        resource.stopFlag.set(true);
                        break; 
                    }
                }
                 if (resource.stopFlag.get()) break;

                if (AL10.alGetSourcei(sourceID, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING && initialDataLoaded) {
                     int queuedBuffers = AL10.alGetSourcei(sourceID, AL10.AL_BUFFERS_QUEUED);
                     if (queuedBuffers > 0) {
                        System.out.println("[SimplySpeakers] Source " + sourceID + " at " + pos + " stopped but has queued buffers. Restarting playback.");
                        AL10.alSourcePlay(sourceID);
                     } else if (!resource.stopFlag.get()) { 
                        System.out.println("[SimplySpeakers] Buffer underrun for source " + sourceID + " at " + pos + ". Waiting for more data.");
                     }
                }

                try {
                    Thread.sleep(50); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); 
                    System.out.println("[SimplySpeakers] Streaming thread for " + pos + " interrupted during sleep.");
                    resource.stopFlag.set(true);
                    break; 
                }
            }
        } catch (UnsupportedAudioFileException e) {
            System.err.println("[SimplySpeakers] Streaming thread ERROR for " + pos + ": Unsupported audio file format for " + filePath + ". " + e.getMessage());
            if (resource != null) resource.stopFlag.set(true);
        } catch (IOException e) {
            System.err.println("[SimplySpeakers] Streaming thread IO ERROR for " + pos + " with file " + filePath + ": " + e.getMessage());
            if (resource != null) resource.stopFlag.set(true);
        } catch (Exception e) {
            System.err.println("[SimplySpeakers] Critical error in streaming thread for " + pos + " (source " + sourceID + "): " + e.getMessage());
            e.printStackTrace();
            if (resource != null) resource.stopFlag.set(true);
        } finally {
            System.out.println("[SimplySpeakers] Streaming thread for " + pos + " (source " + sourceID + ") is exiting. Performing final cleanup.");
            if (pcmAudioStream != null) {
                try {
                    pcmAudioStream.close();
                    System.out.println("[SimplySpeakers] AudioInputStream closed by streaming thread for " + pos);
                } catch (IOException e) {
                    System.err.println("[SimplySpeakers] Error closing audioStream in streaming thread's finally block for " + pos + ": " + e.getMessage());
                }
            }
            // Ensure resource is marked as stopped if thread exits due to an early error before playback could properly start.
            if (resource != null && !resource.stopFlag.get() && !initialDataLoaded) { 
                System.out.println("[SimplySpeakers] Streaming thread for " + pos + " setting stopFlag in finally due to no data loaded.");
                resource.stopFlag.set(true);
            }
        }
        System.out.println("[SimplySpeakers] Streaming thread finished for " + pos + " (source " + sourceID + ").");
    }

    // Updated stop method
    public static void stop(BlockPos pos) {
        StreamingAudioResource resource = speakerResources.remove(pos);
        if (resource != null) {
            resource.stopAndCleanup();
            System.out.println("[SimplySpeakers] Stopped audio for speaker at " + pos);
        }
    }

    // Added back stopAll method
    public static void stopAll() {
         System.out.println("[SimplySpeakers] Stopping all playback...");
         // Create a copy of keys to avoid ConcurrentModificationException
         List<BlockPos> positionsToStop = new ArrayList<>(speakerResources.keySet());
         System.out.println("[SimplySpeakers] Found " + positionsToStop.size() + " active speakers to stop.");
         for (BlockPos pos : positionsToStop) {
             stop(pos); // Call the updated stop method for each
         }
         System.out.println("[SimplySpeakers] Finished stopping all playback.");
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
}
