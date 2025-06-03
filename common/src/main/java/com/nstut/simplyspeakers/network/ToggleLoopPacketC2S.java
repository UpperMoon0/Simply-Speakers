package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import dev.architectury.networking.NetworkManager; // Changed import
import dev.architectury.networking.NetworkManager.PacketContext; // Added import

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

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.isLooping);
    }

    // Changed NetworkEvent.Context to PacketContext and adjusted method body
    public boolean handle(Supplier<PacketContext> supplier) {
        PacketContext context = supplier.get();
        context.queue(() -> { // Changed enqueueWork to queue
            ServerPlayer player = (ServerPlayer) context.getPlayer(); // Cast to ServerPlayer
            if (player != null) {
                ServerLevel level = player.serverLevel();
                if (level.isLoaded(this.pos)) {
                    BlockEntity blockEntity = level.getBlockEntity(this.pos);
                    if (blockEntity instanceof SpeakerBlockEntity speakerEntity) {
                        speakerEntity.setLooping(this.isLooping);
                    }
                }
            }
        });
        // For Architectury, handling is implicit if the lambda executes without error.
        // No explicit setPacketHandled is typically needed unless there's a specific reason.
        return true; // Return true to indicate success
    }
}