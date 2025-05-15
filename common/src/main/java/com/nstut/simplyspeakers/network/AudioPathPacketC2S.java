package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Supplier;

public class AudioPathPacketC2S {

    private final BlockPos pos;
    private final String musicPath;

    public AudioPathPacketC2S(BlockPos pos, String musicPath) {
        this.pos = pos;
        this.musicPath = musicPath;
    }

    public AudioPathPacketC2S(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.musicPath = buffer.readUtf();
    }

    public static void encode(AudioPathPacketC2S packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeUtf(packet.musicPath);
    }

    public static void handle(AudioPathPacketC2S packet, Supplier<NetworkManager.PacketContext> contextSupplier) {
        NetworkManager.PacketContext context = contextSupplier.get();
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player != null) {
                if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    BlockEntity blockEntity = serverLevel.getBlockEntity(packet.pos);
                    if (blockEntity instanceof SpeakerBlockEntity) {
                        ((SpeakerBlockEntity) blockEntity).setAudioPath(packet.musicPath);
                    }
                }
            }
        });
    }
}