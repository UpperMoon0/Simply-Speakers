package com.nstut.simplyspeakers.audio;

import com.nstut.simplyspeakers.testing.WaveFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** The native deadline must accommodate receipt pacing without replacing real audio. */
class LiveTransferWaveFixtureTest {
    @Test void boundedFixtureRetainsRealDecodedAudioAndLongSeekTimeline(@TempDir Path directory) throws Exception {
        byte[] fixture = WaveFixture.liveTransferTone();
        assertEquals(WaveFixture.LIVE_TRANSFER_BYTES, fixture.length);
        int windows = (fixture.length + 4095) / 4096;
        assertTrue(windows > 1, "live transfer must exercise multiple authenticated receipts");
        assertTrue(windows * 100L < 15_000, "leave at least half the 30s deadline for scheduling and decode");
        Path file = directory.resolve("paced.wav"); Files.write(file, fixture);
        try (var stream = IncrementalAudioDecoders.openPcmStream(file.toFile())) {
            assertEquals(1, stream.getFormat().getChannels());
            assertEquals(16, stream.getFormat().getSampleSizeInBits());
            assertEquals(120, stream.getFrameLength() / stream.getFormat().getFrameRate(), .01);
            long seekBytes = (long)(4 * stream.getFormat().getFrameRate() * stream.getFormat().getFrameSize());
            assertEquals(seekBytes, stream.skip(seekBytes));
            byte[] decoded = new byte[4096]; assertEquals(decoded.length, stream.read(decoded));
            boolean nonzero = false; for (byte value : decoded) nonzero |= value != 0;
            assertTrue(nonzero, "native decoded/OpenAL acceptance must use actual audio samples");
        }
    }
}
