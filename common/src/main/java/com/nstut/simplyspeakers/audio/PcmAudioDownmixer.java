package com.nstut.simplyspeakers.audio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

/**
 * Utility for converting and downmixing multi-channel (e.g. stereo) PCM audio
 * to mono 16-bit signed PCM format required for OpenAL 3D spatialization.
 */
public final class PcmAudioDownmixer {

    private PcmAudioDownmixer() {}

    /**
     * Downmixes interleaved stereo 16-bit signed PCM samples to mono 16-bit signed PCM samples.
     *
     * @param interleavedStereo array containing interleaved L, R, L, R shorts
     * @param sampleCount       total number of shorts to process (must be even)
     * @return a new short array containing mono samples with length = sampleCount / 2
     */
    public static short[] downmixStereoToMono(short[] interleavedStereo, int sampleCount) {
        if (interleavedStereo == null || sampleCount <= 0) {
            return new short[0];
        }
        int frameCount = sampleCount / 2;
        short[] mono = new short[frameCount];
        for (int i = 0; i < frameCount; i++) {
            int left = interleavedStereo[i * 2];
            int right = interleavedStereo[i * 2 + 1];
            mono[i] = (short) ((left + right) / 2);
        }
        return mono;
    }

    /**
     * Converts an array of 16-bit shorts to little-endian bytes.
     *
     * @param shorts array of shorts
     * @param count  number of shorts to convert
     * @return byte array of length count * 2
     */
    public static byte[] shortsToBytesLE(short[] shorts, int count) {
        if (shorts == null || count <= 0) {
            return new byte[0];
        }
        byte[] bytes = new byte[count * 2];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts, 0, count);
        return bytes;
    }

    /**
     * Downmixes interleaved stereo 16-bit little-endian PCM bytes directly to mono 16-bit little-endian PCM bytes.
     *
     * @param stereoBytes raw stereo byte buffer (4 bytes per frame)
     * @param length      number of bytes to process (should be a multiple of 4)
     * @return byte array of length (length / 2) containing mono 16-bit little-endian PCM
     */
    public static byte[] downmixStereoBytesToMonoBytes(byte[] stereoBytes, int length) {
        if (stereoBytes == null || length <= 0) {
            return new byte[0];
        }
        int validLength = length - (length % 4);
        int monoLength = validLength / 2;
        byte[] monoBytes = new byte[monoLength];

        int out = 0;
        for (int i = 0; i < validLength; i += 4) {
            short left = (short) ((stereoBytes[i] & 0xFF) | (stereoBytes[i + 1] << 8));
            short right = (short) ((stereoBytes[i + 2] & 0xFF) | (stereoBytes[i + 3] << 8));
            short mono = (short) ((left + right) / 2);

            monoBytes[out++] = (byte) (mono & 0xFF);
            monoBytes[out++] = (byte) ((mono >> 8) & 0xFF);
        }
        return monoBytes;
    }

    /**
     * Converts any input AudioInputStream into a 16-bit signed, little-endian, mono AudioInputStream.
     *
     * @param sourceStream the source audio input stream
     * @return a mono AudioInputStream with sample rate preserved
     * @throws IOException if reading the stream fails
     */
    public static AudioInputStream ensureMono16BitPcmStream(AudioInputStream sourceStream) throws IOException {
        AudioFormat srcFormat = sourceStream.getFormat();
        float sampleRate = srcFormat.getSampleRate();
        int channels = srcFormat.getChannels();

        if (channels == 1 && srcFormat.getSampleSizeInBits() == 16
                && srcFormat.getEncoding() == AudioFormat.Encoding.PCM_SIGNED
                && !srcFormat.isBigEndian()) {
            return sourceStream;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = sourceStream.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        byte[] rawBytes = out.toByteArray();

        byte[] monoBytes;
        if (channels == 2 && srcFormat.getSampleSizeInBits() == 16) {
            monoBytes = downmixStereoBytesToMonoBytes(rawBytes, rawBytes.length);
        } else if (channels == 1 && srcFormat.getSampleSizeInBits() == 16) {
            monoBytes = rawBytes;
        } else {
            // Default passthrough or single-channel truncation
            monoBytes = rawBytes;
        }

        AudioFormat monoFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                16,
                1,
                2,
                sampleRate,
                false
        );

        return new AudioInputStream(
                new ByteArrayInputStream(monoBytes),
                monoFormat,
                monoBytes.length / monoFormat.getFrameSize()
        );
    }
}
