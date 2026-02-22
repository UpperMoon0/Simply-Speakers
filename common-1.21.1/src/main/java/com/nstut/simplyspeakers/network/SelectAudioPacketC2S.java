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

public class SelectAudioPacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SelectAudioPacketC2S> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "select_audio"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectAudioPacketC2S> STREAM_CODEC = 
        StreamCodec.of(SelectAudioPacketC2S::encode, SelectAudioPacketC2S::decode);

    private final BlockPos blockPos;
    private final String audioId;
    private final String filename;

    public SelectAudioPacketC2S(BlockPos blockPos, String audioId, String filename) {
        this.blockPos = blockPos;
        this.audioId = audioId;
        this.filename = filename;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, SelectAudioPacketC2S packet) {
        buffer.writeBlockPos(packet.blockPos);
        buffer.writeUtf(packet.audioId);
        buffer.writeUtf(packet.filename);
    }

    public static SelectAudioPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new SelectAudioPacketC2S(buffer.readBlockPos(), buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(SelectAudioPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            ServerLevel level = player.serverLevel();
            if (level.getBlockEntity(packet.blockPos) instanceof SpeakerBlockEntity speaker) {
                speaker.setSelectedAudio(packet.audioId, packet.filename);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
