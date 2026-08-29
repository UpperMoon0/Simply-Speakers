package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerPermissions;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Client -> server transport commands: the backbone of the 0.8.x transport bar, CC:T bridge, and command API. */
public class TransportControlPacketC2S implements CustomPacketPayload {

    public static final byte ACTION_PLAY = 0;
    public static final byte ACTION_PAUSE = 1;
    public static final byte ACTION_TOGGLE = 2;
    public static final byte ACTION_STOP = 3;
    public static final byte ACTION_RESTART = 4;
    public static final byte ACTION_NEXT = 5;
    public static final byte ACTION_PREVIOUS = 6;
    public static final byte ACTION_SEEK = 7;
    public static final byte ACTION_SEEK_RELATIVE = 8;

    public static final CustomPacketPayload.Type<TransportControlPacketC2S> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "transport_control"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TransportControlPacketC2S> STREAM_CODEC =
        StreamCodec.of(TransportControlPacketC2S::encode, TransportControlPacketC2S::decode);

    private final BlockPos pos;
    private final byte action;
    private final float seekSeconds;

    public TransportControlPacketC2S(BlockPos pos, byte action) {
        this(pos, action, 0.0f);
    }

    public TransportControlPacketC2S(BlockPos pos, byte action, float seekSeconds) {
        this.pos = pos;
        this.action = action;
        this.seekSeconds = seekSeconds;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, TransportControlPacketC2S packet) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeByte(packet.action);
        buffer.writeFloat(packet.seekSeconds);
    }

    public static TransportControlPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new TransportControlPacketC2S(buffer.readBlockPos(), buffer.readByte(), buffer.readFloat());
    }

    public static void handle(TransportControlPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (!SpeakerPacketSecurity.canModify(player, packet.pos)) {
                return;
            }
            if (player.level().getBlockEntity(packet.pos) instanceof SpeakerBlockEntity speaker) {
                SpeakerState state = speaker.getSpeakerState();
                boolean isOp = player.level().getServer() != null && player.level().getServer().getPlayerList().isOp(player.nameAndId());
                if (state == null || !SpeakerPermissions.canControl(state, player.getUUID(), isOp)) {
                    return;
                }
                speaker.transportAction(player.level(), packet.action, packet.seekSeconds);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
