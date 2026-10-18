package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class StereoDecodeTest {
    @Test void localAndUrlWavePreserveDistinctChannelsAndFrameAlignedSeek(@TempDir Path directory) throws Exception {
        byte[] pcm = { 0, 4, 0, 12, 0, 8, 0, 16 };
        File file = directory.resolve("stereo.wav").toFile();
        try (var source = new AudioInputStream(new ByteArrayInputStream(pcm), new AudioFormat(8000, 16, 2, true, false), 2)) {
            AudioSystem.write(source, AudioFileFormat.Type.WAVE, file);
        }
        try (var stereo = IncrementalAudioDecoders.openPcmStream(file, true)) {
            assertEquals(2, stereo.getFormat().getChannels());
            assertEquals(4, stereo.getFormat().getFrameSize());
            assertArrayEquals(pcm, stereo.readAllBytes());
        }
        try (var stereo = IncrementalAudioDecoders.openPcmStream(file, true)) {
            assertEquals(4, stereo.skip(4));
            assertArrayEquals(new byte[]{0,8,0,16}, stereo.readAllBytes());
        }
        try (var mono = IncrementalAudioDecoders.openPcmStream(file)) {
            assertEquals(1, mono.getFormat().getChannels());
            assertArrayEquals(new byte[]{0,8,0,12}, mono.readAllBytes());
        }
        try (var stereo = IncrementalAudioDecoders.openPcmStreamFromUrl(new FileInputStream(file), "https://example.invalid/audio.wav", true)) {
            assertEquals(2, stereo.getFormat().getChannels());
            assertArrayEquals(pcm, stereo.readAllBytes());
        }
    }

    @Test void mp3LocalAndUrlKeepBothChannelsWhileDefaultDownmixes(@TempDir Path directory) throws Exception {
        File file = directory.resolve("asymmetric.mp3").toFile();
        try (var fixture = getClass().getResourceAsStream("/audio/asymmetric.mp3")) {
            assertNotNull(fixture);
            java.nio.file.Files.copy(fixture, file.toPath());
        }
        byte[] stereo;
        try (var stream = IncrementalAudioDecoders.openPcmStream(file, true)) {
            assertEquals(2, stream.getFormat().getChannels());
            stereo = stream.readAllBytes();
        }
        boolean different = false;
        for (int i = 0; i + 3 < stereo.length; i += 4) {
            if (stereo[i] != stereo[i+2] || stereo[i+1] != stereo[i+3]) different = true;
        }
        assertTrue(different, "fixture must retain its asymmetric left/right signals");
        try (var url = IncrementalAudioDecoders.openPcmStreamFromUrl(new FileInputStream(file), "https://example.invalid/audio.mp3", true)) {
            assertArrayEquals(stereo, url.readAllBytes());
        }
        try (var stream = IncrementalAudioDecoders.openPcmStream(file)) {
            assertEquals(1, stream.getFormat().getChannels());
            byte[] mono = stream.readAllBytes();
            assertEquals(stereo.length / 2, mono.length);
            for (int i=0; i<mono.length; i+=2) {
                short left = (short)((stereo[i*2]&255) | stereo[i*2+1]<<8);
                short right = (short)((stereo[i*2+2]&255) | stereo[i*2+3]<<8);
                short sample = (short)((mono[i]&255) | mono[i+1]<<8);
                assertEquals((left+right)/2, sample);
            }
        }
    }

    @Test void monoInputWorksInEitherMode(@TempDir Path directory) throws Exception {
        File file = directory.resolve("mono.wav").toFile();
        byte[] pcm = {0,4,0,8};
        try (var source = new AudioInputStream(new ByteArrayInputStream(pcm), new AudioFormat(8000,16,1,true,false),2)) {
            AudioSystem.write(source, AudioFileFormat.Type.WAVE,file);
        }
        for (boolean mode : new boolean[]{false,true}) {
            try (var stream = IncrementalAudioDecoders.openPcmStream(file,mode)) {
                assertEquals(1,stream.getFormat().getChannels());
                assertArrayEquals(pcm,stream.readAllBytes());
            }
        }
    }
}
