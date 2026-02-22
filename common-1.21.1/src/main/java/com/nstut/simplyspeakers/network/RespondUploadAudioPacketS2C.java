package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public class RespondUploadAudioPacketS2C implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RespondUploadAudioPacketS2C> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "respond_upload_audio"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, RespondUploadAudioPacketS2C> STREAM_CODEC = 
        StreamCodec.of(RespondUploadAudioPacketS2C::encode, RespondUploadAudioPacketS2C::decode);

    private final UUID transactionId;
    private final boolean allowed;
    private final int maxChunkSize;
    private final Component message;

    public RespondUploadAudioPacketS2C(UUID transactionId, boolean allowed, int maxChunkSize, Component message) {
        this.transactionId = transactionId;
        this.allowed = allowed;
        this.maxChunkSize = maxChunkSize;
        this.message = message;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, RespondUploadAudioPacketS2C packet) {
        buffer.writeUUID(packet.transactionId);
        buffer.writeBoolean(packet.allowed);
        buffer.writeInt(packet.maxChunkSize);
        buffer.writeUtf(packet.message.getString());
    }

    public static RespondUploadAudioPacketS2C decode(RegistryFriendlyByteBuf buffer) {
        return new RespondUploadAudioPacketS2C(
            buffer.readUUID(),
            buffer.readBoolean(),
            buffer.readInt(),
            Component.literal(buffer.readUtf())
        );
    }

    public static void handle(RespondUploadAudioPacketS2C packet, NetworkManager.PacketContext context) {
        context.queue(() -> {
            ClientAudioPlayer.handleUploadResponse(packet.transactionId, packet.allowed, packet.maxChunkSize, packet.message);
        });
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    public Component getMessage() {
        return message;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
