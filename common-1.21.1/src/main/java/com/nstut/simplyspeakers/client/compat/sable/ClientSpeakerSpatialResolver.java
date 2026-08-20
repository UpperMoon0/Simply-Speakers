package com.nstut.simplyspeakers.client.compat.sable;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Resolves an emitter through Sable's interpolated client render pose. */
public final class ClientSpeakerSpatialResolver {
    private static final SableCompanion SABLE = SableCompanion.INSTANCE;

    private ClientSpeakerSpatialResolver() {}

    public static @Nullable Vec3 resolveRender(ClientLevel level, BlockPos position) {
        Vec3 local = Vec3.atCenterOf(position);
        SubLevelAccess subLevel = SABLE.getContaining(level, position);
        if (subLevel instanceof ClientSubLevelAccess clientSubLevel) {
            return clientSubLevel.renderPose().transformPosition(local);
        }
        return SABLE.isInPlotGrid(level, position) ? null : local;
    }
}
