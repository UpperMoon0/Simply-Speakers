package com.nstut.simplyspeakers.client.compat.sable;

import com.nstut.simplyspeakers.audio.DirectionalAudio;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Resolves an emitter through Sable's interpolated client render pose. */
public final class ClientSpeakerSpatialResolver {
    private static final SableCompanion SABLE = SableCompanion.INSTANCE;

    private ClientSpeakerSpatialResolver() {}

    /** Caller-owned storage for a position and its horizontal facing resolved together. */
    public static final class RenderPose {
        public final org.joml.Vector3d position = new org.joml.Vector3d();
        public final org.joml.Vector3d facing = new org.joml.Vector3d();
    }

    public static boolean resolveRenderPose(ClientLevel level, BlockPos position, double facingX,
                                            double facingZ, RenderPose destination) {
        return resolveRenderPose(SABLE, level, position, facingX, facingZ, destination);
    }

    public static boolean resolveRenderPose(ClientLevel level, Position position, double facingX,
                                            double facingZ, RenderPose destination) {
        return resolveRenderPose(SABLE, level, position, facingX, facingZ, destination);
    }

    static boolean resolveRenderPose(SableCompanion sable, ClientLevel level, BlockPos position,
                                     double facingX, double facingZ, RenderPose destination) {
        SubLevelAccess subLevel = sable.getContaining(level, position);
        boolean unresolved = !(subLevel instanceof ClientSubLevelAccess) && sable.isInPlotGrid(level, position);
        return resolveRenderPose(subLevel, unresolved, position.getX() + .5, position.getY() + .5,
                position.getZ() + .5, facingX, facingZ, destination);
    }

    static boolean resolveRenderPose(SableCompanion sable, ClientLevel level, Position position,
                                     double facingX, double facingZ, RenderPose destination) {
        SubLevelAccess subLevel = sable.getContaining(level, position);
        boolean unresolved = !(subLevel instanceof ClientSubLevelAccess) && sable.isInPlotGrid(level, position);
        return resolveRenderPose(subLevel, unresolved, position.x(), position.y(), position.z(),
                facingX, facingZ, destination);
    }

    private static boolean resolveRenderPose(SubLevelAccess subLevel, boolean unresolved,
                                             double x, double y, double z, double facingX,
                                             double facingZ, RenderPose destination) {
        if (unresolved) return false;
        destination.position.set(x, y, z);
        destination.facing.set(facingX, 0, facingZ);
        if (subLevel instanceof ClientSubLevelAccess clientSubLevel) {
            var pose = clientSubLevel.renderPose();
            pose.transformPosition(destination.position);
            pose.transformNormal(destination.facing);
        }
        double length = Math.hypot(destination.facing.x, destination.facing.z);
        if (length < 1.0E-6) destination.facing.set(0, 0, -1);
        else destination.facing.set(destination.facing.x / length, 0, destination.facing.z / length);
        return true;
    }

    public static @Nullable Vec3 resolveRender(ClientLevel level, BlockPos position) {
        return resolveRender(SABLE, level, position);
    }

    /** Continuous entity coordinates must not be rounded to a block center. */
    public static @Nullable Vec3 resolveRender(ClientLevel level, Position position) {
        return resolveRender(SABLE, level, position);
    }

    static @Nullable Vec3 resolveRender(SableCompanion sable, ClientLevel level, Position position) {
        Vec3 local = new Vec3(position.x(), position.y(), position.z());
        SubLevelAccess subLevel = sable.getContaining(level, position);
        if (subLevel instanceof ClientSubLevelAccess clientSubLevel) {
            return clientSubLevel.renderPose().transformPosition(local);
        }
        return sable.isInPlotGrid(level, position) ? null : local;
    }

    public static @Nullable double[] resolveRenderFacing(ClientLevel level, Position position, double facingX, double facingZ) {
        return resolveRenderFacing(SABLE, level, position, facingX, facingZ);
    }

    static @Nullable double[] resolveRenderFacing(SableCompanion sable, ClientLevel level, Position position, double facingX, double facingZ) {
        SubLevelAccess subLevel = sable.getContaining(level, position);
        if (subLevel instanceof ClientSubLevelAccess clientSubLevel) {
            Vec3 world = clientSubLevel.renderPose().transformNormal(new Vec3(facingX, 0, facingZ));
            return DirectionalAudio.normalize(world.x, world.z);
        }
        return sable.isInPlotGrid(level, position) ? null : DirectionalAudio.normalize(facingX, facingZ);
    }

    static @Nullable Vec3 resolveRender(SableCompanion sable, ClientLevel level, BlockPos position) {
        Vec3 local = Vec3.atCenterOf(position);
        SubLevelAccess subLevel = sable.getContaining(level, position);
        if (subLevel instanceof ClientSubLevelAccess clientSubLevel) {
            return clientSubLevel.renderPose().transformPosition(local);
        }
        return sable.isInPlotGrid(level, position) ? null : local;
    }

    /**
     * Resolves the emitter's cone facing through the same body transform used for the
     * render position, so a rotated plot body rotates the directional beam with it.
     *
     * @return a normalized horizontal direction {@code {x, z}} in world space, the static
     *         vanilla facing when the emitter is not inside a Sable plot, or {@code null}
     *         when the emitter is inside a plot grid without a resolvable sub level.
     */
    public static @Nullable double[] resolveRenderFacing(ClientLevel level, BlockPos position, int facingOrdinal) {
        return resolveRenderFacing(SABLE, level, position, facingOrdinal);
    }

    static @Nullable double[] resolveRenderFacing(SableCompanion sable, ClientLevel level, BlockPos position, int facingOrdinal) {
        double[] local = DirectionalAudio.facingFromOrdinal(facingOrdinal);
        SubLevelAccess subLevel = sable.getContaining(level, position);
        if (subLevel instanceof ClientSubLevelAccess clientSubLevel) {
            Vec3 world = clientSubLevel.renderPose().transformNormal(
                    new Vec3(local[0], 0.0, local[1]));
            return DirectionalAudio.normalize(world.x, world.z);
        }
        return sable.isInPlotGrid(level, position) ? null : local;
    }
}
