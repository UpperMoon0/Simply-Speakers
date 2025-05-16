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
import javazoom.jl.decoder.Obuffer;

public class ClientAudioPlayer {

    // Use ConcurrentHashMap for thread safety when accessing from different threads
    // Value is now StreamingAudioResource which holds source, buffers, stream, thread etc.
    private static final Map<BlockPos, StreamingAudioResource> speakerResources = new ConcurrentHashMap<>();
    private static final int NUM_BUFFERS = 3; // Number of buffers to queue for streaming
    private static final int BUFFER_SIZE_SECONDS = 1; // Target buffer size in seconds (adjust as needed)

    // Cache for decoded PCM data and their formats
    private static final Map<String, byte[]> pcmCache = new ConcurrentHashMap<>();
    private static final Map<String, AudioFormat> formatCache = new ConcurrentHashMap<>();

    // Updated resource class for streaming
    private static class StreamingAudioResource {
        final int sourceID;
        final int[] bufferIDs; // Array of buffer IDs
        final AudioInputStream audioStream; // The stream being read from
        final Thread streamingThread; // The thread handling buffer refills
        final AtomicBoolean stopFlag = new AtomicBoolean(false); // Flag to signal thread termination
        StreamingAudioResource(int sourceID, int[] bufferIDs, AudioInputStream audioStream, Thread streamingThread, AudioFormat format) {
            this.sourceID = sourceID;
            this.bufferIDs = bufferIDs;
            this.audioStream = audioStream;
            this.streamingThread = streamingThread;
        }

