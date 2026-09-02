package com.nstut.simplyspeakers;

/** Pure evaluation of redstone signal changes into speaker actions. */
public final class RedstoneLogic {

    public enum Action {
        NONE, PLAY, STOP, RESTART, TOGGLE_PAUSE, NEXT_TRACK, PREVIOUS_TRACK,
        SET_VOLUME, SELECT_TRACK
    }

    public record RedstoneResult(Action action, int payload) {
        public static final RedstoneResult NONE = new RedstoneResult(Action.NONE, 0);

        public boolean hasAction() {
            return action != Action.NONE;
        }
    }

    private RedstoneLogic() {
    }

    /**
     * Evaluates a signal change for the given mode.
     *
     * @param mode       configured behaviour
     * @param previous   previous signal strength (0-15)
     * @param current    current signal strength (0-15)
     * @param trackCount number of playlist tracks, for ANALOG_TRACK clamping
     */
    public static RedstoneResult evaluate(RedstoneMode mode, int previous, int current, int trackCount) {
        if (mode == null) return RedstoneResult.NONE;
        boolean wasPowered = previous > 0;
        boolean isPowered = current > 0;
        boolean risingEdge = isPowered && !wasPowered;
        boolean fallingEdge = !isPowered && wasPowered;

        switch (mode) {
            case POWER: {
                if (!risingEdge && !fallingEdge && isPowered) {
                    return RedstoneResult.NONE;
                }
                if (risingEdge) return new RedstoneResult(Action.PLAY, 0);
                if (fallingEdge) return new RedstoneResult(Action.STOP, 0);
                return RedstoneResult.NONE;
            }
            case PULSE:
                return risingEdge ? new RedstoneResult(Action.RESTART, 0) : RedstoneResult.NONE;
            case TOGGLE:
                return risingEdge ? new RedstoneResult(Action.TOGGLE_PAUSE, 0) : RedstoneResult.NONE;
            case NEXT:
                return risingEdge ? new RedstoneResult(Action.NEXT_TRACK, 0) : RedstoneResult.NONE;
            case ANALOG_VOLUME: {
                if (previous == current) return RedstoneResult.NONE;
                return new RedstoneResult(Action.SET_VOLUME, clamp(current));
            }
            case ANALOG_TRACK: {
                if (!risingEdge || trackCount <= 0) return RedstoneResult.NONE;
                int slot = Math.max(1, Math.min(RedstoneMode.MAX_ANALOG_SLOTS, current)) - 1;
                int index = Math.min(slot, trackCount - 1);
                return new RedstoneResult(Action.SELECT_TRACK, index);
            }
            default:
                return RedstoneResult.NONE;
        }
    }

    /**
     * Comparator output exposing continuously observable playback progress:
     * 0 stopped/paused, 1..15 proportional position. Unknown-duration streams
     * report a neutral mid-level of 7 while playing.
     */
    public static int comparatorLevel(boolean playing, float positionSeconds, float durationSeconds) {
        if (!playing) return 0;
        if (Float.isFinite(durationSeconds) && durationSeconds > 0.0f) {
            double safePosition = Float.isFinite(positionSeconds) ? Math.max(0.0, positionSeconds) : 0.0;
            double fraction = Math.max(0.0, Math.min(1.0, safePosition / durationSeconds));
            // Fifteen stable progress bands. 0% => 1, ~50% => 8, 100% => 15.
            int level = 1 + (int) Math.round(fraction * 14.0);
            return Math.max(1, Math.min(15, level));
        }
        return 7;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(15, value));
    }
}