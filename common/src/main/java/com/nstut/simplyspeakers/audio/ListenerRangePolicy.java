package com.nstut.simplyspeakers.audio;

/**
 * Pure policy for determining whether a listener is within range of an audio emitter,
 * accounting for entry exactness and exit hysteresis.
 */
public final class ListenerRangePolicy {

    private ListenerRangePolicy() {
    }

    /**
     * Determines whether a listener should hear an emitter based on squared distance,
     * base effective range, and current listening membership.
     *
     * @param distanceSquared   squared distance between listener and emitter
     * @param effectiveRange    base maximum range of the emitter
     * @param currentlyListening true if the player is already recorded as an active listener
     * @return true if the listener is within audible range (including exit margin if already listening)
     */
    public static boolean shouldListen(double distanceSquared, double effectiveRange, boolean currentlyListening) {
        if (effectiveRange <= 0.0) {
            return false;
        }
        double threshold = currentlyListening
                ? effectiveRange + SpatialAudioCalculator.LISTENER_EXIT_HYSTERESIS
                : effectiveRange;
        return distanceSquared <= threshold * threshold;
    }
}
