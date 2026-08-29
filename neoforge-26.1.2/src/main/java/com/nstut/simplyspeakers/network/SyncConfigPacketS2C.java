package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public class SyncConfigPacketS2C implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncConfigPacketS2C> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "sync_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncConfigPacketS2C> STREAM_CODEC =
            StreamCodec.of(SyncConfigPacketS2C::encode, SyncConfigPacketS2C::decode);

    private final int speakerRange;
    private final boolean disableUpload;
    private final int maxUploadSize;
    private final boolean allowRemoteStreams;

    public SyncConfigPacketS2C(int speakerRange, boolean disableUpload, int maxUploadSize) {
        this(speakerRange, disableUpload, maxUploadSize, Config.allowRemoteStreams);
    }

    public SyncConfigPacketS2C(int speakerRange, boolean disableUpload, int maxUploadSize, boolean allowRemoteStreams) {
        this.speakerRange = speakerRange;
        this.disableUpload = disableUpload;
        this.maxUploadSize = maxUploadSize;
        this.allowRemoteStreams = allowRemoteStreams;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, SyncConfigPacketS2C packet) {
        buffer.writeInt(packet.speakerRange);
        buffer.writeBoolean(packet.disableUpload);
        buffer.writeInt(packet.maxUploadSize);
        buffer.writeBoolean(packet.allowRemoteStreams);
    }

    public static SyncConfigPacketS2C decode(RegistryFriendlyByteBuf buffer) {
        return new SyncConfigPacketS2C(
                buffer.readInt(),
                buffer.readBoolean(),
                buffer.readInt(),
                buffer.readBoolean()
        );
    }

    public static void handle(SyncConfigPacketS2C packet, NetworkManager.PacketContext context) {
        context.queue(() -> {
            SimplySpeakers.LOGGER.info("Received server config: speakerRange={}, disableUpload={}, maxUploadSize={}, allowRemoteStreams={}",
                    packet.speakerRange, packet.disableUpload, packet.maxUploadSize, packet.allowRemoteStreams);
            Config.applyServerConfig(packet.speakerRange, packet.disableUpload, packet.maxUploadSize, packet.allowRemoteStreams);
        });
    }

    public int getSpeakerRange() {
        return speakerRange;
    }

    public boolean isDisableUpload() {
        return disableUpload;
    }

    public int getMaxUploadSize() {
        return maxUploadSize;
    }

    public boolean isAllowRemoteStreams() {
        return allowRemoteStreams;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
