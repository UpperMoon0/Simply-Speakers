package com.nstut.simplyspeakers.audio;

import java.util.function.Supplier;

/**
 * Serializes short audio operations against destruction of their native context.
 *
 * <p>Capture {@link #epoch()} when creating context-owned resources and pass that
 * epoch to every operation on them. Native source and buffer IDs may be reused
 * after a reload, so checking the ID alone cannot establish ownership.</p>
 *
 * <p>Callbacks run under this gate's monitor. They must contain only short native
 * audio operation groups, never decoding, I/O, or waits for other threads. The
 * lifecycle owner must suspend before destroying a context and resume only after
 * the replacement is ready. Lifecycle transitions must be serialized by that
 * owner; resuming does not undo any prior epoch invalidation.</p>
 */
public final class AudioContextGate {
    private long epoch;
    private boolean available = true;

    public synchronized long epoch() {
        return epoch;
    }

    /** Returns whether operations for this epoch can currently run. */
    public synchronized boolean isCurrent(long expectedEpoch) {
        return available && epoch == expectedEpoch;
    }

    public synchronized boolean isAvailable() {
        return available;
    }

    /**
     * Waits for any running operation, then invalidates all prior resource epochs.
     * Every invocation advances the epoch, including while already suspended.
     */
    public synchronized long suspend() {
        available = false;
        return ++epoch;
    }

    /** Makes the current epoch available without making older resources valid. */
    public synchronized void resume() {
        available = true;
    }

    public synchronized <T> T call(long expectedEpoch, Supplier<T> operation) {
        requireCurrent(expectedEpoch);
        return operation.get();
    }

    public synchronized void run(long expectedEpoch, Runnable operation) {
        requireCurrent(expectedEpoch);
        operation.run();
    }

    private void requireCurrent(long expectedEpoch) {
        if (!available || epoch != expectedEpoch) {
            throw new StaleContextException(expectedEpoch, epoch, available);
        }
    }

    /** Expected cancellation when work outlives the context that owns its IDs. */
    public static final class StaleContextException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        public StaleContextException() {
            super("Audio playback resource is no longer active");
        }

        private StaleContextException(long expectedEpoch, long currentEpoch, boolean available) {
            super("Audio context epoch " + expectedEpoch + " is unavailable (current="
                    + currentEpoch + ", available=" + available + ")");
        }
    }
}
