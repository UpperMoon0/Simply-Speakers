package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.RedstoneMode;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerAccess;
import com.nstut.simplyspeakers.SpeakerPermissions;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Client -> server policy updates: network name, redstone mode, access, trusted players, directionality. */
public class SpeakerPolicyPacketC2S implements CustomPacketPayload {

    public static final byte OP_NETWORK_NAME = 0;
    public static final byte OP_REDSTONE_MODE = 1;
    public static final byte OP_ACCESS_MODE = 2;
    public static final byte OP_TRUST_CHANGE = 3;
    public static final byte OP_DIRECTIONALITY = 4;
    public static final byte OP_CONE_ANGLE = 5;
    public static final byte OP_REAR_ATTENUATION = 6;

    public static final CustomPacketPayload.Type<SpeakerPolicyPacketC2S> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "speaker_policy"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpeakerPolicyPacketC2S> STREAM_CODEC =
        StreamCodec.of(SpeakerPolicyPacketC2S::encode, SpeakerPolicyPacketC2S::decode);

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

    public static void encode(RegistryFriendlyByteBuf buffer, SpeakerPolicyPacketC2S packet) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeByte(packet.op);
        buffer.writeVarInt(packet.intValue);
        buffer.writeBoolean(packet.boolValue);
        buffer.writeUtf(packet.stringValue, 128);
    }

    public static SpeakerPolicyPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new SpeakerPolicyPacketC2S(buffer.readBlockPos(), buffer.readByte(),
                buffer.readVarInt(), buffer.readBoolean(), buffer.readUtf(128));
    }

    public static void handle(SpeakerPolicyPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (!SpeakerPacketSecurity.canModify(player, packet.pos)) {
                return;
            }
            if (player.level().getBlockEntity(packet.pos) instanceof SpeakerBlockEntity speaker) {
                SpeakerState state = speaker.getSpeakerState();
                if (state == null || !SpeakerPermissions.canManage(state, player.getUUID(), player.hasPermissions(2))) {
                    return;
                }
                if (state.getOwnerUuid() == null && packet.op != OP_DIRECTIONALITY
                        && packet.op != OP_CONE_ANGLE && packet.op != OP_REAR_ATTENUATION) {
                    speaker.claimOwnership(player.getUUID());
                    state = speaker.getSpeakerState();
                }
                switch (packet.op) {
                    case OP_NETWORK_NAME -> speaker.setNetworkName(packet.stringValue);
                    case OP_REDSTONE_MODE -> speaker.setRedstoneMode(RedstoneMode.fromIndex(packet.intValue));
                    case OP_ACCESS_MODE -> speaker.setAccessMode(SpeakerAccess.fromIndex(packet.intValue));
                    case OP_TRUST_CHANGE -> {
                        try {
                            speaker.modifyTrust(java.util.UUID.fromString(packet.stringValue), packet.boolValue);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    case OP_DIRECTIONALITY -> speaker.setDirectionality(Float.intBitsToFloat(packet.intValue));
                    case OP_CONE_ANGLE -> speaker.setConeAngleDegrees(packet.intValue);
                    case OP_REAR_ATTENUATION -> speaker.setRearAttenuation(Float.intBitsToFloat(packet.intValue));
                    default -> {
                    }
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
