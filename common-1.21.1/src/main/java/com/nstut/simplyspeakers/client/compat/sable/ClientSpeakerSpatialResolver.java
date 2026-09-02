package com.nstut.simplyspeakers.client.compat.sable;

import com.nstut.simplyspeakers.audio.DirectionalAudio;
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

    /**
     * Resolves the emitter's cone facing through the same body transform used for the
     * render position, so a rotated plot body rotates the directional beam with it.
     *
     * @return a normalized horizontal direction {@code {x, z}} in world space, the static
     *         vanilla facing when the emitter is not inside a Sable plot, or {@code null}
     *         when the emitter is inside a plot grid without a resolvable sub level.
     */
    public static @Nullable double[] resolveRenderFacing(ClientLevel level, BlockPos position, int facingOrdinal) {
        double[] local = DirectionalAudio.facingFromOrdinal(facingOrdinal);
        SubLevelAccess subLevel = SABLE.getContaining(level, position);
        if (subLevel instanceof ClientSubLevelAccess clientSubLevel) {
            Vec3 world = clientSubLevel.renderPose().transformNormal(
                    new Vec3(local[0], 0.0, local[1]));
            return DirectionalAudio.normalize(world.x, world.z);
        }
        return SABLE.isInPlotGrid(level, position) ? null : local;
    }
}
