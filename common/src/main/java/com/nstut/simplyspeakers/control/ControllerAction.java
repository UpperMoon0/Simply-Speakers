package com.nstut.simplyspeakers.control;

/** One job per controller; the action chooses pulse or continuous input semantics. */
public enum ControllerAction {
    TOGGLE, NEXT, PREVIOUS, ANNOUNCEMENT, ENABLED, VOLUME, STOP, RESTART, TRACK;

    public boolean continuous() { return this == ENABLED || this == VOLUME; }
    public boolean supportsProxy() { return this == ENABLED || this == VOLUME; }
    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    public static ControllerAction byId(String value) {
        for (var action : values()) if (action.id().equals(value)) return action;
        return TOGGLE;
    }
    public boolean shouldApply(int previous, int current) {
        previous = clamp(previous);
        current = clamp(current);
        return continuous() ? previous != current : previous == 0 && current > 0;
    }
    public static int clamp(int signal) { return Math.max(0, Math.min(15, signal)); }
    public static int trackIndex(int signal,int size) {
        return signal<=0 || size<=0 ? -1 : Math.min(clamp(signal)-1,size-1);
    }
    public static float volume(int signal, float ceiling) {
        float safe = Float.isFinite(ceiling) ? Math.max(0, Math.min(1, ceiling)) : 1;
        return clamp(signal) / 15.0f * safe;
    }
}
