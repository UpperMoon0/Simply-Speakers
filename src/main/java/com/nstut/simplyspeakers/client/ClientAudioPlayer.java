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
import com.nstut.simplyspeakers.Config; // Added import
import net.minecraft.client.Minecraft; // Import Minecraft
import java.util.List; // Import List
import java.util.ArrayList; // Import ArrayList if not using Java 10+ List.copyOf

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
import javazoom.jl.decoder.Obuffer; // Base class for SampleBuffer

public class ClientAudioPlayer {

    // Use ConcurrentHashMap for thread safety when accessing from different threads
    private static final Map<BlockPos, AudioResource> speakerResources = new ConcurrentHashMap<>();

    private static class AudioResource {
        int sourceID;
        int bufferID;

        AudioResource(int sourceID, int bufferID) {
            this.sourceID = sourceID;
            this.bufferID = bufferID;
        }
    }

    public static void play(BlockPos pos, String filePath) {
        // --- Modification Start ---
        // Check if audio is already playing/managed for this position
        if (speakerResources.containsKey(pos)) {
            // Audio resource already exists. OpenAL is handling playback/attenuation.
            // We don't need to do anything here, especially not restart it.
            // System.out.println("[SimplySpeakers] Audio resource already exists for " + pos + ". Letting OpenAL manage.");
            return; // Exit early, playback is already handled
        }
        // --- Modification End ---

        // If we reach here, no existing resource was found, so proceed to load and play.

        // Stop any potentially orphaned playback at this position first (Safety check, might be redundant now)
        // stop(pos); // Removed the unconditional stop

        if (filePath == null || filePath.trim().isEmpty()) {
            System.err.println("Audio path is empty for speaker at " + pos + ". Skipping playback.");
            return; // Keep only the first return
        }

        System.out.println("[SimplySpeakers] Attempting to play audio at " + pos + " with path: " + filePath); // Log path

        Thread audioThread = new Thread(() -> {
            System.out.println("[SimplySpeakers] Audio thread started for: " + filePath);
            File audioFile = new File(filePath);
            if (!audioFile.exists()) {
                System.err.println("[SimplySpeakers] ERROR: Audio file not found: " + filePath); // Log file not found
                return;
            }
            System.out.println("[SimplySpeakers] Audio file exists: " + filePath); // Log file found

            try {
                AudioInputStream pcmInputStream; // Renamed for clarity
                AudioFormat pcmFormat;          // Renamed for clarity

                // Check file extension to decide how to get the initial stream
                if (filePath.toLowerCase().endsWith(".mp3")) {
                    System.out.println("[SimplySpeakers] Detected MP3 file. Decoding directly using JLayer...");
                    // Bitstream is not AutoCloseable, handle FileInputStream in try-with-resources
                    try (InputStream fileStream = new FileInputStream(audioFile);
                         ByteArrayOutputStream pcmOutputStream = new ByteArrayOutputStream()) {

                        Bitstream bitstream = new Bitstream(fileStream); // Create Bitstream here
                        Decoder decoder = new Decoder();
                        Header frame;
                        int frameCount = 0;
                        float sampleRate = -1;
                        int channels = -1;
                        SampleBuffer outputBuffer = null; // Buffer to hold decoded samples

                        System.out.println("[SimplySpeakers] Starting JLayer frame decoding loop...");
                        // Decode frame by frame
                        while (true) {
                            try {
                                frame = bitstream.readFrame(); // Read the next frame header
                                if (frame == null) {
                                    System.out.println("[SimplySpeakers] End of MP3 stream reached.");
                                    break; // End of stream
                                }

                                if (frameCount == 0) {
                                    // Get format info from the first frame
                                    sampleRate = frame.frequency();
                                    channels = (frame.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
                                    // Corrected log message, no modeExtension()
                                    System.out.println("[SimplySpeakers] MP3 Header Info: Sample Rate=" + sampleRate + ", Channels=" + channels + ", Mode=" + frame.mode());
                                    // Create the output buffer based on channel count, cast sampleRate to int
                                    outputBuffer = new SampleBuffer((int)sampleRate, channels);
                                    decoder.setOutputBuffer(outputBuffer); // Tell decoder where to write samples
                                }

                                // Decode the frame - samples are written into outputBuffer
                                // Corrected decodeFrame call - does not take bitstream argument
                                Obuffer decodedBuffer = decoder.decodeFrame(frame, bitstream);
                                // Check if the decoded buffer matches our output buffer (it should)
                                if (decodedBuffer != outputBuffer) {
                                     System.err.println("[SimplySpeakers] WARNING: Decoder returned a different buffer instance!");
                                     // This case might require handling decodedBuffer directly if it happens
                                }


                                // Write the decoded samples (short[]) to the byte stream (converting to byte[])
                                short[] pcmShorts = outputBuffer.getBuffer();
                                int sampleCount = outputBuffer.getBufferLength();
                                byte[] pcmBytes = shortsToBytesLE(pcmShorts, sampleCount); // Convert short[] to byte[] (Little Endian)
                                pcmOutputStream.write(pcmBytes);

                                bitstream.closeFrame(); // Important to close the frame in the bitstream
                                frameCount++;

                            } catch (BitstreamException e) {
                                 // Specific handling for Bitstream errors (e.g., end of stream, invalid data)
                                 System.err.println("[SimplySpeakers] BitstreamException during JLayer frame decoding: " + e.getMessage());
                                 if (e.getErrorCode() == BitstreamException.INVALIDFRAME) {
                                     System.err.println("[SimplySpeakers] Invalid MP3 frame encountered.");
                                     // Optionally continue to try and skip the bad frame
                                 }
                                 break; // Stop decoding on most bitstream errors
                            } catch (DecoderException e) {
                                 System.err.println("[SimplySpeakers] DecoderException during JLayer frame decoding: " + e.getMessage());
                                 break; // Stop decoding on decoder errors
                            }
                        }
                        System.out.println("[SimplySpeakers] Finished JLayer decoding loop. Decoded " + frameCount + " frames.");


                        if (sampleRate <= 0 || channels <= 0 || frameCount == 0) {
                             System.err.println("[SimplySpeakers] ERROR: Could not determine MP3 format after decoding.");
                             return; // Exit thread if format is invalid
                        }

                        // Define the PCM format based on decoded MP3 info
                        pcmFormat = new AudioFormat(sampleRate, 16, channels, true, false); // 16-bit, signed, little-endian
                        System.out.println("[SimplySpeakers] JLayer decoded MP3 to PCM format: " + pcmFormat);

                        // Create an AudioInputStream from the decoded PCM byte array
                        byte[] pcmData = pcmOutputStream.toByteArray();
                        ByteArrayInputStream pcmByteStream = new ByteArrayInputStream(pcmData);
                        pcmInputStream = new AudioInputStream(pcmByteStream, pcmFormat, pcmData.length / pcmFormat.getFrameSize());
                        System.out.println("[SimplySpeakers] Created AudioInputStream from JLayer decoded PCM data.");

                    } catch (Exception e) {
                        System.err.println("[SimplySpeakers] ERROR: Failed to decode MP3 using JLayer for: " + filePath);
                        e.printStackTrace();
                        return; // Exit thread on decoding failure
                    }
                } else {
                    // Standard handling for WAV, OGG (if supported by SPI), etc.
                    System.out.println("[SimplySpeakers] Detected non-MP3 file. Getting stream via AudioSystem...");
                    AudioInputStream initialStream = AudioSystem.getAudioInputStream(audioFile);
                    System.out.println("[SimplySpeakers] Got initial non-MP3 stream. Base format: " + initialStream.getFormat());
                    pcmFormat = initialStream.getFormat(); // Use the format directly if it's already PCM-like
                    // If the base format isn't PCM, attempt to decode it
                    if (pcmFormat.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
                         System.out.println("[SimplySpeakers] Non-MP3 base format is not PCM. Attempting decode to PCM...");
                         pcmFormat = new AudioFormat(
                             AudioFormat.Encoding.PCM_SIGNED,
                             pcmFormat.getSampleRate(),
                             16, // Target 16-bit
                             pcmFormat.getChannels(),
                             pcmFormat.getChannels() * 2, // Frame size
                             pcmFormat.getSampleRate(),
                             false // Little-endian
                         );
                         pcmInputStream = AudioSystem.getAudioInputStream(pcmFormat, initialStream);
                         System.out.println("[SimplySpeakers] Decoded non-MP3 stream to PCM format: " + pcmFormat);
                    } else {
                         System.out.println("[SimplySpeakers] Non-MP3 base format is already PCM.");
                         pcmInputStream = initialStream; // Use the stream directly
                    }
                }

                // --- Now process the pcmInputStream (which is guaranteed to be PCM) ---
                AudioFormat finalFormat = pcmInputStream.getFormat(); // This is the format we'll use for OpenAL

                // Define the target PCM format we want OpenAL to use (should match finalFormat ideally)
                // This decoding step might be redundant now if pcmInputStream is already correct,
                // but it ensures consistency (e.g., forces 16-bit if original was 8-bit).
                 AudioFormat openAlTargetFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED, // Target encoding: PCM signed
                        finalFormat.getSampleRate(),
                        16, // Force 16-bit for OpenAL
                        finalFormat.getChannels(),
                        finalFormat.getChannels() * 2, // Frame size for 16-bit
                        finalFormat.getSampleRate(),
                        false // Little-endian
                );
                 System.out.println("[SimplySpeakers] Final target format for OpenAL: " + openAlTargetFormat);

                // Use try-with-resources for the final decoded stream
                try (AudioInputStream finalDecodedStream = AudioSystem.getAudioInputStream(openAlTargetFormat, pcmInputStream)) {
                     System.out.println("[SimplySpeakers] Successfully obtained final decoded stream for OpenAL.");
                     System.out.println("[SimplySpeakers] Reading all bytes from final decoded stream...");
                    byte[] data = finalDecodedStream.readAllBytes();
                     System.out.println("[SimplySpeakers] Read " + data.length + " bytes for OpenAL buffer."); // Log bytes read
                    ByteBuffer buffer = ByteBuffer.allocateDirect(data.length);
                    buffer.order(ByteOrder.nativeOrder()); // Use native byte order
                    buffer.put(data);
                    buffer.flip(); // Prepare buffer for reading by OpenAL

                    int alFormat = getOpenALFormat(openAlTargetFormat); // Use the final target format
                     System.out.println("[SimplySpeakers] Determined OpenAL format ID: " + alFormat);
                    // Removed bufferID, sourceID, alBufferData, and speakerResources.put from here

                    // --- OpenAL operations MUST run on the main client thread ---
                     System.out.println("[SimplySpeakers] Scheduling OpenAL operations on main thread...");
                    Minecraft.getInstance().tell(() -> {
                         System.out.println("[SimplySpeakers] Executing OpenAL operations on main thread for: " + filePath);
                        // Buffer creation and data upload
                        int bufferID = AL10.alGenBuffers();
                        // Use the correct format variable here
                        AL10.alBufferData(bufferID, alFormat, buffer, (int) openAlTargetFormat.getSampleRate());
                         System.out.println("[SimplySpeakers] OpenAL Buffer created (ID: " + bufferID + ") and data uploaded.");

                        // Source creation
                        int sourceID = AL10.alGenSources();
                         System.out.println("[SimplySpeakers] OpenAL Source created (ID: " + sourceID + ").");

                        // Store the resource immediately after creation on the client thread
                        speakerResources.put(pos, new AudioResource(sourceID, bufferID));
                         System.out.println("[SimplySpeakers] Stored audio resource for pos: " + pos);

                        // Set source position for spatial audio (center of the block)
                        AL10.alSource3f(sourceID, AL10.AL_POSITION, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f);
                        // Set source properties for attenuation
                        AL10.alSourcef(sourceID, AL10.AL_REFERENCE_DISTANCE, 4.0f); // Full volume within 4 blocks
                        AL10.alSourcef(sourceID, AL10.AL_ROLLOFF_FACTOR, 1.0f); // Standard rolloff
                        AL10.alSourcef(sourceID, AL10.AL_MAX_DISTANCE, Config.SPEAKER_RANGE.get()); // Max distance from config
                        // Ensure the source is not relative to the listener
                        AL10.alSourcei(sourceID, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);

                        AL10.alSourcei(sourceID, AL10.AL_BUFFER, bufferID);
                        AL10.alSourcePlay(sourceID);

                        // Optional: Add logic to clean up buffer/source when playback finishes
                        // This might involve checking source state periodically or using AL events if available/reliable.
                         System.out.println("[SimplySpeakers] Initiating playback for source ID: " + sourceID);
                    });
                     // --- End of main thread execution block ---
                     System.out.println("[SimplySpeakers] Successfully scheduled OpenAL operations.");

                } catch (IOException e) {
                     System.err.println("[SimplySpeakers] ERROR: IOException while reading decoded audio stream for: " + filePath + ". Details: " + e.getMessage()); // Add details
                     e.printStackTrace(); // Keep stack trace for full debug
                } // End Decoded AudioInputStream try-with-resources
            } catch (UnsupportedAudioFileException e) {
                 System.err.println("[SimplySpeakers] ERROR: Unsupported audio file format for: " + filePath + ". Java Sound may not support this format directly (e.g., MP3). Try converting to WAV. Details: " + e.getMessage()); // Add details and suggestion
                 e.printStackTrace(); // Keep stack trace
            } catch (IOException e) {
                 System.err.println("[SimplySpeakers] ERROR: IOException while getting base audio stream for: " + filePath + ". Check file permissions and path validity. Details: " + e.getMessage()); // Add details
                 e.printStackTrace(); // Keep stack trace
            } catch (Exception e) {
                 System.err.println("[SimplySpeakers] ERROR: Unexpected error processing audio file: " + filePath + ". Details: " + e.getMessage()); // Add details
                 e.printStackTrace(); // Keep stack trace
            }
        }); // End Thread lambda
        audioThread.setDaemon(true); // Ensure thread doesn't prevent JVM shutdown
        audioThread.setName("SimplySpeakers Audio Thread - " + pos); // Give the thread a descriptive name
        audioThread.start();
    }

