package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

public class SendAudioFilePacketS2C {
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

    public SendAudioFilePacketS2C(FriendlyByteBuf buf) {
        this.audioId = buf.readUtf();
        this.transferId = buf.readUUID();
        this.index = buf.readVarInt();
        this.data = buf.readByteArray(com.nstut.packetweave.api.OutboundTransfers.CHUNK_BYTES);
        this.isLast = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(audioId);
        buf.writeUUID(transferId);
        buf.writeVarInt(index);
        buf.writeByteArray(data);
        buf.writeBoolean(isLast);
    }

    public static void handle(SendAudioFilePacketS2C pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        context.queue(() -> {
            ClientAudioPlayer.handleAudioFileChunk(pkt.audioId, pkt.transferId, pkt.index, pkt.data, pkt.isLast);
        });
    }
}