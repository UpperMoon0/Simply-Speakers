package com.nstut.simplyspeakers.compat.sable;

import com.nstut.simplyspeakers.audio.DirectionalAudio;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Resolves stable plot-space identities to Sable's logical world space. */
public final class SpeakerSpatialResolver {
    private static final SableCompanion SABLE = SableCompanion.INSTANCE;

    private SpeakerSpatialResolver() {}

    public static @Nullable Vec3 resolveLogical(Level level, BlockPos position) {
        return resolveLogical(level, Vec3.atCenterOf(position));
    }

    public static @Nullable Vec3 resolveLogical(Level level, Position position) {
        SubLevelAccess subLevel = SABLE.getContaining(level, position);
        Vec3 local = new Vec3(position.x(), position.y(), position.z());
        if (subLevel != null) {
            return subLevel.logicalPose().transformPosition(local);
        }
        return SABLE.isInPlotGrid(level, position) ? null : local;
    }

    /**
     * Resolves the emitter's cone facing through the same body transform used for the
     * logical position, so a rotated plot body rotates the directional beam with it.
     *
     * @return a normalized horizontal direction {@code {x, z}} in logical world space, the
     *         static vanilla facing when the emitter is not inside a Sable plot, or
     *         {@code null} when the emitter is inside a plot grid without a resolvable sub level.
     */
    public static @Nullable double[] resolveLogicalFacing(Level level, BlockPos position, int facingOrdinal) {
        double[] local = DirectionalAudio.facingFromOrdinal(facingOrdinal);
        SubLevelAccess subLevel = SABLE.getContaining(level, position);
        if (subLevel != null) {
            Vec3 world = subLevel.logicalPose().transformNormal(
                    new Vec3(local[0], 0.0, local[1]));
            return DirectionalAudio.normalize(world.x, world.z);
        }
        return SABLE.isInPlotGrid(level, position) ? null : local;
    }
}
