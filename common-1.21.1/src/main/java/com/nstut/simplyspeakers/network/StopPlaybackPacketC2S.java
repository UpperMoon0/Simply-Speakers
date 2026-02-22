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

public class StopPlaybackPacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StopPlaybackPacketC2S> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "stop_playback"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, StopPlaybackPacketC2S> STREAM_CODEC = 
        StreamCodec.of(StopPlaybackPacketC2S::encode, StopPlaybackPacketC2S::decode);

    private final BlockPos blockPos;

    public StopPlaybackPacketC2S(BlockPos blockPos) {
        this.blockPos = blockPos;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, StopPlaybackPacketC2S packet) {
        buffer.writeBlockPos(packet.blockPos);
    }

    public static StopPlaybackPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new StopPlaybackPacketC2S(buffer.readBlockPos());
    }

    public static void handle(StopPlaybackPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            ServerLevel level = player.serverLevel();
            if (level.getBlockEntity(packet.blockPos) instanceof SpeakerBlockEntity speaker) {
                speaker.stopAudio();
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
