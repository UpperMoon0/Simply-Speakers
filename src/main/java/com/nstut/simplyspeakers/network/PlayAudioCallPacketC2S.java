package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PlayAudioCallPacketC2S {
    private final BlockPos pos;

    public PlayAudioCallPacketC2S(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(PlayAudioCallPacketC2S msg, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(msg.pos);
    }

    public static PlayAudioCallPacketC2S decode(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        return new PlayAudioCallPacketC2S(pos);
    }

    public static void handle(PlayAudioCallPacketC2S msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            // Get the server player who sent the packet.
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                // Get the block entity at the specified position.
                try (var level = player.level()) {
                    BlockEntity blockEntity = level.getBlockEntity(msg.pos);
                    if (blockEntity instanceof SpeakerBlockEntity) {
                        ((SpeakerBlockEntity) blockEntity).playAudio();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
