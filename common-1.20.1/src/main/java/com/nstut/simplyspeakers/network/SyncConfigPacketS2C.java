package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

public class SyncConfigPacketS2C {
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

    public SyncConfigPacketS2C(FriendlyByteBuf buf) {
        this.speakerRange = buf.readInt();
        this.disableUpload = buf.readBoolean();
        this.maxUploadSize = buf.readInt();
        this.allowRemoteStreams = buf.readBoolean();
    }

    public static void encode(SyncConfigPacketS2C pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.speakerRange);
        buf.writeBoolean(pkt.disableUpload);
        buf.writeInt(pkt.maxUploadSize);
        buf.writeBoolean(pkt.allowRemoteStreams);
    }

    public static void handle(SyncConfigPacketS2C pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        context.queue(() -> {
            SimplySpeakers.LOGGER.info("Received server config: speakerRange={}, disableUpload={}, maxUploadSize={}, allowRemoteStreams={}",
                    pkt.speakerRange, pkt.disableUpload, pkt.maxUploadSize, pkt.allowRemoteStreams);
            Config.applyServerConfig(pkt.speakerRange, pkt.disableUpload, pkt.maxUploadSize, pkt.allowRemoteStreams);
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
}
