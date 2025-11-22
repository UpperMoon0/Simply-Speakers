package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

public class StopPlaybackPacketC2S {
    private final BlockPos blockPos;

    public StopPlaybackPacketC2S(BlockPos blockPos) {
        this.blockPos = blockPos;
    }

    public StopPlaybackPacketC2S(FriendlyByteBuf buf) {
        this.blockPos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.blockPos);
    }

    public static void handle(StopPlaybackPacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            ServerLevel level = player.serverLevel();
            if (level.getBlockEntity(pkt.blockPos) instanceof SpeakerBlockEntity speaker) {
                speaker.stopAudio();
            }
        });
    }
}