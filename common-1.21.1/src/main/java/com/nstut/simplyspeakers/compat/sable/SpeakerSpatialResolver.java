package com.nstut.simplyspeakers.compat.sable;

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
}
