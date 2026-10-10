package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.portable.PortableEmitterSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client poses keyed by the server's reserved token, never by the carrier's block position. */
public final class ClientPortableSpeakers {
    private static final Map<BlockPos, Pose> poses = new ConcurrentHashMap<>();
    private static long lastClientTick = System.nanoTime();
    private static final long INTERPOLATION_NANOS = 100_000_000L;
    private ClientPortableSpeakers() {}

    private static final class Pose {
        PortableEmitterSnapshot snapshot;
        Vec3 from, target;
        long received;
        Pose(PortableEmitterSnapshot snapshot) {
            this.snapshot = snapshot;
            this.from = this.target = new Vec3(snapshot.x(), snapshot.y(), snapshot.z());
            this.received = System.nanoTime();
        }
        Vec3 interpolated(long now) {
            double alpha = Math.max(0, Math.min(1, (double) (now - received) / INTERPOLATION_NANOS));
            return from.lerp(target, alpha);
        }
        void update(PortableEmitterSnapshot next) {
            long now = System.nanoTime();
            Vec3 current = interpolated(now);
            target = new Vec3(next.x(), next.y(), next.z());
            // Teleports snap once, rather than sweeping sound through unrelated locations.
            from = current.distanceToSqr(target) > 256 ? target : current;
            received = now;
            snapshot = next;
        }
    }

    public static boolean isPortableToken(BlockPos token) { return token != null && token.getY() == -2048; }
    private static boolean inCurrentDimension(PortableEmitterSnapshot snapshot) {
        var client = Minecraft.getInstance();
        return client != null && client.level != null
                && client.level.dimension().location().toString().equals(snapshot.dimension());
    }

    /** Only an authoritative play packet may create an audible portable emitter. */
    public static boolean begin(BlockPos token, PortableEmitterSnapshot snapshot) {
        if (!isPortableToken(token) || snapshot == null || !inCurrentDimension(snapshot)) return false;
        Pose old = poses.get(token);
        if (old != null && (!old.snapshot.identity().equals(snapshot.identity())
                || !old.snapshot.holderId().equals(snapshot.holderId()))) ClientAudioPlayer.stop(token);
        poses.compute(token.immutable(), (key, pose) -> {
            if (pose == null) return new Pose(snapshot);
            pose.update(snapshot);
            return pose;
        });
        return true;
    }

    /** Late movement packets cannot resurrect a stopped source or overwrite a reused identity. */
    public static void update(BlockPos token, PortableEmitterSnapshot snapshot) {
        if (snapshot == null || !inCurrentDimension(snapshot)) return;
        Pose pose = poses.get(token);
        if (pose == null || !pose.snapshot.identity().equals(snapshot.identity())
                || !pose.snapshot.holderId().equals(snapshot.holderId())) return;
        pose.update(snapshot);
    }

    public static void tick() {
        lastClientTick = System.nanoTime();
        // A safety net for world swaps where a loader does not emit a respawn callback.
        for (var entry : poses.entrySet()) {
            if (!inCurrentDimension(entry.getValue().snapshot)) ClientAudioPlayer.stop(entry.getKey());
        }
    }

    /** Tracked carriers use their interpolated pose; distant carriers use server snapshots. */
    public static Vec3 resolvePosition(BlockPos token) {
        Pose pose = poses.get(token);
        if (pose == null || !inCurrentDimension(pose.snapshot)) return null;
        var level = Minecraft.getInstance().level;
        var holder = level.getPlayerByUUID(pose.snapshot.holderId());
        if (holder != null) {
            if (!holder.isAlive()) return null;
            float partial = (float) Math.max(0, Math.min(1,
                    (double) (System.nanoTime() - lastClientTick) / 50_000_000L));
            return holder.getPosition(partial).add(0, PortableEmitterSnapshot.EMITTER_HEIGHT, 0);
        }
        return pose.interpolated(System.nanoTime());
    }

    public static double[] resolveFacing(BlockPos token) {
        Pose pose = poses.get(token);
        if (pose == null || !inCurrentDimension(pose.snapshot)) return null;
        var holder = Minecraft.getInstance().level.getPlayerByUUID(pose.snapshot.holderId());
        double yaw = Math.toRadians(holder == null ? pose.snapshot.yaw() : holder.getYRot());
        return new double[] { -Math.sin(yaw), Math.cos(yaw) };
    }

    public static void remove(BlockPos token) { poses.remove(token); }
    public static void clear() { poses.clear(); }
    static int poseCount() { return poses.size(); }
    static boolean contains(BlockPos token) { return poses.containsKey(token); }
    static Set<BlockPos> tokens() { return Set.copyOf(poses.keySet()); }
}
