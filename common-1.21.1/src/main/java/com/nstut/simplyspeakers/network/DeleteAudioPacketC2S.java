package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class DeleteAudioPacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DeleteAudioPacketC2S> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "delete_audio"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteAudioPacketC2S> STREAM_CODEC =
        StreamCodec.of(DeleteAudioPacketC2S::encode, DeleteAudioPacketC2S::decode);

    private final String audioId;

    public DeleteAudioPacketC2S(String audioId) {
        this.audioId = audioId;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, DeleteAudioPacketC2S packet) {
        buffer.writeUtf(packet.audioId);
    }

    public static DeleteAudioPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new DeleteAudioPacketC2S(buffer.readUtf());
    }

    public static void handle(DeleteAudioPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            SimplySpeakers.getAudioFileManager().deleteAudioFile(packet.audioId, player.getUUID().toString());
            List<AudioFileMetadata> audioList = SimplySpeakers.getAudioFileManager()
                .getAudioListForPlayer(player.getUUID().toString());
            NetworkManager.sendToPlayer(player, new SendAudioListPacketS2C(audioList));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
