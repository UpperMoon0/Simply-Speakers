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

    public SyncConfigPacketS2C(int speakerRange, boolean disableUpload, int maxUploadSize) {
        this.speakerRange = speakerRange;
        this.disableUpload = disableUpload;
        this.maxUploadSize = maxUploadSize;
    }

    public SyncConfigPacketS2C(FriendlyByteBuf buf) {
        this.speakerRange = buf.readInt();
        this.disableUpload = buf.readBoolean();
        this.maxUploadSize = buf.readInt();
    }

    public static void encode(SyncConfigPacketS2C pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.speakerRange);
        buf.writeBoolean(pkt.disableUpload);
        buf.writeInt(pkt.maxUploadSize);
    }

    public static void handle(SyncConfigPacketS2C pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        context.queue(() -> {
            SimplySpeakers.LOGGER.info("Received server config: speakerRange={}, disableUpload={}, maxUploadSize={}",
                    pkt.speakerRange, pkt.disableUpload, pkt.maxUploadSize);
            Config.applyServerConfig(pkt.speakerRange, pkt.disableUpload, pkt.maxUploadSize);
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
}
