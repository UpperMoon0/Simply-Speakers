package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.UUID;
import java.util.function.Supplier;

public class RespondUploadAudioPacketS2C {
    private final UUID transactionId;
    private final boolean allowed;
    private final int maxChunkSize;
    private final Component message;

    public RespondUploadAudioPacketS2C(UUID transactionId, boolean allowed, int maxChunkSize, Component message) {
        this.transactionId = transactionId;
        this.allowed = allowed;
        this.maxChunkSize = maxChunkSize;
        this.message = message;
    }

    public RespondUploadAudioPacketS2C(FriendlyByteBuf buf) {
        this.transactionId = buf.readUUID();
        this.allowed = buf.readBoolean();
        this.maxChunkSize = buf.readInt();
        this.message = buf.readComponent();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(transactionId);
        buf.writeBoolean(allowed);
        buf.writeInt(maxChunkSize);
        buf.writeComponent(message);
    }

    public static void handle(RespondUploadAudioPacketS2C pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        context.queue(() -> {
            ClientAudioPlayer.handleUploadResponse(pkt.transactionId, pkt.allowed, pkt.maxChunkSize, pkt.message);
        });
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    public Component getMessage() {
        return message;
    }
}