package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import dev.architectury.networking.NetworkManager;

import java.util.function.Supplier;

public class UpdateMaxRangePacketC2S {

    private final BlockPos pos;
    private final int maxRange;

    public UpdateMaxRangePacketC2S(BlockPos pos, int maxRange) {
        this.pos = pos;
        this.maxRange = maxRange;
    }

    public UpdateMaxRangePacketC2S(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.maxRange = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeInt(this.maxRange);
    }

    public static void handle(UpdateMaxRangePacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (player != null) {
                ServerLevel level = player.serverLevel();
                if (level.isLoaded(pkt.pos)) {
                    BlockEntity blockEntity = level.getBlockEntity(pkt.pos);
                    if (blockEntity instanceof SpeakerBlockEntity speakerEntity) {
                        speakerEntity.setMaxRange(pkt.maxRange);
                    }
                }
            }
        });
    }
}