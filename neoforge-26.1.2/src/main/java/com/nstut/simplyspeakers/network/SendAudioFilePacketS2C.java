package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public class SendAudioFilePacketS2C implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SendAudioFilePacketS2C> TYPE = 
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "send_audio_file"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, SendAudioFilePacketS2C> STREAM_CODEC = 
        StreamCodec.of(SendAudioFilePacketS2C::encode, SendAudioFilePacketS2C::decode);

    private final String audioId;
    private final java.util.UUID transferId;
    private final int index;
    private final byte[] data;
    private final boolean isLast;

    public SendAudioFilePacketS2C(String audioId, java.util.UUID transferId, int index, byte[] data, boolean isLast) {
        this.audioId = audioId;
        this.transferId = transferId;
        this.index = index;
        this.data = data;
        this.isLast = isLast;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, SendAudioFilePacketS2C packet) {
        buffer.writeUtf(packet.audioId);
        buffer.writeUUID(packet.transferId);
        buffer.writeVarInt(packet.index);
        buffer.writeByteArray(packet.data);
        buffer.writeBoolean(packet.isLast);
    }

    public static SendAudioFilePacketS2C decode(RegistryFriendlyByteBuf buffer) {
        return new SendAudioFilePacketS2C(
            buffer.readUtf(),
            buffer.readUUID(),
            buffer.readVarInt(),
            buffer.readByteArray(com.nstut.packetweave.api.OutboundTransfers.CHUNK_BYTES),
            buffer.readBoolean()
        );
    }

    public static void handle(SendAudioFilePacketS2C packet, NetworkManager.PacketContext context) {
        context.queue(() -> {
            ClientAudioPlayer.handleAudioFileChunk(packet.audioId, packet.transferId, packet.index, packet.data, packet.isLast);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}


