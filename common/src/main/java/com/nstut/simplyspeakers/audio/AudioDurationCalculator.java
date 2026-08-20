package com.nstut.simplyspeakers.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Header;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;

public final class AudioDurationCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger("simplyspeakers");

    private AudioDurationCalculator() {
    }

    /**
     * Calculates duration in seconds for an MP3 or WAV file.
     * Returns 0.0f if duration cannot be determined.
     */
    public static float calculateDurationSeconds(File file) {
        if (file == null || !file.exists() || !file.canRead()) {
            return 0.0f;
        }

        String name = file.getName().toLowerCase();
        if (name.endsWith(".wav")) {
            return calculateWavDuration(file);
        } else if (name.endsWith(".mp3")) {
            return calculateMp3Duration(file);
        }
        return 0.0f;
    }

    public static float calculateDurationSeconds(Path path) {
        return path == null ? 0.0f : calculateDurationSeconds(path.toFile());
    }

    public static float calculateWavDuration(File file) {
        try {
            AudioFileFormat format = AudioSystem.getAudioFileFormat(file);
            AudioFormat audioFormat = format.getFormat();
            long frameLength = format.getFrameLength();
            float frameRate = audioFormat.getFrameRate();
            if (frameLength > 0 && frameRate > 0) {
                return (float) frameLength / frameRate;
            }
            // Fallback: file length based
            if (audioFormat.getFrameSize() > 0 && frameRate > 0) {
                long audioBytes = file.length() - 44; // approximate WAV header
                if (audioBytes > 0) {
                    return (float) audioBytes / (audioFormat.getFrameSize() * frameRate);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not determine WAV duration for {}: {}", file.getName(), e.getMessage());
        }
        return 0.0f;
    }

    public static float calculateMp3Duration(File file) {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            Bitstream bitstream = new Bitstream(in);
            Header firstHeader = bitstream.readFrame();
            if (firstHeader != null) {
                float totalMs = firstHeader.total_ms((int) file.length());
                bitstream.close();
                if (totalMs > 0) {
                    return totalMs / 1000.0f;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not determine MP3 duration for {}: {}", file.getName(), e.getMessage());
        }
        return 0.0f;
    }
}
