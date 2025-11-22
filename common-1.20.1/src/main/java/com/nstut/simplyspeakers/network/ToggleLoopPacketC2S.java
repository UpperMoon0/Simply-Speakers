package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Supplier;

public class ToggleLoopPacketC2S {

    private final BlockPos pos;
    private final boolean isLooping;

    public ToggleLoopPacketC2S(BlockPos pos, boolean isLooping) {
        this.pos = pos;
        this.isLooping = isLooping;
    }

    public ToggleLoopPacketC2S(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.isLooping = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.isLooping);
    }

    public static void handle(ToggleLoopPacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (player != null) {
                ServerLevel level = player.serverLevel();
                if (level.isLoaded(pkt.pos)) {
                    BlockEntity blockEntity = level.getBlockEntity(pkt.pos);
                    if (blockEntity instanceof SpeakerBlockEntity speakerEntity) {
                        speakerEntity.setLooping(pkt.isLooping);
                    }
                }
            }
        });
    }
}