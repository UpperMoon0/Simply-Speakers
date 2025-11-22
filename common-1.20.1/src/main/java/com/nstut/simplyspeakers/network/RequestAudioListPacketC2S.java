package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.audio.AudioFileManager;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

public class RequestAudioListPacketC2S {
    private final BlockPos blockPos;

    public RequestAudioListPacketC2S(BlockPos blockPos) {
        this.blockPos = blockPos;
    }

    public RequestAudioListPacketC2S(FriendlyByteBuf buf) {
        this.blockPos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
    }

    public static void handle(RequestAudioListPacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            SimplySpeakers.getAudioFileManager().sendAudioList(player, pkt.blockPos);
        });
    }
}