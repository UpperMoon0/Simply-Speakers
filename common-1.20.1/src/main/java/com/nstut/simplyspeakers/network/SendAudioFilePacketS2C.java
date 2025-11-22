package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

public class SendAudioFilePacketS2C {
    private final String audioId;
    private final byte[] data;
    private final boolean isLast;

    public SendAudioFilePacketS2C(String audioId, byte[] data, boolean isLast) {
        this.audioId = audioId;
        this.data = data;
        this.isLast = isLast;
    }

    public SendAudioFilePacketS2C(FriendlyByteBuf buf) {
        this.audioId = buf.readUtf();
        this.data = buf.readByteArray();
        this.isLast = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(audioId);
        buf.writeByteArray(data);
        buf.writeBoolean(isLast);
    }

    public static void handle(SendAudioFilePacketS2C pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        context.queue(() -> {
            ClientAudioPlayer.handleAudioFileChunk(pkt.audioId, pkt.data, pkt.isLast);
        });
    }
}