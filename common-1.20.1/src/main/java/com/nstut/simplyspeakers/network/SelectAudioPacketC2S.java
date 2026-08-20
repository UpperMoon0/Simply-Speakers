package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.audio.AudioFileManager;
import com.nstut.simplyspeakers.audio.AudioOwnership;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

public class SelectAudioPacketC2S {
    private final BlockPos blockPos;
    private final String audioId;

    public SelectAudioPacketC2S(BlockPos blockPos, String audioId) {
        this.blockPos = blockPos;
        this.audioId = audioId != null ? audioId : "";
    }

    public SelectAudioPacketC2S(BlockPos blockPos, String audioId, String ignoredFilename) {
        this(blockPos, audioId);
    }

    public SelectAudioPacketC2S(FriendlyByteBuf buf) {
        this.blockPos = buf.readBlockPos();
        this.audioId = buf.readUtf();
        if (buf.readableBytes() > 0) {
            buf.readUtf(); // consume legacy filename field if present
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
        buf.writeUtf(audioId);
    }

    public static void handle(SelectAudioPacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (!SpeakerPacketSecurity.canModify(player, pkt.blockPos)) {
                return;
            }

            ServerLevel level = player.serverLevel();
            if (level.getBlockEntity(pkt.blockPos) instanceof SpeakerBlockEntity speaker) {
                if (pkt.audioId.isEmpty()) {
                    speaker.setSelectedAudio("", "");
                    return;
                }

                AudioFileManager manager = SimplySpeakers.getAudioFileManager();
                if (manager != null) {
                    AudioFileMetadata meta = manager.getManifest().get(pkt.audioId);
                    if (meta != null && AudioOwnership.isOwnedBy(meta.getOwnerUUID(), player.getUUID().toString())) {
                        speaker.setSelectedAudio(meta.getUuid(), meta.getOriginalFilename());
                    }
                }
            }
        });
    }
}