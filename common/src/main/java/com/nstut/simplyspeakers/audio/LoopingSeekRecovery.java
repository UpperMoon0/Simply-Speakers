package com.nstut.simplyspeakers.audio;

/**
 * Allows one retry when seeking a looping stream reveals its previously unknown
 * length. Keep one instance per playback resource.
 */
public final class LoopingSeekRecovery {
    private boolean retryGranted;

    /**
     * Discovers the whole-track frame length from a seek that started at the
     * beginning of the decoded stream and reached EOF. The caller can reopen the
     * stream and use {@link PlaybackOffset} to wrap its elapsed playback offset.
     *
     * @return the discovered frame length, or {@code -1} if no retry is allowed
     */
    public long discoverFrameLength(
            boolean looping,
            long knownFrameLength,
            long requestedFrames,
            long skippedFrames,
            boolean atEof) {
        if (retryGranted || !looping || knownFrameLength > 0L || !atEof
                || requestedFrames <= 0L || skippedFrames <= 0L
                || skippedFrames > requestedFrames) {
            return -1L;
        }

        retryGranted = true;
        return skippedFrames;
    }
}
