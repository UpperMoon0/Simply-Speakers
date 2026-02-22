package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

public class AudioPathPacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AudioPathPacketC2S> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "audio_path"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, AudioPathPacketC2S> STREAM_CODEC = 
        StreamCodec.of(AudioPathPacketC2S::encode, AudioPathPacketC2S::decode);

    private final BlockPos pos;
    private final String audioId;

    public AudioPathPacketC2S(BlockPos pos, String audioId) {
        this.pos = pos;
        this.audioId = audioId;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, AudioPathPacketC2S packet) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeUtf(packet.audioId);
    }

    public static AudioPathPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new AudioPathPacketC2S(buffer.readBlockPos(), buffer.readUtf());
    }

    public static void handle(AudioPathPacketC2S packet, NetworkManager.PacketContext context) {
        context.queue(() -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player != null) {
                if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    BlockEntity blockEntity = serverLevel.getBlockEntity(packet.pos);
                    if (blockEntity instanceof SpeakerBlockEntity speakerEntity) {
                        speakerEntity.setAudioId(packet.audioId);
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
