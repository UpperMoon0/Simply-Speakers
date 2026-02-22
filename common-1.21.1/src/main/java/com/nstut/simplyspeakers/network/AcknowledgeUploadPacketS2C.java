package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public class AcknowledgeUploadPacketS2C implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AcknowledgeUploadPacketS2C> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "acknowledge_upload"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, AcknowledgeUploadPacketS2C> STREAM_CODEC = 
        StreamCodec.of(AcknowledgeUploadPacketS2C::encode, AcknowledgeUploadPacketS2C::decode);

    private final UUID transactionId;
    private final boolean success;
    private final Component message;
    private final BlockPos blockPos;

    public AcknowledgeUploadPacketS2C(UUID transactionId, boolean success, Component message, BlockPos blockPos) {
        this.transactionId = transactionId;
        this.success = success;
        this.message = message;
        this.blockPos = blockPos;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, AcknowledgeUploadPacketS2C packet) {
        buffer.writeUUID(packet.transactionId);
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.message.getString());
        buffer.writeBlockPos(packet.blockPos);
    }

    public static AcknowledgeUploadPacketS2C decode(RegistryFriendlyByteBuf buffer) {
        return new AcknowledgeUploadPacketS2C(
            buffer.readUUID(),
            buffer.readBoolean(),
            Component.literal(buffer.readUtf()),
            buffer.readBlockPos()
        );
    }

    public static void handle(AcknowledgeUploadPacketS2C packet, NetworkManager.PacketContext context) {
        context.queue(() -> {
            ClientAudioPlayer.handleUploadAcknowledgement(packet.transactionId, packet.success, packet.message, packet.blockPos);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
