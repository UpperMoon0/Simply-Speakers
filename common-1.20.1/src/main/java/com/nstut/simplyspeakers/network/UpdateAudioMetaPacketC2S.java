package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.audio.AudioFileManager;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class UpdateAudioMetaPacketC2S {

    private final String audioId;
    private final String displayName;
    private final String category;
    private final String tagsCsv;

    public UpdateAudioMetaPacketC2S(String audioId, String displayName, String category, String tagsCsv) {
        this.audioId = audioId != null ? audioId : "";
        this.displayName = displayName != null ? displayName.trim() : "";
        this.category = category != null ? category.trim() : "";
        this.tagsCsv = tagsCsv != null ? tagsCsv : "";
    }

    public UpdateAudioMetaPacketC2S(FriendlyByteBuf buf) {
        this.audioId = buf.readUtf(64);
        this.displayName = buf.readUtf(128);
        this.category = buf.readUtf(64);
        this.tagsCsv = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.audioId, 64);
        buf.writeUtf(this.displayName, 128);
        buf.writeUtf(this.category, 64);
        buf.writeUtf(this.tagsCsv, 256);
    }

    public static void handle(UpdateAudioMetaPacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            AudioFileManager fileManager = SimplySpeakers.getAudioFileManager();
            if (fileManager == null || pkt.audioId.isEmpty()) return;
            AudioFileMetadata meta = fileManager.getManifest().get(pkt.audioId);
            boolean isOp = player.hasPermissions(2);
            String owner = meta != null ? meta.getOwnerUUID() : null;
            if (!isOp && (owner == null || !owner.equals(player.getUUID().toString()))) return;
            List<String> tags = new ArrayList<>();
            for (String tag : pkt.tagsCsv.split(",")) {
                if (!tag.isBlank()) tags.add(tag.trim());
            }
            fileManager.updateAudioMetadata(pkt.audioId,
                    meta.withDisplayName(pkt.displayName)
                            .withCategory(pkt.category)
                            .withTags(tags));
            PacketRegistries.CHANNEL.sendToPlayer(player,
                    new SendAudioListPacketS2C(fileManager.getAudioListForPlayer(player.getUUID().toString())));
        });
    }
}
