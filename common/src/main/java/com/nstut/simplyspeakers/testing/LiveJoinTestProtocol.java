package com.nstut.simplyspeakers.testing;

import java.util.concurrent.atomic.AtomicBoolean;

/** Shared state and markers for the opt-in real-client join smoke test. */
public final class LiveJoinTestProtocol {
    public static final String SYSTEM_PROPERTY = "simplyspeakers.liveJoinTest";
    public static final String PROBE_AUDIO_ID = "__simplyspeakers_live_join_probe__";
    public static final String PASS_MARKER = "SIMPLYSPEAKERS_LIVE_JOIN_TEST_PASS";

    private static final AtomicBoolean DEFERRED = new AtomicBoolean();
    private static final AtomicBoolean COMPLETED = new AtomicBoolean();
    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private LiveJoinTestProtocol() {
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean(SYSTEM_PROPERTY);
    }

    public static void markDeferred() {
        DEFERRED.set(true);
    }

    public static void markCompleted() {
        COMPLETED.set(true);
    }

    public static boolean passed() {
        return DEFERRED.get() && COMPLETED.get();
    }

    /** Keep the synthetic pre-world packet alive across quick-play's pre-connect quit event. */
    public static boolean hasPendingProbe() {
        return isEnabled() && DEFERRED.get() && !COMPLETED.get();
    }

    public static boolean markReported() {
        return REPORTED.compareAndSet(false, true);
    }

    /** Prefer Minecraft's clean shutdown, with a test-only fallback for loader hangs. */
    public static void stopClient(Runnable cleanStop) {
        Thread fallback = new Thread(() -> {
            try {
                Thread.sleep(15_000L);
                System.exit(0);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }, "simplyspeakers-live-test-exit");
        fallback.setDaemon(true);
        fallback.start();
        cleanStop.run();
    }

    public static void reset() {
        DEFERRED.set(false);
        COMPLETED.set(false);
        REPORTED.set(false);
    }
}
