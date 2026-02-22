package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class SendAudioFilePacketS2C implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SendAudioFilePacketS2C> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "send_audio_file"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, SendAudioFilePacketS2C> STREAM_CODEC = 
        StreamCodec.of(SendAudioFilePacketS2C::encode, SendAudioFilePacketS2C::decode);

    private final String audioId;
    private final byte[] data;
    private final boolean isLast;

    public SendAudioFilePacketS2C(String audioId, byte[] data, boolean isLast) {
        this.audioId = audioId;
        this.data = data;
        this.isLast = isLast;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, SendAudioFilePacketS2C packet) {
        buffer.writeUtf(packet.audioId);
        buffer.writeByteArray(packet.data);
        buffer.writeBoolean(packet.isLast);
    }

    public static SendAudioFilePacketS2C decode(RegistryFriendlyByteBuf buffer) {
        return new SendAudioFilePacketS2C(
            buffer.readUtf(),
            buffer.readByteArray(),
            buffer.readBoolean()
        );
    }

    public static void handle(SendAudioFilePacketS2C packet, NetworkManager.PacketContext context) {
        context.queue(() -> {
            ClientAudioPlayer.handleAudioFileChunk(packet.audioId, packet.data, packet.isLast);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
