package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackTimelineTest {
    @Test
    void includesPacketOffsetAndLoadAndReloadDelay() {
        AtomicLong clock = new AtomicLong(10_000_000_000L);
        PlaybackTimeline timeline = new PlaybackTimeline(12.5f, clock::get);

        assertEquals(12.5f, timeline.offsetSeconds());
        clock.addAndGet(2_000_000_000L);
        assertEquals(14.5f, timeline.offsetSeconds());
        clock.addAndGet(3_250_000_000L);
        assertEquals(17.75f, timeline.offsetSeconds());
    }

    @Test
    void repeatedQueriesDoNotResetOrAccumulateTheAnchor() {
        AtomicLong clock = new AtomicLong();
        PlaybackTimeline timeline = new PlaybackTimeline(4.0f, clock::get);

        clock.set(1_250_000_000L);
        assertEquals(5.25f, timeline.offsetSeconds());
        assertEquals(5.25f, timeline.offsetSeconds());
        clock.set(2_500_000_000L);
        assertEquals(6.5f, timeline.offsetSeconds());
    }

    @Test
    void restartingLoopDropsEarlierLoopAgeAndAdvancesFromNewAnchor() {
        AtomicLong clock = new AtomicLong();
        PlaybackTimeline timeline = new PlaybackTimeline(20.0f, clock::get);

        clock.set(30_000_000_000L);
        assertEquals(50.0f, timeline.offsetSeconds());
        timeline.restartLoop();
        assertEquals(0.0f, timeline.offsetSeconds());
        clock.addAndGet(1_500_000_000L);
        assertEquals(1.5f, timeline.offsetSeconds());

        timeline.restartLoop();
        clock.addAndGet(250_000_000L);
        assertEquals(0.25f, timeline.offsetSeconds());
    }

    @Test
    void invalidPacketOffsetsStartAtZeroAndStillAdvance() {
        for (float offset : new float[] {-5.0f, Float.NaN,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            AtomicLong clock = new AtomicLong();
            PlaybackTimeline timeline = new PlaybackTimeline(offset, clock::get);

            assertEquals(0.0f, timeline.offsetSeconds());
            clock.set(500_000_000L);
            assertEquals(0.5f, timeline.offsetSeconds());
        }
    }

    @Test
    void veryLongElapsedTimeRemainsFiniteAndNonnegative() {
        AtomicLong clock = new AtomicLong();
        PlaybackTimeline timeline = new PlaybackTimeline(1.0f, clock::get);

        clock.set(Long.MAX_VALUE);
        float offset = timeline.offsetSeconds();
        assertTrue(Float.isFinite(offset));
        assertEquals((float) (1.0 + Long.MAX_VALUE / 1_000_000_000.0), offset);
    }

    @Test
    void maximumFinitePacketOffsetRemainsFiniteAfterElapsedTime() {
        AtomicLong clock = new AtomicLong();
        PlaybackTimeline timeline = new PlaybackTimeline(Float.MAX_VALUE, clock::get);

        clock.set(Long.MAX_VALUE);
        assertEquals(Float.MAX_VALUE, timeline.offsetSeconds());
        assertTrue(Float.isFinite(timeline.offsetSeconds()));
    }

    @Test
    void negativeElapsedTimeCannotProduceANegativeOffset() {
        AtomicLong clock = new AtomicLong(5_000_000_000L);
        PlaybackTimeline timeline = new PlaybackTimeline(2.0f, clock::get);

        clock.set(4_000_000_000L);
        assertEquals(2.0f, timeline.offsetSeconds());
        timeline.restartLoop();
        clock.set(3_000_000_000L);
        assertEquals(0.0f, timeline.offsetSeconds());
    }

    @Test
    void monotonicClockMayWrapItsSignedLongRepresentation() {
        AtomicLong clock = new AtomicLong(Long.MAX_VALUE - 500_000_000L);
        PlaybackTimeline timeline = new PlaybackTimeline(2.0f, clock::get);

        clock.addAndGet(1_000_000_000L);
        assertEquals(3.0f, timeline.offsetSeconds());
    }
}
