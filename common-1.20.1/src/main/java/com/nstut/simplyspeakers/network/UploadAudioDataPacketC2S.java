package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Supplier;

public class UploadAudioDataPacketC2S {
    private final UUID transactionId;
    private final int index;
    private final byte[] data;

    public UploadAudioDataPacketC2S(UUID transactionId, int index, byte[] data) {
        this.transactionId = transactionId;
        this.index = index;
        this.data = data;
    }

    public UploadAudioDataPacketC2S(FriendlyByteBuf buf) {
        this.transactionId = buf.readUUID();
        this.index = buf.readVarInt();
        this.data = buf.readByteArray(32000);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(transactionId);
        buf.writeVarInt(index);
        buf.writeByteArray(data);
    }

    public static void handle(UploadAudioDataPacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (SimplySpeakers.getAudioFileManager() != null)
                SimplySpeakers.getAudioFileManager().handleUploadData(player, pkt.transactionId, pkt.index, pkt.data);
        });
    }
}