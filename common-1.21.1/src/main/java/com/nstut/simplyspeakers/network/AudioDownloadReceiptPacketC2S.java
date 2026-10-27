package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class AudioDownloadReceiptPacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AudioDownloadReceiptPacketC2S> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "audio_download_receipt"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, AudioDownloadReceiptPacketC2S> STREAM_CODEC = 
        StreamCodec.of(AudioDownloadReceiptPacketC2S::encode, AudioDownloadReceiptPacketC2S::decode);

    private final long committedBytes;
    private final java.util.UUID transferId;

    public AudioDownloadReceiptPacketC2S(long committedBytes, java.util.UUID transferId) {
        this.committedBytes = committedBytes;
        this.transferId = transferId;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, AudioDownloadReceiptPacketC2S packet) {
        buffer.writeLong(packet.committedBytes);
        buffer.writeUUID(packet.transferId);
    }

    public static AudioDownloadReceiptPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new AudioDownloadReceiptPacketC2S(buffer.readLong(), buffer.readUUID());
    }

    public static void handle(AudioDownloadReceiptPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            com.nstut.packetweave.api.OutboundTransfers.shared().acknowledge(player, packet.transferId, packet.committedBytes);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
