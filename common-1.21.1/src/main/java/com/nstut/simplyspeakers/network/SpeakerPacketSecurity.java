package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class SpeakerPacketSecurity {

    private static final double MAX_INTERACT_DISTANCE_SQR = 64.0; // 8 blocks

    private SpeakerPacketSecurity() {
    }

    /**
     * Validates that a player is authorized to modify the speaker or proxy speaker block entity at pos.
     */
    public static boolean canModify(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null || player.isRemoved()) {
            return false;
        }

        Level level = player.level();
        if (level == null || level.isClientSide()) {
            return false;
        }

        // Distance validation
        double distSqr = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (distSqr > MAX_INTERACT_DISTANCE_SQR) {
            SimplySpeakers.LOGGER.debug("SECURITY: Player {} attempted to modify speaker at {} out of range (distance sq: {})",
                    player.getName().getString(), pos, distSqr);
            return false;
        }

        // Chunk and block entity validation
        if (!level.hasChunkAt(pos)) {
            return false;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SpeakerBlockEntity) && !(be instanceof ProxySpeakerBlockEntity)) {
            SimplySpeakers.LOGGER.debug("SECURITY: Player {} attempted to modify non-speaker block entity at {}",
                    player.getName().getString(), pos);
            return false;
        }

        // Interaction permission hook
        if (!level.mayInteract(player, pos)) {
            SimplySpeakers.LOGGER.debug("SECURITY: Player {} not permitted to interact at {}",
                    player.getName().getString(), pos);
            return false;
        }

        return true;
    }
}
