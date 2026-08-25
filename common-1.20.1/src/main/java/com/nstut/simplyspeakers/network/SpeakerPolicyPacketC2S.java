package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.RedstoneMode;
import com.nstut.simplyspeakers.SpeakerAccess;
import com.nstut.simplyspeakers.SpeakerPermissions;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

public class SpeakerPolicyPacketC2S {

    public static final byte OP_NETWORK_NAME = 0;
    public static final byte OP_REDSTONE_MODE = 1;
    public static final byte OP_ACCESS_MODE = 2;
    public static final byte OP_TRUST_CHANGE = 3;
    public static final byte OP_DIRECTIONALITY = 4;
    public static final byte OP_CONE_ANGLE = 5;
    public static final byte OP_REAR_ATTENUATION = 6;

    private final BlockPos pos;
    private final byte op;
    private final int intValue;
    private final boolean boolValue;
    private final String stringValue;

    public SpeakerPolicyPacketC2S(BlockPos pos, byte op, int intValue, boolean boolValue, String stringValue) {
        this.pos = pos;
        this.op = op;
        this.intValue = intValue;
        this.boolValue = boolValue;
        this.stringValue = stringValue != null ? stringValue : "";
    }

    public static SpeakerPolicyPacketC2S networkName(BlockPos pos, String name) {
        return new SpeakerPolicyPacketC2S(pos, OP_NETWORK_NAME, 0, false, name);
    }

    public static SpeakerPolicyPacketC2S redstoneMode(BlockPos pos, RedstoneMode mode) {
        return new SpeakerPolicyPacketC2S(pos, OP_REDSTONE_MODE, mode.ordinal(), false, "");
    }

    public static SpeakerPolicyPacketC2S accessMode(BlockPos pos, SpeakerAccess access) {
        return new SpeakerPolicyPacketC2S(pos, OP_ACCESS_MODE, access.ordinal(), false, "");
    }

    public static SpeakerPolicyPacketC2S trust(BlockPos pos, java.util.UUID player, boolean add) {
        return new SpeakerPolicyPacketC2S(pos, OP_TRUST_CHANGE, 0, add, player.toString());
    }

    public static SpeakerPolicyPacketC2S directionality(BlockPos pos, float value) {
        return new SpeakerPolicyPacketC2S(pos, OP_DIRECTIONALITY, Float.floatToIntBits(value), false, "");
    }

    public static SpeakerPolicyPacketC2S coneAngle(BlockPos pos, int degrees) {
        return new SpeakerPolicyPacketC2S(pos, OP_CONE_ANGLE, degrees, false, "");
    }

    public static SpeakerPolicyPacketC2S rearAttenuation(BlockPos pos, float value) {
        return new SpeakerPolicyPacketC2S(pos, OP_REAR_ATTENUATION, Float.floatToIntBits(value), false, "");
    }

    public SpeakerPolicyPacketC2S(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.op = buf.readByte();
        this.intValue = buf.readVarInt();
        this.boolValue = buf.readBoolean();
        this.stringValue = buf.readUtf(128);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeByte(this.op);
        buf.writeVarInt(this.intValue);
        buf.writeBoolean(this.boolValue);
        buf.writeUtf(this.stringValue, 128);
    }

    public static void handle(SpeakerPolicyPacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (!SpeakerPacketSecurity.canModify(player, pkt.pos)) return;
            ServerLevel level = player.serverLevel();
            if (level.getBlockEntity(pkt.pos) instanceof SpeakerBlockEntity speaker) {
                SpeakerState state = speaker.getSpeakerState();
                if (state == null || !SpeakerPermissions.canManage(state, player.getUUID(), player.hasPermissions(2))) return;
                if (state.getOwnerUuid() == null && pkt.op != OP_DIRECTIONALITY
                        && pkt.op != OP_CONE_ANGLE && pkt.op != OP_REAR_ATTENUATION) {
                    speaker.claimOwnership(player.getUUID());
                }
                switch (pkt.op) {
                    case OP_NETWORK_NAME -> speaker.setNetworkName(pkt.stringValue);
                    case OP_REDSTONE_MODE -> speaker.setRedstoneMode(RedstoneMode.fromIndex(pkt.intValue));
                    case OP_ACCESS_MODE -> speaker.setAccessMode(SpeakerAccess.fromIndex(pkt.intValue));
                    case OP_TRUST_CHANGE -> {
                        try {
                            speaker.modifyTrust(java.util.UUID.fromString(pkt.stringValue), pkt.boolValue);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    case OP_DIRECTIONALITY -> speaker.setDirectionality(Float.intBitsToFloat(pkt.intValue));
                    case OP_CONE_ANGLE -> speaker.setConeAngleDegrees(pkt.intValue);
                    case OP_REAR_ATTENUATION -> speaker.setRearAttenuation(Float.intBitsToFloat(pkt.intValue));
                    default -> {
                    }
                }
            }
        });
    }
}