        // Method to signal the streaming thread to stop and clean up resources
        void stopAndCleanup() {
            stopFlag.set(true); // Signal the thread to stop
            if (streamingThread != null && streamingThread.isAlive()) {
                try {
                    streamingThread.interrupt(); // Interrupt if sleeping/waiting
                    streamingThread.join(1000); // Wait briefly for thread to finish
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("[SimplySpeakers] Interrupted while waiting for streaming thread to stop for source " + sourceID);
                }
            }
            // Close the audio stream
            try {
                if (audioStream != null) {
                    audioStream.close();
                }
            } catch (IOException e) {
                System.err.println("[SimplySpeakers] Error closing audio stream for source " + sourceID + ": " + e.getMessage());
            }

            // Schedule OpenAL cleanup on the main thread
            Minecraft.getInstance().tell(() -> {
                try {
                    AL10.alSourceStop(sourceID);
                    // Detach any buffers still attached to the source
                    int buffersQueued = AL10.alGetSourcei(sourceID, AL10.AL_BUFFERS_QUEUED); // Corrected: alGetSourcei
                    if (buffersQueued > 0) {
                        int[] detachedBuffers = new int[buffersQueued];
                        AL10.alSourceUnqueueBuffers(sourceID, detachedBuffers);
                    }
                    AL10.alSourcei(sourceID, AL10.AL_BUFFER, 0); // Ensure no buffer is attached

                    // Delete source and buffers
                    AL10.alDeleteSources(sourceID);
                    AL10.alDeleteBuffers(bufferIDs); // Delete all buffers in the array
                    System.out.println("[SimplySpeakers] Cleaned up OpenAL source " + sourceID + " and buffers.");
                } catch (Exception e) {
                     System.err.println("[SimplySpeakers] Error during OpenAL cleanup for source " + sourceID + ": " + e.getMessage());
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

        // --- Start of New Streaming Logic ---
        // This part runs initially to set up the stream and start the process.
        // The actual reading and buffer queuing will happen in a separate thread.

        try {
            File audioFile = new File(filePath);
            if (!audioFile.exists()) {
                System.err.println("[SimplySpeakers] ERROR: Audio file not found: " + filePath);
                return;
            }

            // --- Get AudioInputStream (Handles decoding WAV/OGG/MP3 to PCM) ---
            AudioInputStream pcmAudioStream = getPcmAudioStream(audioFile);
            if (pcmAudioStream == null) {
                System.err.println("[SimplySpeakers] ERROR: Could not get PCM audio stream for: " + filePath);
                return;
            }

            // --- Calculate Seek Position ---
            AudioFormat format = pcmAudioStream.getFormat();
            long bytesToSkip = 0;
            if (startPositionSeconds > 0) {
                float frameRate = format.getFrameRate();
                int frameSize = format.getFrameSize();
                if (frameRate > 0 && frameSize > 0) {
                    long framesToSkip = (long) (startPositionSeconds * frameRate);
                    bytesToSkip = framesToSkip * frameSize;
                    System.out.println("[SimplySpeakers] Calculated skip: " + bytesToSkip + " bytes for " + startPositionSeconds + "s");
                } else {
                    System.err.println("[SimplySpeakers] WARNING: Invalid format for seeking: " + format);
                }
            }

            // --- Perform Seek ---
            if (bytesToSkip > 0) {
                try {
                    long skipped = skipFully(pcmAudioStream, bytesToSkip);
                    if (skipped < bytesToSkip) {
                        System.err.println("[SimplySpeakers] WARNING: Could only skip " + skipped + "/" + bytesToSkip + " bytes. Reached EOF?");
                        // If we couldn't skip enough, likely near or past EOF, so don't play.
                        pcmAudioStream.close(); // Close the stream
                        return;
                    }
                    System.out.println("[SimplySpeakers] Successfully skipped " + skipped + " bytes.");
                } catch (IOException e) {
                    System.err.println("[SimplySpeakers] ERROR: IOException during skipBytes for: " + filePath + ". " + e.getMessage());
                    try { pcmAudioStream.close(); } catch (IOException ignored) {}
                    return;
                }
            }

            // --- Setup OpenAL Source and Buffers (on main thread) ---
            final AudioInputStream finalStream = pcmAudioStream; // Effectively final for lambda
            final AudioFormat finalFormat = format; // Effectively final for lambda
            Minecraft.getInstance().tell(() -> {
                try {
                    int sourceID = AL10.alGenSources();
                    int[] bufferIDs = new int[NUM_BUFFERS];
                    AL10.alGenBuffers(bufferIDs); // Generate multiple buffers

                    // Configure source properties (position, attenuation, etc.)
                    AL10.alSource3f(sourceID, AL10.AL_POSITION, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f);
                    // Disable OpenAL's distance attenuation - we'll handle gain manually
                    AL10.alSourcef(sourceID, AL10.AL_ROLLOFF_FACTOR, 0.0f);
                    // Set initial gain to full volume
                    AL10.alSourcef(sourceID, AL10.AL_GAIN, 1.0f);
                    AL10.alSourcei(sourceID, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE); // Position is absolute

                    // --- Create and Start Streaming Thread ---
                    // Placeholder for the thread object
                    Thread streamingThread = new Thread(() -> streamAudioData(pos, sourceID, bufferIDs, finalStream, finalFormat),
                                                        "SimplySpeakers Streaming Thread - " + pos);
                    streamingThread.setDaemon(true);

                    // Store the resource (including the stream and thread)
                    StreamingAudioResource resource = new StreamingAudioResource(sourceID, bufferIDs, finalStream, streamingThread, finalFormat);
                    speakerResources.put(pos, resource);
                    System.out.println("[SimplySpeakers] Stored streaming resource for pos: " + pos);

                    // Start the streaming thread AFTER storing the resource
                    streamingThread.start();

                    // Initial buffer fill and playback start will be handled by the streaming thread itself.
                    // We don't call alSourcePlay here directly anymore.

                } catch (Exception e) {
                    System.err.println("[SimplySpeakers] ERROR: Exception during OpenAL setup on main thread for: " + filePath);
                    e.printStackTrace();
                    // Clean up the stream if OpenAL setup failed
                    try { finalStream.close(); } catch (IOException ignored) {}
                }
            });

        } catch (UnsupportedAudioFileException e) {
            System.err.println("[SimplySpeakers] ERROR: Unsupported audio file format for: " + filePath + ". " + e.getMessage());
        } catch (IOException e) {
            System.err.println("[SimplySpeakers] ERROR: IOException getting/processing audio file: " + filePath + ". " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[SimplySpeakers] ERROR: Unexpected error setting up playback for: " + filePath + ". " + e.getMessage());
            e.printStackTrace();
        }
        // --- End of New Streaming Logic ---
    }

     // Helper method to ensure all requested bytes are skipped
    private static long skipFully(InputStream in, long n) throws IOException {
        long remaining = n;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) { // skip returns 0 on EOF or if n is non-positive
                break; // Reached EOF or cannot skip further
            }
            remaining -= skipped;
        }
        return n - remaining; // Return total bytes actually skipped
    }


    // Core streaming logic executed in a separate thread
    private static void streamAudioData(BlockPos pos, int sourceID, int[] bufferIDs, AudioInputStream stream, AudioFormat format) {
        System.out.println("[Streaming Thread " + sourceID + "] Started for " + pos);
        StreamingAudioResource resource = speakerResources.get(pos); // Get resource again to access stopFlag

        // Check resource validity immediately after starting
        if (resource == null || resource.stopFlag.get()) {
            System.out.println("[Streaming Thread " + sourceID + "] Resource invalid or stop requested immediately. Exiting.");
            try { if (stream != null) stream.close(); } catch (IOException ignored) {}
            return;
        }

        boolean streamEnded = false;
        try {
            // Calculate buffer size in bytes
            int bytesPerSecond = (int) (format.getSampleRate() * format.getFrameSize());
            int bufferSizeBytes = bytesPerSecond * BUFFER_SIZE_SECONDS;
            byte[] readBuffer = new byte[bufferSizeBytes];
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSizeBytes).order(ByteOrder.nativeOrder());
            int alFormat = getOpenALFormat(format);

            // --- Initial Buffer Fill ---
            int initialBuffersFilled = 0;
            for (int bufferID : bufferIDs) {
                if (resource.stopFlag.get()) break; // Check stop flag during initial fill
                int bytesRead = stream.read(readBuffer);
                if (bytesRead > 0) {
                    byteBuffer.clear();
                    byteBuffer.put(readBuffer, 0, bytesRead);
                    byteBuffer.flip();
                    // Upload data on the streaming thread (OpenAL context might not be current, schedule?)
                    // For now, assume context is okay or handle potential issues.
                    // Scheduling every buffer upload might be too slow.
                    AL10.alBufferData(bufferID, alFormat, byteBuffer, (int) format.getSampleRate());
                    AL10.alSourceQueueBuffers(sourceID, bufferID);
                    initialBuffersFilled++;
                } else {
                    streamEnded = true; // Reached end of stream during initial fill
                    break;
                }
            }
            System.out.println("[Streaming Thread " + sourceID + "] Initial buffers filled: " + initialBuffersFilled);

            if (initialBuffersFilled == 0 || resource.stopFlag.get()) {
                 System.out.println("[Streaming Thread " + sourceID + "] No initial buffers filled or stop requested. Exiting.");
                 throw new IOException("Failed to fill initial buffers or stop requested."); // Trigger cleanup in finally
            }

            // --- Start Playback ---
            AL10.alSourcePlay(sourceID);
            System.out.println("[Streaming Thread " + sourceID + "] Playback started.");

            // --- Streaming Loop ---
            while (!streamEnded && !resource.stopFlag.get()) {
                int processed = AL10.alGetSourcei(sourceID, AL10.AL_BUFFERS_PROCESSED);

                while (processed-- > 0 && !resource.stopFlag.get()) {
                    int bufferID = AL10.alSourceUnqueueBuffers(sourceID);
                    // Check for OpenAL errors after unqueueing
                    int alError = AL10.alGetError();
                    if (alError != AL10.AL_NO_ERROR) {
                         System.err.println("[Streaming Thread " + sourceID + "] OpenAL error after unqueue: " + alError);
                         // Potentially break or handle error
                    }


                    int bytesRead = stream.read(readBuffer);
                    if (bytesRead > 0) {
                        byteBuffer.clear();
                        byteBuffer.put(readBuffer, 0, bytesRead);
                        byteBuffer.flip();
                        AL10.alBufferData(bufferID, alFormat, byteBuffer, (int) format.getSampleRate());
                        AL10.alSourceQueueBuffers(sourceID, bufferID);
                    } else {
                        streamEnded = true; // Reached end of stream
                        System.out.println("[Streaming Thread " + sourceID + "] End of audio stream reached.");
                        break; // Exit inner loop if stream ended
                    }
                }

                // Check source state and restart if necessary (handles buffer underrun)
                if (!resource.stopFlag.get() && AL10.alGetSourcei(sourceID, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                     // Check if there are still buffers queued or if we expect more data
                     int queuedBuffers = AL10.alGetSourcei(sourceID, AL10.AL_BUFFERS_QUEUED);
                     if (queuedBuffers > 0 || !streamEnded) {
                          System.out.println("[Streaming Thread " + sourceID + "] Source stopped unexpectedly, restarting playback.");
                          AL10.alSourcePlay(sourceID);
                     } else {
                          // Source stopped and no more buffers/data, let the loop terminate naturally
                          System.out.println("[Streaming Thread " + sourceID + "] Source stopped, no more buffers or data.");
                          break; // Exit outer loop
                     }
                }


                // Avoid busy-waiting if no buffers were processed
                if (!streamEnded && !resource.stopFlag.get()) {
                    try {
                        Thread.sleep(50); // Sleep briefly (e.g., 50ms)
                    } catch (InterruptedException e) {
                        System.out.println("[Streaming Thread " + sourceID + "] Interrupted, likely stopping.");
                        resource.stopFlag.set(true); // Ensure stop flag is set if interrupted
                        Thread.currentThread().interrupt(); // Re-interrupt thread
                    }
                }
            } // End while loop

        } catch (IOException e) {
            System.err.println("[Streaming Thread " + sourceID + "] IOException during streaming: " + e.getMessage());
            // e.printStackTrace(); // Uncomment for debugging
        } catch (Exception e) {
             System.err.println("[Streaming Thread " + sourceID + "] Unexpected exception during streaming: " + e.getMessage());
             e.printStackTrace();
        } finally {
            System.out.println("[Streaming Thread " + sourceID + "] Exiting loop/try block.");
            // Ensure cleanup happens if the thread exits, even on error
            // Check if the resource still exists and hasn't been cleaned up by stop() already
            StreamingAudioResource currentResource = speakerResources.get(pos);
            if (currentResource != null && currentResource.sourceID == sourceID && !currentResource.stopFlag.get()) {
                 System.out.println("[Streaming Thread " + sourceID + "] Initiating cleanup from finally block.");
                 // Use remove here to prevent stop() from trying to clean up again
                 StreamingAudioResource res = speakerResources.remove(pos);
                 if (res != null) {
                     res.stopAndCleanup(); // Call cleanup if we removed it successfully
                 }
            } else {
                 System.out.println("[Streaming Thread " + sourceID + "] Cleanup likely handled by stop() or resource removed.");
            }
        }
    }


    // Updated stop method
    public static void stop(BlockPos pos) {
        StreamingAudioResource resource = speakerResources.remove(pos);
        if (resource != null) {
            System.out.println("[SimplySpeakers] Stopping playback for pos: " + pos + " (Source ID: " + resource.sourceID + ")");
            resource.stopAndCleanup(); // Use the resource's cleanup method
        }
    }

    // Updated stopAll method
    public static void stopAll() {
         System.out.println("[SimplySpeakers] Stopping all playback...");
         // Create a copy of keys to avoid ConcurrentModificationException
         List<BlockPos> positionsToStop = new ArrayList<>(speakerResources.keySet());
         System.out.println("[SimplySpeakers] Found " + positionsToStop.size() + " active speakers to stop.");
         for (BlockPos pos : positionsToStop) {
             stop(pos); // Call the updated stop method for each
         }
         System.out.println("[SimplySpeakers] Finished stopping all playback.");
         // No need to schedule on main thread here, as stop() already does.
    }

    // --- Client Tick Update for Volume ---
    public static void updateSpeakerVolumes() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || speakerResources.isEmpty()) {
            return;
        }

        Vec3 playerPos = player.position();
        double maxRange = Config.speakerRange;
        double maxRangeSq = maxRange * maxRange;

        // Iterate safely over a copy of the entry set in case of concurrent modification
        List<Map.Entry<BlockPos, StreamingAudioResource>> entries = new ArrayList<>(speakerResources.entrySet());

        for (Map.Entry<BlockPos, StreamingAudioResource> entry : entries) {
            BlockPos speakerPos = entry.getKey();
            StreamingAudioResource resource = entry.getValue();

            // Check if resource is still valid (might have been stopped concurrently)
            if (resource == null || resource.stopFlag.get()) {
                continue;
            }

            Vec3 speakerCenterPos = new Vec3(speakerPos.getX() + 0.5, speakerPos.getY() + 0.5, speakerPos.getZ() + 0.5);
            double distSq = playerPos.distanceToSqr(speakerCenterPos);

            float gain = 1.0f; // Default to full volume

            if (distSq >= maxRangeSq) {
                gain = 0.0f; // Beyond max range, should be silent (server should stop it anyway)
            } else if (distSq > 0) { // Avoid division by zero and calculate fade
                double distance = Math.sqrt(distSq);
                // Linear fade: gain = 1.0 - (distance / maxRange)
                // Squared fade (faster drop-off): gain = (1.0 - (distance / maxRange))^2
                gain = (float) Math.pow(1.0 - (distance / maxRange), 2.0);
                gain = Math.max(0.0f, Math.min(1.0f, gain)); // Clamp between 0.0 and 1.0
            }

            // Schedule the OpenAL call on the main thread
            final float finalGain = gain; // Need effectively final variable for lambda
            mc.tell(() -> {
                // Double-check resource validity before executing AL call
                 StreamingAudioResource currentResource = speakerResources.get(speakerPos);
                 if (currentResource != null && currentResource.sourceID == resource.sourceID && !currentResource.stopFlag.get()) {
                    try {
                        AL10.alSourcef(resource.sourceID, AL10.AL_GAIN, finalGain);
                        // Check for AL errors after setting gain
                        int error = AL10.alGetError();
                        if (error != AL10.AL_NO_ERROR) {
                            System.err.println("[SimplySpeakers] OpenAL error setting gain for source " + resource.sourceID + ": " + error);
                        }
                    } catch (Exception e) {
                        // Catch potential errors if the source was deleted unexpectedly
                        System.err.println("[SimplySpeakers] Error setting gain for source " + resource.sourceID + ": " + e.getMessage());
                    }
                 }
            });
        }
    }

