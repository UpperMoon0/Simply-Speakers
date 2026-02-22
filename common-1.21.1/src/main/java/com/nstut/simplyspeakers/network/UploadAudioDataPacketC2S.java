package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class UploadAudioDataPacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UploadAudioDataPacketC2S> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "upload_audio_data"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, UploadAudioDataPacketC2S> STREAM_CODEC = 
        StreamCodec.of(UploadAudioDataPacketC2S::encode, UploadAudioDataPacketC2S::decode);

    private final UUID transactionId;
    private final byte[] data;

    public UploadAudioDataPacketC2S(UUID transactionId, byte[] data) {
        this.transactionId = transactionId;
        this.data = data;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, UploadAudioDataPacketC2S packet) {
        buffer.writeUUID(packet.transactionId);
        buffer.writeByteArray(packet.data);
    }

    public static UploadAudioDataPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new UploadAudioDataPacketC2S(buffer.readUUID(), buffer.readByteArray());
    }

    public static void handle(UploadAudioDataPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            // Check if uploads are disabled
            if (Config.disableUpload) {
                return;
            }

            SimplySpeakers.getAudioFileManager().handleUploadData(player, packet.transactionId, packet.data);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
