package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

public class RequestAudioFilePacketC2S {
    private final String audioId;
    private final java.util.UUID transferId;

    public RequestAudioFilePacketC2S(String audioId, java.util.UUID transferId) {
        this.audioId = audioId;
        this.transferId = transferId;
    }

    public RequestAudioFilePacketC2S(FriendlyByteBuf buf) {
        this.audioId = buf.readUtf();
        this.transferId = buf.readUUID();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(audioId);
        buf.writeUUID(transferId);
    }

    public static void handle(RequestAudioFilePacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            SimplySpeakers.getAudioFileManager().sendAudioFile(player, pkt.audioId, pkt.transferId);
        });
    }
}