package com.nstut.simplyspeakers.client;

/** Deterministic listener poses shared by both benchmark backends. */
final class ZonePreviewScenario {
    static final int PORTABLE_SETTLE_TICK = 20;
    record Pose(double x, double y, double z, float yaw) {}

    private ZonePreviewScenario() {}

    static Pose listener(int phase, int tick) {
        if (phase != 4) return new Pose(0, -60, 0, 0);
        // Exercise movement first, then allow 80 ticks for particle history to settle
        // before sampling. Render throughput cannot change the measured pose.
        double radians = Math.min(Math.max(tick, 0), PORTABLE_SETTLE_TICK) * .035;
        return new Pose(Math.cos(radians) * 3, -60, Math.sin(radians) * 3,
                (float) Math.toDegrees(radians));
    }
}
