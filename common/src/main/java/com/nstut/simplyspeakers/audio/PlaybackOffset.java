package com.nstut.simplyspeakers.audio;

/**
 * Calculates a decoded audio stream offset for synchronized playback.
 */
public final class PlaybackOffset {
    private PlaybackOffset() {
    }

    /**
     * Converts elapsed playback time to a frame offset. Looping tracks wrap the
     * elapsed position into the current loop instead of seeking past EOF.
     *
     * @param elapsedSeconds total time elapsed since playback started
     * @param looping whether playback is looping
     * @param frameLength decoded length of the stream in frames
     * @param frameRate decoded stream frame rate
     * @return number of frames to skip from the beginning of the stream
     */
    public static long frameOffset(
            float elapsedSeconds,
            boolean looping,
            long frameLength,
            float frameRate) {
        if (elapsedSeconds <= 0.0f || !Float.isFinite(elapsedSeconds)
                || frameRate <= 0.0f || !Float.isFinite(frameRate)) {
            return 0L;
        }

        long elapsedFrames = (long) (elapsedSeconds * frameRate);
        if (looping && frameLength > 0L) {
            return elapsedFrames % frameLength;
        }
        return elapsedFrames;
    }
}
