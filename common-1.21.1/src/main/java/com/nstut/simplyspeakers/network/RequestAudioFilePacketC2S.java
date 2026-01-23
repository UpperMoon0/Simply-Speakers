package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.audio.AudioFileManager;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

public class RequestAudioFilePacketC2S {
    private final String audioId;

    public RequestAudioFilePacketC2S(String audioId) {
        this.audioId = audioId;
    }

    public RequestAudioFilePacketC2S(FriendlyByteBuf buf) {
        this.audioId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(audioId);
    }

    public static void handle(RequestAudioFilePacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            SimplySpeakers.getAudioFileManager().sendAudioFile(player, pkt.audioId);
        });
    }
}