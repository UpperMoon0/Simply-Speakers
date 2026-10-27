package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

public class AudioDownloadReceiptPacketC2S {
    private final long committedBytes;
    private final java.util.UUID transferId;

    public AudioDownloadReceiptPacketC2S(long committedBytes, java.util.UUID transferId) {
        this.committedBytes = committedBytes;
        this.transferId = transferId;
    }

    public AudioDownloadReceiptPacketC2S(FriendlyByteBuf buf) {
        this.committedBytes = buf.readLong();
        this.transferId = buf.readUUID();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(committedBytes);
        buf.writeUUID(transferId);
    }

    public static void handle(AudioDownloadReceiptPacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            com.nstut.packetweave.api.OutboundTransfers.shared().acknowledge(player, pkt.transferId, pkt.committedBytes);
        });
    }
}