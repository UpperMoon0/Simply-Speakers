package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.UUID;
import java.util.function.Supplier;

public class AcknowledgeUploadPacketS2C {
    private final UUID transactionId;
    private final boolean success;
    private final Component message;
    private final BlockPos blockPos;

    public AcknowledgeUploadPacketS2C(UUID transactionId, boolean success, Component message, BlockPos blockPos) {
        this.transactionId = transactionId;
        this.success = success;
        this.message = message;
        this.blockPos = blockPos;
    }

    public AcknowledgeUploadPacketS2C(FriendlyByteBuf buf) {
        this.transactionId = buf.readUUID();
        this.success = buf.readBoolean();
        this.message = buf.readComponent();
        this.blockPos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(transactionId);
        buf.writeBoolean(success);
        buf.writeComponent(message);
        buf.writeBlockPos(blockPos);
    }

    public static void handle(AcknowledgeUploadPacketS2C pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        context.queue(() -> {
            ClientAudioPlayer.handleUploadAcknowledgement(pkt.transactionId, pkt.success, pkt.message, pkt.blockPos);
        });
    }
}