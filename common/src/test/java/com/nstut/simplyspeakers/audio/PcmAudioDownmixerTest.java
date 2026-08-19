package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

import static org.junit.jupiter.api.Assertions.*;

public class PcmAudioDownmixerTest {

    @Test
    public void testDownmixShortArray() {
        short[] stereo = new short[]{ 1000, 3000, -2000, -4000, 0, 0, 100, -100 };
        short[] mono = PcmAudioDownmixer.downmixStereoToMono(stereo, stereo.length);

        assertEquals(4, mono.length);
        assertEquals(2000, mono[0]);
        assertEquals(-3000, mono[1]);
        assertEquals(0, mono[2]);
        assertEquals(0, mono[3]);
    }

    @Test
    public void testDownmixStereoBytesLE() {
        short left = 1000;
        short right = 3000;
        short expectedMono = 2000;

        byte[] stereoBytes = new byte[4];
        stereoBytes[0] = (byte) (left & 0xFF);
        stereoBytes[1] = (byte) ((left >> 8) & 0xFF);
        stereoBytes[2] = (byte) (right & 0xFF);
        stereoBytes[3] = (byte) ((right >> 8) & 0xFF);

        byte[] monoBytes = PcmAudioDownmixer.downmixStereoBytesToMonoBytes(stereoBytes, stereoBytes.length);

        assertEquals(2, monoBytes.length);
        short actualMono = (short) ((monoBytes[0] & 0xFF) | (monoBytes[1] << 8));
        assertEquals(expectedMono, actualMono);
    }

    @Test
    public void testEnsureMono16BitPcmStream() throws IOException {
        AudioFormat stereoFormat = new AudioFormat(44100.0f, 16, 2, true, false);
        byte[] stereoData = new byte[8]; // 2 frames
        // Frame 1: L=200, R=400 -> Mono=300
        stereoData[0] = (byte) (200 & 0xFF);
        stereoData[1] = 0;
        stereoData[2] = (byte) (400 & 0xFF);
        stereoData[3] = (byte) ((400 >> 8) & 0xFF);
        // Frame 2: L=-100, R=100 -> Mono=0
        stereoData[4] = (byte) (-100 & 0xFF);
        stereoData[5] = (byte) ((-100 >> 8) & 0xFF);
        stereoData[6] = 100;
        stereoData[7] = 0;

        AudioInputStream stereoStream = new AudioInputStream(
                new ByteArrayInputStream(stereoData), stereoFormat, 2);

        AudioInputStream monoStream = PcmAudioDownmixer.ensureMono16BitPcmStream(stereoStream);
        AudioFormat outFormat = monoStream.getFormat();

        assertEquals(1, outFormat.getChannels());
        assertEquals(16, outFormat.getSampleSizeInBits());
        assertEquals(44100.0f, outFormat.getSampleRate());

        byte[] readBuffer = new byte[4];
        int bytesRead = monoStream.read(readBuffer);
        assertEquals(4, bytesRead); // 2 mono frames = 4 bytes

        short frame1Mono = (short) ((readBuffer[0] & 0xFF) | (readBuffer[1] << 8));
        short frame2Mono = (short) ((readBuffer[2] & 0xFF) | (readBuffer[3] << 8));

        assertEquals(300, frame1Mono);
        assertEquals(0, frame2Mono);
    }
}
