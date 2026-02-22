package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class SetSpeakerIdPacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetSpeakerIdPacketC2S> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "set_speaker_id"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, SetSpeakerIdPacketC2S> STREAM_CODEC = 
        StreamCodec.of(SetSpeakerIdPacketC2S::encode, SetSpeakerIdPacketC2S::decode);

    private final BlockPos blockPos;
    private final String speakerId;

    public SetSpeakerIdPacketC2S(BlockPos blockPos, String speakerId) {
        this.blockPos = blockPos;
        this.speakerId = speakerId;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, SetSpeakerIdPacketC2S packet) {
        buffer.writeBlockPos(packet.blockPos);
        buffer.writeUtf(packet.speakerId);
    }

    public static SetSpeakerIdPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new SetSpeakerIdPacketC2S(buffer.readBlockPos(), buffer.readUtf());
    }

    public static void handle(SetSpeakerIdPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            ServerLevel level = player.serverLevel();
            // Handle both speaker block entity types
            if (level.getBlockEntity(packet.blockPos) instanceof SpeakerBlockEntity speaker) {
                speaker.setSpeakerId(packet.speakerId);
            } else if (level.getBlockEntity(packet.blockPos) instanceof ProxySpeakerBlockEntity proxySpeaker) {
                proxySpeaker.setSpeakerId(packet.speakerId);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
