package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;

import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class UpdateAudioDropoffPacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateAudioDropoffPacketC2S> TYPE = 
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "update_audio_dropoff"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateAudioDropoffPacketC2S> STREAM_CODEC = 
        StreamCodec.of(UpdateAudioDropoffPacketC2S::encode, UpdateAudioDropoffPacketC2S::decode);

    private final BlockPos pos;
    private final float audioDropoff;

    public UpdateAudioDropoffPacketC2S(BlockPos pos, float audioDropoff) {
        this.pos = pos.immutable();
        this.audioDropoff = audioDropoff;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, UpdateAudioDropoffPacketC2S packet) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeFloat(com.nstut.simplyspeakers.math.AudioMath.sanitizeFloat(packet.audioDropoff, 1.0f));
    }

    public static UpdateAudioDropoffPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new UpdateAudioDropoffPacketC2S(buffer.readBlockPos(), com.nstut.simplyspeakers.math.AudioMath.sanitizeFloat(buffer.readFloat(), 1.0f));
    }

    public static void handle(UpdateAudioDropoffPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (!SpeakerPacketSecurity.canModify(player, packet.pos)) {
                return;
            }

            Level level = player.level();
            BlockEntity blockEntity = level.getBlockEntity(packet.pos);
            if (blockEntity instanceof SpeakerBlockEntity speakerEntity) {
                speakerEntity.setAudioDropoff(packet.audioDropoff);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

