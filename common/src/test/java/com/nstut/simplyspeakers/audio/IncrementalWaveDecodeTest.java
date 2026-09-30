package com.nstut.simplyspeakers.audio;

import com.nstut.simplyspeakers.testing.WaveFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class IncrementalWaveDecodeTest {
    @Test void decodeAndSeekUseRealWaveBytesWithoutAudioHardware(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("fixture.wav");
        byte[] fixture = WaveFixture.tone(2);
        Files.write(file, fixture);
        try (var stream = IncrementalAudioDecoders.openPcmStream(file.toFile())) {
            assertEquals(22050, stream.getFormat().getSampleRate());
            assertEquals(1, stream.getFormat().getChannels());
            assertEquals(44100, stream.getFrameLength());
            assertEquals(22050, stream.skip(22050));
            byte[] decoded = stream.readAllBytes();
            assertArrayEquals(java.util.Arrays.copyOfRange(fixture, 44 + 22050, fixture.length), decoded);
        }
    }
    @Test void truncatedNonAudioCannotBecomeAValidPcmStream(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("bad.wav"); Files.write(file, new byte[] {1, 2, 3});
        assertThrows(javax.sound.sampled.UnsupportedAudioFileException.class,
                () -> IncrementalAudioDecoders.openPcmStream(file.toFile()));
    }
}
