package com.nstut.simplyspeakers.audio;

import java.util.Objects;
import java.util.function.LongSupplier;

/** Tracks the current playback position across decoder loads and audio-context reloads. */
public final class PlaybackTimeline {
    private final LongSupplier nanoClock;
    private float initialOffsetSeconds;
    private long anchorNanos;

    public PlaybackTimeline(float authoritativeOffsetSeconds) {
        this(authoritativeOffsetSeconds, System::nanoTime);
    }

    PlaybackTimeline(float authoritativeOffsetSeconds, LongSupplier nanoClock) {
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        initialOffsetSeconds = Float.isFinite(authoritativeOffsetSeconds)
                ? Math.max(0.0f, authoritativeOffsetSeconds) : 0.0f;
        anchorNanos = nanoClock.getAsLong();
    }

    /** Includes time spent loading or rebuilding a decoder without moving the anchor. */
    public synchronized float offsetSeconds() {
        // Subtract before converting so System.nanoTime's signed wrap remains valid.
        long elapsedNanos = Math.max(0L, nanoClock.getAsLong() - anchorNanos);
        double seconds = initialOffsetSeconds + elapsedNanos / 1_000_000_000.0;
        return (float) Math.min(Float.MAX_VALUE, seconds);
    }

    /**
     * Starts a fresh timeline when a decoder naturally finishes a loop. Unknown-length
     * streams must not seek through all earlier loops after an audio-context reload.
     */
    public synchronized void restartLoop() {
        initialOffsetSeconds = 0.0f;
        anchorNanos = nanoClock.getAsLong();
    }
}
