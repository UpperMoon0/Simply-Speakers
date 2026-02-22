package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class RequestAudioListPacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestAudioListPacketC2S> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "request_audio_list"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestAudioListPacketC2S> STREAM_CODEC = 
        StreamCodec.of(RequestAudioListPacketC2S::encode, RequestAudioListPacketC2S::decode);

    private final BlockPos blockPos;

    public RequestAudioListPacketC2S(BlockPos blockPos) {
        this.blockPos = blockPos;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, RequestAudioListPacketC2S packet) {
        buffer.writeBlockPos(packet.blockPos);
    }

    public static RequestAudioListPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new RequestAudioListPacketC2S(buffer.readBlockPos());
    }

    public static void handle(RequestAudioListPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            SimplySpeakers.getAudioFileManager().sendAudioList(player, packet.blockPos);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
