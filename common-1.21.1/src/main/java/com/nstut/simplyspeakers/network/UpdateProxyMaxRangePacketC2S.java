package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

public class UpdateProxyMaxRangePacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateProxyMaxRangePacketC2S> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "update_proxy_max_range"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateProxyMaxRangePacketC2S> STREAM_CODEC = 
        StreamCodec.of(UpdateProxyMaxRangePacketC2S::encode, UpdateProxyMaxRangePacketC2S::decode);

    private final BlockPos pos;
    private final int maxRange;

    public UpdateProxyMaxRangePacketC2S(BlockPos pos, int maxRange) {
        this.pos = pos;
        this.maxRange = maxRange;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, UpdateProxyMaxRangePacketC2S packet) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeInt(packet.maxRange);
    }

    public static UpdateProxyMaxRangePacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new UpdateProxyMaxRangePacketC2S(buffer.readBlockPos(), buffer.readInt());
    }

    public static void handle(UpdateProxyMaxRangePacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (player != null) {
                ServerLevel level = player.serverLevel();
                if (level.isLoaded(packet.pos)) {
                    BlockEntity blockEntity = level.getBlockEntity(packet.pos);
                    if (blockEntity instanceof ProxySpeakerBlockEntity proxySpeakerEntity) {
                        proxySpeakerEntity.setMaxRange(packet.maxRange);
                    }
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
