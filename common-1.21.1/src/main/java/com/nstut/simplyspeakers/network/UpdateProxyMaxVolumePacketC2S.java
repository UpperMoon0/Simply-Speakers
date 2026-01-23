package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import dev.architectury.networking.NetworkManager;

import java.util.function.Supplier;

public class UpdateProxyMaxVolumePacketC2S {

    private final BlockPos pos;
    private final float maxVolume;

    public UpdateProxyMaxVolumePacketC2S(BlockPos pos, float maxVolume) {
        this.pos = pos;
        this.maxVolume = maxVolume;
    }

    public UpdateProxyMaxVolumePacketC2S(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.maxVolume = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeFloat(this.maxVolume);
    }

    public static void handle(UpdateProxyMaxVolumePacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (player != null) {
                ServerLevel level = player.serverLevel();
                if (level.isLoaded(pkt.pos)) {
                    BlockEntity blockEntity = level.getBlockEntity(pkt.pos);
                    if (blockEntity instanceof ProxySpeakerBlockEntity proxySpeakerEntity) {
                        proxySpeakerEntity.setMaxVolume(pkt.maxVolume);
                    }
                }
            }
        });
    }
}