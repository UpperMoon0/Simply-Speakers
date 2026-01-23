package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.audio.AudioFileManager;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Supplier;

public class RequestUploadAudioPacketC2S {
    private final BlockPos blockPos;
    private final UUID transactionId;
    private final String fileName;
    private final long fileSize;

    public RequestUploadAudioPacketC2S(BlockPos blockPos, UUID transactionId, String fileName, long fileSize) {
        this.blockPos = blockPos;
        this.transactionId = transactionId;
        this.fileName = fileName;
        this.fileSize = fileSize;
    }

    public RequestUploadAudioPacketC2S(FriendlyByteBuf buf) {
        this.blockPos = buf.readBlockPos();
        this.transactionId = buf.readUUID();
        this.fileName = buf.readUtf();
        this.fileSize = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
        buf.writeUUID(transactionId);
        buf.writeUtf(fileName);
        buf.writeLong(fileSize);
    }

    public static void handle(RequestUploadAudioPacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            SimplySpeakers.LOGGER.info("Received upload request for file: " + pkt.fileName + " with transaction ID: " + pkt.transactionId);
            SimplySpeakers.getAudioFileManager().handleUploadRequest(player, pkt.blockPos, pkt.transactionId, pkt.fileName, pkt.fileSize);
        });
    }
}