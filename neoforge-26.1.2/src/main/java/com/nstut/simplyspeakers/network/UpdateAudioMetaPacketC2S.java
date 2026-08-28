package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Client -> server library organization: display name, category, tags for one audio entry. */
public class UpdateAudioMetaPacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateAudioMetaPacketC2S> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "update_audio_meta"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateAudioMetaPacketC2S> STREAM_CODEC =
        StreamCodec.of(UpdateAudioMetaPacketC2S::encode, UpdateAudioMetaPacketC2S::decode);

    private final String audioId;
    private final String displayName;
    private final String category;
    private final String tagsCsv;

    public UpdateAudioMetaPacketC2S(String audioId, String displayName, String category, String tagsCsv) {
        this.audioId = audioId;
        this.displayName = displayName != null ? displayName.trim() : "";
        this.category = category != null ? category.trim() : "";
        this.tagsCsv = tagsCsv != null ? tagsCsv : "";
    }

    public static void encode(RegistryFriendlyByteBuf buffer, UpdateAudioMetaPacketC2S packet) {
        buffer.writeUtf(packet.audioId, 64);
        buffer.writeUtf(packet.displayName, 128);
        buffer.writeUtf(packet.category, 64);
        buffer.writeUtf(packet.tagsCsv, 256);
    }

    public static UpdateAudioMetaPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new UpdateAudioMetaPacketC2S(buffer.readUtf(64), buffer.readUtf(128),
                buffer.readUtf(64), buffer.readUtf(256));
    }

    public static void handle(UpdateAudioMetaPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            var fileManager = SimplySpeakers.getAudioFileManager();
            if (fileManager == null || packet.audioId.isEmpty()) {
                return;
            }
            AudioFileMetadata meta = fileManager.getManifest().get(packet.audioId);
            boolean isOp = player.level().getServer() != null && player.level().getServer().getPlayerList().isOp(player.nameAndId());
            String owner = meta != null ? meta.getOwnerUUID() : null;
            if (!isOp && (owner == null || !owner.equals(player.getUUID().toString()))) {
                return;
            }
            List<String> tags = new ArrayList<>();
            for (String tag : packet.tagsCsv.split(",")) {
                if (!tag.isBlank()) tags.add(tag.trim());
            }
            fileManager.updateAudioMetadata(packet.audioId,
                    meta.withDisplayName(packet.displayName)
                            .withCategory(packet.category)
                            .withTags(tags));
            NetworkManager.sendToPlayer(player, new SendAudioListPacketS2C(
                    fileManager.getAudioListForPlayer(player.getUUID().toString())));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
