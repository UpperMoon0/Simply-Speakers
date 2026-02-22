package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

public class UpdateMaxVolumePacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateMaxVolumePacketC2S> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "update_max_volume"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateMaxVolumePacketC2S> STREAM_CODEC = 
        StreamCodec.of(UpdateMaxVolumePacketC2S::encode, UpdateMaxVolumePacketC2S::decode);

    private final BlockPos pos;
    private final float maxVolume;

    public UpdateMaxVolumePacketC2S(BlockPos pos, float maxVolume) {
        this.pos = pos;
        this.maxVolume = maxVolume;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, UpdateMaxVolumePacketC2S packet) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeFloat(packet.maxVolume);
    }

    public static UpdateMaxVolumePacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new UpdateMaxVolumePacketC2S(buffer.readBlockPos(), buffer.readFloat());
    }

    public static void handle(UpdateMaxVolumePacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (player != null) {
                ServerLevel level = player.serverLevel();
                if (level.isLoaded(packet.pos)) {
                    BlockEntity blockEntity = level.getBlockEntity(packet.pos);
                    if (blockEntity instanceof SpeakerBlockEntity speakerEntity) {
                        speakerEntity.setMaxVolume(packet.maxVolume);
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
