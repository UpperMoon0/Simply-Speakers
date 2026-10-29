package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public class RequestAudioFilePacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestAudioFilePacketC2S> TYPE = 
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "request_audio_file"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestAudioFilePacketC2S> STREAM_CODEC = 
        StreamCodec.of(RequestAudioFilePacketC2S::encode, RequestAudioFilePacketC2S::decode);

    private final String audioId;
    private final java.util.UUID transferId;

    public RequestAudioFilePacketC2S(String audioId, java.util.UUID transferId) {
        this.audioId = audioId;
        this.transferId = transferId;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, RequestAudioFilePacketC2S packet) {
        buffer.writeUtf(packet.audioId);
        buffer.writeUUID(packet.transferId);
    }

    public static RequestAudioFilePacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new RequestAudioFilePacketC2S(buffer.readUtf(), buffer.readUUID());
    }

    public static void handle(RequestAudioFilePacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            SimplySpeakers.getAudioFileManager().sendAudioFile(player, packet.audioId, packet.transferId);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}


