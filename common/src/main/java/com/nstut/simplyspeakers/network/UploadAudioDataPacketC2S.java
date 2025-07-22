package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.audio.AudioFileManager;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Supplier;

public class UploadAudioDataPacketC2S {
    private final UUID transactionId;
    private final byte[] data;

    public UploadAudioDataPacketC2S(UUID transactionId, byte[] data) {
        this.transactionId = transactionId;
        this.data = data;
    }

    public UploadAudioDataPacketC2S(FriendlyByteBuf buf) {
        this.transactionId = buf.readUUID();
        this.data = buf.readByteArray();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(transactionId);
        buf.writeByteArray(data);
    }

    public static void handle(UploadAudioDataPacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            SimplySpeakers.getAudioFileManager().handleUploadData(player, pkt.transactionId, pkt.data);
        });
    }
}