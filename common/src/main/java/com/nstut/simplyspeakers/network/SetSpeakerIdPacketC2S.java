package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

public class SetSpeakerIdPacketC2S {
    private final BlockPos blockPos;
    private final String speakerId;

    public SetSpeakerIdPacketC2S(BlockPos blockPos, String speakerId) {
        this.blockPos = blockPos;
        this.speakerId = speakerId;
    }

    public SetSpeakerIdPacketC2S(FriendlyByteBuf buf) {
        this.blockPos = buf.readBlockPos();
        this.speakerId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
        buf.writeUtf(speakerId);
    }

    public static void handle(SetSpeakerIdPacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            ServerLevel level = player.serverLevel();
            if (level.getBlockEntity(pkt.blockPos) instanceof SpeakerBlockEntity speaker) {
                speaker.setSpeakerId(pkt.speakerId);
            }
        });
    }
}