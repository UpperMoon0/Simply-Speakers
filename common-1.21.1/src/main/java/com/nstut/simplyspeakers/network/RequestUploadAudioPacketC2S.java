package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class RequestUploadAudioPacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestUploadAudioPacketC2S> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "request_upload_audio"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestUploadAudioPacketC2S> STREAM_CODEC = 
        StreamCodec.of(RequestUploadAudioPacketC2S::encode, RequestUploadAudioPacketC2S::decode);

    private final BlockPos blockPos;
    private final UUID transactionId;
    private final String fileName;
    private final long fileSize;

    public RequestUploadAudioPacketC2S(BlockPos blockPos, UUID transactionId, String fileName, long fileSize) {
        this.blockPos = blockPos;
        this.transactionId = transactionId;
        this.fileName = fileName;
        this.fileSize = fileSize;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, RequestUploadAudioPacketC2S packet) {
        buffer.writeBlockPos(packet.blockPos);
        buffer.writeUUID(packet.transactionId);
        buffer.writeUtf(packet.fileName);
        buffer.writeLong(packet.fileSize);
    }

    public static RequestUploadAudioPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new RequestUploadAudioPacketC2S(
            buffer.readBlockPos(),
            buffer.readUUID(),
            buffer.readUtf(),
            buffer.readLong()
        );
    }

    public static void handle(RequestUploadAudioPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            SimplySpeakers.LOGGER.info("Received upload request for file: " + packet.fileName + " with transaction ID: " + packet.transactionId);
            SimplySpeakers.getAudioFileManager().handleUploadRequest(player, packet.blockPos, packet.transactionId, packet.fileName, packet.fileSize);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
