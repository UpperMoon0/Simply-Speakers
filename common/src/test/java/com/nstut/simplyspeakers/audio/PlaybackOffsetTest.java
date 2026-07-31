package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaybackOffsetTest {
    private static final float FRAME_RATE = 48_000.0f;
    private static final long TWO_SECOND_TRACK = 96_000L;

    @Test
    void wrapsLoopingOffsetAfterTrackHasEnded() {
        assertEquals(48_000L,
                PlaybackOffset.frameOffset(11.0f, true, TWO_SECOND_TRACK, FRAME_RATE));
    }

    @Test
    void wrapsExactLoopBoundaryToBeginning() {
        assertEquals(0L,
                PlaybackOffset.frameOffset(10.0f, true, TWO_SECOND_TRACK, FRAME_RATE));
    }

    @Test
    void preservesOffsetWhileStillInsideFirstLoop() {
        assertEquals(72_000L,
                PlaybackOffset.frameOffset(1.5f, true, TWO_SECOND_TRACK, FRAME_RATE));
    }

    @Test
    void doesNotWrapNonLoopingPlayback() {
        assertEquals(528_000L,
                PlaybackOffset.frameOffset(11.0f, false, TWO_SECOND_TRACK, FRAME_RATE));
    }

    @Test
    void preservesExistingBehaviorWhenStreamLengthIsUnknown() {
        assertEquals(528_000L,
                PlaybackOffset.frameOffset(11.0f, true, -1L, FRAME_RATE));
    }

    @Test
    void invalidInputsStartAtBeginning() {
        assertEquals(0L,
                PlaybackOffset.frameOffset(-1.0f, true, TWO_SECOND_TRACK, FRAME_RATE));
        assertEquals(0L,
                PlaybackOffset.frameOffset(Float.NaN, true, TWO_SECOND_TRACK, FRAME_RATE));
        assertEquals(0L,
                PlaybackOffset.frameOffset(1.0f, true, TWO_SECOND_TRACK, 0.0f));
    }
}
