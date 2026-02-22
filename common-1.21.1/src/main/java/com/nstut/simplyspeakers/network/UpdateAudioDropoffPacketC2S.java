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

public class UpdateAudioDropoffPacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateAudioDropoffPacketC2S> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "update_audio_dropoff"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateAudioDropoffPacketC2S> STREAM_CODEC = 
        StreamCodec.of(UpdateAudioDropoffPacketC2S::encode, UpdateAudioDropoffPacketC2S::decode);

    private final BlockPos pos;
    private final float audioDropoff;

    public UpdateAudioDropoffPacketC2S(BlockPos pos, float audioDropoff) {
        this.pos = pos;
        this.audioDropoff = audioDropoff;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, UpdateAudioDropoffPacketC2S packet) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeFloat(packet.audioDropoff);
    }

    public static UpdateAudioDropoffPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new UpdateAudioDropoffPacketC2S(buffer.readBlockPos(), buffer.readFloat());
    }

    public static void handle(UpdateAudioDropoffPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (player != null) {
                ServerLevel level = player.serverLevel();
                if (level.isLoaded(packet.pos)) {
                    BlockEntity blockEntity = level.getBlockEntity(packet.pos);
                    if (blockEntity instanceof SpeakerBlockEntity speakerEntity) {
                        speakerEntity.setAudioDropoff(packet.audioDropoff);
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
