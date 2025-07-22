package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

public class SelectAudioPacketC2S {
    private final BlockPos blockPos;
    private final String audioId;

    public SelectAudioPacketC2S(BlockPos blockPos, String audioId) {
        this.blockPos = blockPos;
        this.audioId = audioId;
    }

    public SelectAudioPacketC2S(FriendlyByteBuf buf) {
        this.blockPos = buf.readBlockPos();
        this.audioId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
        buf.writeUtf(audioId);
    }

    public static void handle(SelectAudioPacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            ServerLevel level = player.serverLevel();
            if (level.getBlockEntity(pkt.blockPos) instanceof SpeakerBlockEntity speaker) {
                speaker.setSelectedAudio(pkt.audioId);
            }
        });
    }
}