    // --- Audio Decoding Logic (Moved to helper method) ---
    private static AudioInputStream getPcmAudioStream(File audioFile) throws UnsupportedAudioFileException, IOException {
        String filePath = audioFile.getPath();
        AudioInputStream pcmInputStream;
        AudioFormat pcmFormat;

        if (filePath.toLowerCase().endsWith(".mp3")) {
            System.out.println("[SimplySpeakers] Decoding MP3: " + filePath);
            try (InputStream fileStream = new FileInputStream(audioFile);
                 ByteArrayOutputStream pcmOutputStream = new ByteArrayOutputStream()) {

                Bitstream bitstream = new Bitstream(fileStream);
                Decoder decoder = new Decoder();
                Header frame;
                int frameCount = 0;
                float sampleRate = -1;
                int channels = -1;
                SampleBuffer outputBuffer = null;

                while (true) {
                    try {
                        frame = bitstream.readFrame();
                        if (frame == null) break;
                        if (frameCount == 0) {
                            sampleRate = frame.frequency();
                            channels = (frame.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
                            outputBuffer = new SampleBuffer((int)sampleRate, channels);
                            decoder.setOutputBuffer(outputBuffer);
                        }
                        Obuffer decodedBuffer = decoder.decodeFrame(frame, bitstream);
                        if (decodedBuffer != outputBuffer) { /* Handle error or unexpected buffer */ }
                        short[] pcmShorts = outputBuffer.getBuffer();
                        int sampleCount = outputBuffer.getBufferLength();
                        byte[] pcmBytes = shortsToBytesLE(pcmShorts, sampleCount);
                        pcmOutputStream.write(pcmBytes);
                        bitstream.closeFrame();
                        frameCount++;
                    } catch (BitstreamException | DecoderException e) {
                        System.err.println("[SimplySpeakers] JLayer decoding error: " + e.getMessage());
                        break; // Stop on error
                    }
                }

                if (sampleRate <= 0 || channels <= 0 || frameCount == 0) {
                    throw new UnsupportedAudioFileException("Could not determine MP3 format for: " + filePath);
                }
                pcmFormat = new AudioFormat(sampleRate, 16, channels, true, false); // LE
                byte[] pcmData = pcmOutputStream.toByteArray();
                ByteArrayInputStream pcmByteStream = new ByteArrayInputStream(pcmData);
                pcmInputStream = new AudioInputStream(pcmByteStream, pcmFormat, pcmData.length / pcmFormat.getFrameSize());
                System.out.println("[SimplySpeakers] MP3 decoded to PCM format: " + pcmFormat);
                return pcmInputStream;

            } catch (Exception e) { // Catch broader exceptions during MP3 decoding
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
                16,
                initialFormat.getChannels(),
                initialFormat.getChannels() * 2,
                initialFormat.getSampleRate(),
                false // Little-endian
            );

            // Check if conversion is needed
            if (!initialFormat.matches(targetPcmFormat)) {
                 System.out.println("[SimplySpeakers] Converting to target PCM format: " + targetPcmFormat);
                 if (AudioSystem.isConversionSupported(targetPcmFormat, initialFormat)) {
                     pcmInputStream = AudioSystem.getAudioInputStream(targetPcmFormat, initialStream);
                     System.out.println("[SimplySpeakers] Conversion successful.");
                     // Important: Don't close initialStream here, AudioSystem handles it
                     return pcmInputStream;
                 } else {
                     initialStream.close(); // Close the initial stream if conversion fails
                     throw new UnsupportedAudioFileException("PCM conversion not supported from " + initialFormat + " to " + targetPcmFormat);
                 }
            } else {
                 System.out.println("[SimplySpeakers] Initial format is already the target PCM format.");
                 return initialStream; // Return the original stream as it's already correct
            }
        }
    }


    // Helper method to convert short array (PCM) to byte array (Little Endian)
    private static byte[] shortsToBytesLE(short[] shorts, int count) {
        byte[] bytes = new byte[count * 2];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts, 0, count);
        return bytes;
    }


    private static int getOpenALFormat(AudioFormat format) {
        int channels = format.getChannels();
        int bitDepth = format.getSampleSizeInBits(); // Get bit depth

        // Check encoding first
        if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
             throw new IllegalArgumentException("Unsupported encoding for OpenAL: " + format.getEncoding() + " (expected PCM_SIGNED)");
        }

        // Determine format based on channels and bit depth
        if (channels == 1) {
            if (bitDepth == 8) {
                return AL10.AL_FORMAT_MONO8;
            } else if (bitDepth == 16) {
                return AL10.AL_FORMAT_MONO16;
            }
        } else if (channels == 2) {
            if (bitDepth == 8) {
                return AL10.AL_FORMAT_STEREO8;
            } else if (bitDepth == 16) {
                return AL10.AL_FORMAT_STEREO16;
            }
        }

        // If we reach here, the format is unsupported
        throw new IllegalArgumentException("Unsupported OpenAL format: Channels=" + channels + ", BitDepth=" + bitDepth);
    }
}
