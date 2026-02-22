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

public class UpdateProxyAudioDropoffPacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateProxyAudioDropoffPacketC2S> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "update_proxy_audio_dropoff"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateProxyAudioDropoffPacketC2S> STREAM_CODEC = 
        StreamCodec.of(UpdateProxyAudioDropoffPacketC2S::encode, UpdateProxyAudioDropoffPacketC2S::decode);

    private final BlockPos pos;
    private final float audioDropoff;

    public UpdateProxyAudioDropoffPacketC2S(BlockPos pos, float audioDropoff) {
        this.pos = pos;
        this.audioDropoff = audioDropoff;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, UpdateProxyAudioDropoffPacketC2S packet) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeFloat(packet.audioDropoff);
    }

    public static UpdateProxyAudioDropoffPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new UpdateProxyAudioDropoffPacketC2S(buffer.readBlockPos(), buffer.readFloat());
    }

    public static void handle(UpdateProxyAudioDropoffPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (player != null) {
                ServerLevel level = player.serverLevel();
                if (level.isLoaded(packet.pos)) {
                    BlockEntity blockEntity = level.getBlockEntity(packet.pos);
                    if (blockEntity instanceof ProxySpeakerBlockEntity proxySpeakerEntity) {
                        proxySpeakerEntity.setAudioDropoff(packet.audioDropoff);
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
