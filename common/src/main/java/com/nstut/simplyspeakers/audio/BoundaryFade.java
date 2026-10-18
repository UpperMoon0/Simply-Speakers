package com.nstut.simplyspeakers.audio;

/** Per-source range envelope. Time is supplied so boundary reversals are deterministic. */
public final class BoundaryFade {
    private float level;
    private float lastSpatialGain;
    private long previousNanos;
    private boolean initialized;
    private boolean wasAudible;

    public float gain(boolean audible, float spatialGain, long nowNanos, int inMillis, int outMillis) {
        // Advance toward the previous target before accepting a new boundary transition.
        if (initialized) {
            double elapsedMillis = Math.max(0L, nowNanos - previousNanos) / 1_000_000.0;
            int duration = wasAudible ? inMillis : outMillis;
            level = duration <= 0 ? (wasAudible ? 1f : 0f)
                    : (float) Math.max(0, Math.min(1, level + (wasAudible ? 1 : -1) * elapsedMillis / duration));
        }
        previousNanos = nowNanos;
        initialized = true;
        wasAudible = audible;
        if ((audible ? inMillis : outMillis) <= 0) level = audible ? 1f : 0f;
        if (audible) lastSpatialGain = Math.max(0f, spatialGain);
        return lastSpatialGain * level;
    }

    /** An immediate emitter removal must not retain that emitter's last gain. */
    public void clearSpatialGain() { lastSpatialGain = 0f; }

    /** Missing emitter transforms must not replay the last known spatial gain. */
    public void reset() {
        level = 0f;
        lastSpatialGain = 0f;
        initialized = false;
        wasAudible = false;
    }

    public boolean silent() { return level <= 0f; }
}
