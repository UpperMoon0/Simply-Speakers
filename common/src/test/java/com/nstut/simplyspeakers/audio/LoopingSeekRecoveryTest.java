package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoopingSeekRecoveryTest {
    private static final float FRAME_RATE = 48_000.0f;
    private static final long TWO_SECOND_TRACK = 96_000L;

    @Test
    void discoversLengthWhenSeekRunsPastEof() {
        assertEquals(TWO_SECOND_TRACK, new LoopingSeekRecovery()
                .discoverFrameLength(true, -1L, 528_000L, TWO_SECOND_TRACK, true));
    }

    @Test
    void discoversLengthAtExactTrackBoundary() {
        assertEquals(TWO_SECOND_TRACK, new LoopingSeekRecovery()
                .discoverFrameLength(true, -1L, TWO_SECOND_TRACK, TWO_SECOND_TRACK, true));
    }

    @Test
    void treatsZeroLengthAsUnknown() {
        assertEquals(TWO_SECOND_TRACK, new LoopingSeekRecovery()
                .discoverFrameLength(true, 0L, 528_000L, TWO_SECOND_TRACK, true));
    }

    @Test
    void doesNotRetryEmptyStream() {
        assertEquals(-1L, new LoopingSeekRecovery()
                .discoverFrameLength(true, -1L, 528_000L, 0L, true));
    }

    @Test
    void doesNotRetryNonLoopingStream() {
        assertEquals(-1L, new LoopingSeekRecovery()
                .discoverFrameLength(false, -1L, 528_000L, TWO_SECOND_TRACK, true));
    }

    @Test
    void doesNotRetryStreamWithKnownLength() {
        assertEquals(-1L, new LoopingSeekRecovery()
                .discoverFrameLength(true, TWO_SECOND_TRACK, 528_000L, TWO_SECOND_TRACK, true));
    }

    @Test
    void doesNotDiscoverLengthWithoutConfirmedEof() {
        assertEquals(-1L, new LoopingSeekRecovery()
                .discoverFrameLength(true, -1L, 528_000L, TWO_SECOND_TRACK, false));
    }

    @Test
    void rejectsNonpositiveRequestedFrames() {
        LoopingSeekRecovery recovery = new LoopingSeekRecovery();
        assertEquals(-1L, recovery.discoverFrameLength(true, -1L, 0L, 0L, true));
        assertEquals(-1L, recovery.discoverFrameLength(true, -1L, -1L, 0L, true));
    }

    @Test
    void rejectsInvalidSkipProgress() {
        LoopingSeekRecovery recovery = new LoopingSeekRecovery();
        assertEquals(-1L, recovery.discoverFrameLength(true, -1L, 10L, -1L, true));
        assertEquals(-1L, recovery.discoverFrameLength(true, -1L, 10L, 11L, true));
    }

    @Test
    void allowsOnlyOneSuccessfulDetectionPerResource() {
        LoopingSeekRecovery recovery = new LoopingSeekRecovery();
        assertEquals(TWO_SECOND_TRACK,
                recovery.discoverFrameLength(true, -1L, 528_000L, TWO_SECOND_TRACK, true));
        assertEquals(-1L,
                recovery.discoverFrameLength(true, -1L, 528_000L, TWO_SECOND_TRACK, true));
        assertEquals(-1L,
                recovery.discoverFrameLength(true, 0L, TWO_SECOND_TRACK, TWO_SECOND_TRACK, true));
    }

    @Test
    void rejectedDetectionDoesNotConsumeRetry() {
        LoopingSeekRecovery recovery = new LoopingSeekRecovery();
        assertEquals(-1L,
                recovery.discoverFrameLength(true, -1L, 528_000L, TWO_SECOND_TRACK, false));
        assertEquals(TWO_SECOND_TRACK,
                recovery.discoverFrameLength(true, -1L, 528_000L, TWO_SECOND_TRACK, true));
    }

    @Test
    void retryBudgetIsIndependentForEachResource() {
        LoopingSeekRecovery first = new LoopingSeekRecovery();
        LoopingSeekRecovery second = new LoopingSeekRecovery();
        assertEquals(TWO_SECOND_TRACK,
                first.discoverFrameLength(true, -1L, 528_000L, TWO_SECOND_TRACK, true));
        assertEquals(TWO_SECOND_TRACK,
                second.discoverFrameLength(true, -1L, 528_000L, TWO_SECOND_TRACK, true));
    }

    @Test
    void discoveredTwoSecondLengthWrapsElevenSecondDowntimeToOneSecond() {
        long requestedFrames = PlaybackOffset.frameOffset(11.0f, true, -1L, FRAME_RATE);
        assertEquals(528_000L, requestedFrames);
        long discoveredLength = new LoopingSeekRecovery().discoverFrameLength(
                true, -1L, requestedFrames, TWO_SECOND_TRACK, true);

        assertEquals(48_000L,
                PlaybackOffset.frameOffset(11.0f, true, discoveredLength, FRAME_RATE));
    }

    @Test
    void discoveredExactBoundaryWrapsToBeginning() {
        long discoveredLength = new LoopingSeekRecovery().discoverFrameLength(
                true, -1L, TWO_SECOND_TRACK, TWO_SECOND_TRACK, true);

        assertEquals(0L,
                PlaybackOffset.frameOffset(2.0f, true, discoveredLength, FRAME_RATE));
    }
}
