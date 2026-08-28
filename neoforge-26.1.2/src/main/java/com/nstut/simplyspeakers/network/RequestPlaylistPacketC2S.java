package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Client -> server request to fetch a speaker block's current playlist. */
public class RequestPlaylistPacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestPlaylistPacketC2S> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "request_playlist"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestPlaylistPacketC2S> STREAM_CODEC =
        StreamCodec.of(RequestPlaylistPacketC2S::encode, RequestPlaylistPacketC2S::decode);

    private final BlockPos pos;

    public RequestPlaylistPacketC2S(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, RequestPlaylistPacketC2S packet) {
        buffer.writeBlockPos(packet.pos);
    }

    public static RequestPlaylistPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new RequestPlaylistPacketC2S(buffer.readBlockPos());
    }

    public static void handle(RequestPlaylistPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (player.level().getBlockEntity(packet.pos) instanceof SpeakerBlockEntity speaker) {
                speaker.sendPlaylistSync(player);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