    public static void stop(BlockPos pos) {
        // Schedule the stop operation on the main client thread as well
        Minecraft.getInstance().tell(() -> {
            AudioResource resource = speakerResources.remove(pos);
            if (resource != null) {
                try { // Add try-catch for robustness
                    AL10.alSourceStop(resource.sourceID);
                    AL10.alSourcei(resource.sourceID, AL10.AL_BUFFER, 0); // Detach buffer
                    AL10.alDeleteSources(resource.sourceID);
                    AL10.alDeleteBuffers(resource.bufferID);
                    System.out.println("[SimplySpeakers] Stopped and cleaned resource for pos: " + pos);
                } catch (Exception e) {
                    System.err.println("[SimplySpeakers] Error stopping resource for pos " + pos + ": " + e.getMessage());
                }
            }
        });
    }

    public static void stopAll() {
        // Schedule the stop operation on the main client thread
         Minecraft.getInstance().tell(() -> {
            System.out.println("[SimplySpeakers] Executing stopAll on main thread.");
            // Create a copy of the values to iterate over. Use ArrayList if not on Java 10+
            // List<AudioResource> resourcesToClean = List.copyOf(speakerResources.values());
            List<AudioResource> resourcesToClean = new ArrayList<>(speakerResources.values()); // Use ArrayList for broader compatibility

            speakerResources.clear(); // Clear the map immediately on the main thread

            System.out.println("[SimplySpeakers] Cleaning up " + resourcesToClean.size() + " audio resources.");
            for (AudioResource resource : resourcesToClean) {
                try {
                    AL10.alSourceStop(resource.sourceID);
                    AL10.alSourcei(resource.sourceID, AL10.AL_BUFFER, 0); // Detach buffer
                    AL10.alDeleteSources(resource.sourceID);
                    AL10.alDeleteBuffers(resource.bufferID);
                    System.out.println("[SimplySpeakers] Cleaned up Source: " + resource.sourceID + ", Buffer: " + resource.bufferID);
                } catch (Exception e) {
                    // Log potential errors during cleanup, but continue with others.
                    System.err.println("[SimplySpeakers] Error cleaning up OpenAL resource (Source: " + resource.sourceID + ", Buffer: " + resource.bufferID + "): " + e.getMessage());
                    // e.printStackTrace(); // Uncomment for deeper debugging if needed
                }
            }
            System.out.println("[SimplySpeakers] Finished cleaning audio resources.");
         });
         System.out.println("[SimplySpeakers] Scheduled stopAll task.");
    }

    // Helper method to convert short array (PCM) to byte array (Little Endian)
    private static byte[] shortsToBytesLE(short[] shorts, int count) {
        byte[] bytes = new byte[count * 2];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts, 0, count);
        return bytes;
    }


    private static int getOpenALFormat(AudioFormat format) {
        int channels = format.getChannels();
        // Ensure we are dealing with 16-bit samples as expected by AL_FORMAT_MONO16/STEREO16
        if (format.getSampleSizeInBits() != 16) {
             throw new IllegalArgumentException("Unsupported sample size for OpenAL: " + format.getSampleSizeInBits() + " (expected 16)");
        }
        if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
             throw new IllegalArgumentException("Unsupported encoding for OpenAL: " + format.getEncoding() + " (expected PCM_SIGNED)");
        }

        if (channels == 1) {
            return AL10.AL_FORMAT_MONO16;
        } else if (channels == 2) {
            return AL10.AL_FORMAT_STEREO16;
        } else {
            throw new IllegalArgumentException("Unsupported number of channels: " + channels);
        }
    }
}
