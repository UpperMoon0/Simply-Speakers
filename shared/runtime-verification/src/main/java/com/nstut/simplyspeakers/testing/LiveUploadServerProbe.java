package com.nstut.simplyspeakers.testing;

import net.minecraft.server.MinecraftServer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real server tick-time evidence for the opt-in two-player, 60 MiB upload fixture. */
public final class LiveUploadServerProbe {
    private static UUID transaction;
    private static volatile long committed;
    private static volatile boolean complete;
    private static boolean emitted;
    private static int recoveryTicks;
    private static final List<Double> before = new ArrayList<>(), during = new ArrayList<>(), after = new ArrayList<>();
    private LiveUploadServerProbe() { }
    public static void begin(UUID id, String name) {
        if (LivePlaybackServerProbe.enabled() && "__upload_load_test.wav".equals(name)) transaction = id;
    }
    public static void committed(UUID id, long bytes) {
        if (id.equals(transaction)) committed = bytes;
    }
    public static void completed(UUID id, boolean success) {
        if (!id.equals(transaction)) return;
        if (!success) throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL server upload validation");
        complete = true;
    }
    public static void tick(MinecraftServer server) {
        if (!LivePlaybackServerProbe.enabled() || emitted) return;
        double ms = previousTickMillis(server);
        if (transaction == null) before.add(ms);
        else if (!complete) during.add(ms);
        else {
            after.add(ms);
            if (++recoveryTicks >= 100) {
                if (committed != 60L * 1024 * 1024 || during.isEmpty()
                        || server.getPlayerList().getPlayers().size() < 2)
                    throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL upload size or audience");
                System.out.println("SIMPLYSPEAKERS_SERVER_UPLOAD_PASS bytes=" + committed
                        + " baseline=" + stats(before) + " during=" + stats(during) + " recovery=" + stats(after));
                emitted = true;
            }
        }
    }
    private static double previousTickMillis(MinecraftServer server) {
        try {
            long[] nanos;
            try { nanos = (long[]) MinecraftServer.class.getMethod("getTickTimesNanos").invoke(server); }
            catch (NoSuchMethodException ignored) {
                var field = MinecraftServer.class.getDeclaredField("tickTimes");
                field.setAccessible(true); nanos = (long[]) field.get(server);
            }
            return nanos[Math.floorMod(server.getTickCount() - 1, nanos.length)] / 1_000_000.0;
        } catch (Exception error) { throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL tick timing access", error); }
    }
    private static String stats(List<Double> values) {
        var ordered = values.stream().sorted().toList();
        if (ordered.isEmpty()) return "no-samples";
        return "ticks:" + ordered.size() + ",p50-ms:" + ordered.get(ordered.size()/2)
                + ",p95-ms:" + ordered.get(Math.min(ordered.size()-1, (int)(ordered.size()*.95)))
                + ",max-ms:" + ordered.get(ordered.size()-1);
    }
}
