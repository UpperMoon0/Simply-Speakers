package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerLink;
import com.nstut.simplyspeakers.SpeakerPermissions;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry;
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

    /**
     * Resolves the shared {@link SpeakerState} behind a speaker or proxy block entity.
     * Returns null for an unlinked proxy speaker, which has no shared state to protect.
     */
    private static SpeakerState resolveSpeakerState(BlockEntity be) {
        if (be instanceof SpeakerBlockEntity speaker) {
            return speaker.getSpeakerState();
        }
        if (be instanceof ProxySpeakerBlockEntity proxy) {
            return proxy.getSpeakerState();
        }
        return null;
    }

    private static boolean isOperator(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    /**
     * Full authorization for mutating control packets (transport, loop, stop, track
     * selection, volume/range/dropoff, uploads): physical security via
     * {@link #canModify(ServerPlayer, BlockPos)} plus the speaker network's
     * OWNER_ONLY/TRUSTED/PUBLIC access policy via {@link SpeakerPermissions#canControl}.
     * An unlinked proxy speaker has no shared state to protect and only requires
     * physical access.
     */
    public static boolean canControlSpeaker(ServerPlayer player, BlockPos pos) {
        if (!canModify(player, pos)) {
            return false;
        }
        BlockEntity be = player.level().getBlockEntity(pos);
        SpeakerState state = resolveSpeakerState(be);
        if (state == null) {
            return be instanceof ProxySpeakerBlockEntity;
        }
        return SpeakerPermissions.canControl(state, player.getUUID(), isOperator(player));
    }

    /**
     * Authorization for re-linking a speaker or proxy ({@code SetSpeakerIdPacketC2S}):
     * manage rights on the block's current state (prevents re-pointing an
     * OWNER_ONLY/TRUSTED network's speaker or proxy) and, when the new id names an
     * existing network, manage rights on that destination network too. Linking an
     * unlinked block or creating a brand-new network remains allowed.
     */
    public static boolean canRelinkSpeaker(ServerPlayer player, BlockPos pos, String newSpeakerId) {
        if (!canModify(player, pos)) {
            return false;
        }
        BlockEntity be = player.level().getBlockEntity(pos);
        if (!(be instanceof SpeakerBlockEntity) && !(be instanceof ProxySpeakerBlockEntity)) {
            return false;
        }
        boolean isOp = isOperator(player);
        SpeakerState currentState = resolveSpeakerState(be);
        if (currentState != null && !SpeakerPermissions.canManage(currentState, player.getUUID(), isOp)) {
            return false;
        }
        String newId = newSpeakerId == null ? "" : newSpeakerId.trim();
        if (SpeakerLink.isLinkableId(newId)) {
            SpeakerState destination = ServerSpeakerRegistry.getSpeakerState(player.level(), "net_" + newId);
            if (destination != null && !SpeakerPermissions.canManage(destination, player.getUUID(), isOp)) {
                return false;
            }
        }
        return true;
    }
}
