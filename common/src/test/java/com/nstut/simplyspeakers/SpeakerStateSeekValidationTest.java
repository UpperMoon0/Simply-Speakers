package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for seek hardening: non-finite values must never reach
 * pauseOffsetSeconds, even when a caller skips service-layer validation.
 */
class SpeakerStateSeekValidationTest {

    @Test
    void seekToWithNaNClampsToZero() {
        SpeakerState state = new SpeakerState();
        state.startPlaybackAt(100, 5.0f);
        state.seekTo(Float.NaN, 200, 60.0f);
        assertEquals(0.0f, state.getPauseOffsetSeconds());
    }

    @Test
    void seekToWithPositiveInfinityClampsToZero() {
        SpeakerState state = new SpeakerState();
        state.startPlaybackAt(100, 5.0f);
        state.seekTo(Float.POSITIVE_INFINITY, 200, 60.0f);
        assertEquals(0.0f, state.getPauseOffsetSeconds());
    }

    @Test
    void seekToWithNegativeInfinityClampsToZero() {
        SpeakerState state = new SpeakerState();
        state.startPlaybackAt(100, 5.0f);
        state.seekTo(Float.NEGATIVE_INFINITY, 200, 60.0f);
        assertEquals(0.0f, state.getPauseOffsetSeconds());
    }

    @Test
    void seekToWithFiniteValueStillSeeks() {
        SpeakerState state = new SpeakerState();
        state.startPlaybackAt(100, 0.0f);
        state.seekTo(12.5f, 200, 60.0f);
        assertEquals(12.5f, state.getPauseOffsetSeconds());
        assertTrue(state.isPlaying());
    }

    @Test
    void seekToWithoutKnownDurationKeepsFiniteTarget() {
        SpeakerState state = new SpeakerState();
        state.startPlaybackAt(100, 0.0f);
        state.seekTo(9999.0f, 200, 0.0f);
        assertEquals(9999.0f, state.getPauseOffsetSeconds());
    }

    @Test
    void seekToWithNaNAndNoDurationClampsToZero() {
        // Previously: Math.max(0, NaN) == NaN poisoned pauseOffsetSeconds.
        SpeakerState state = new SpeakerState();
        state.startPlaybackAt(100, 0.0f);
        state.seekTo(Float.NaN, 200, 0.0f);
        assertEquals(0.0f, state.getPauseOffsetSeconds());
    }
}
