package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class SelectAudioPacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SelectAudioPacketC2S> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "select_audio"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectAudioPacketC2S> STREAM_CODEC = 
        StreamCodec.of(SelectAudioPacketC2S::encode, SelectAudioPacketC2S::decode);

    private final BlockPos blockPos;
    private final String audioId;

    public SelectAudioPacketC2S(BlockPos blockPos, String audioId) {
        this.blockPos = blockPos;
        this.audioId = audioId != null ? audioId : "";
    }

    public SelectAudioPacketC2S(BlockPos blockPos, String audioId, String ignoredFilename) {
        this(blockPos, audioId);
    }

    public static void encode(RegistryFriendlyByteBuf buffer, SelectAudioPacketC2S packet) {
        buffer.writeBlockPos(packet.blockPos);
        buffer.writeUtf(packet.audioId);
    }

    public static SelectAudioPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        String id = buffer.readUtf();
        if (buffer.readableBytes() > 0) {
            buffer.readUtf(); // consume legacy filename if sent by older client
        }
        return new SelectAudioPacketC2S(pos, id);
    }

    public static void handle(SelectAudioPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (!SpeakerPacketSecurity.canControlSpeaker(player, packet.blockPos)) {
                return;
            }

            ServerLevel level = player.serverLevel();
            if (level.getBlockEntity(packet.blockPos) instanceof SpeakerBlockEntity speaker) {
                if (packet.audioId.isEmpty()) {
                    speaker.setSelectedAudio("", "");
                    return;
                }

                // Content-level authorization is shared with the playlist paths:
                // owned local entries resolve via the manifest (server-derived
                // filename), URL tracks via remote-streaming policy only.
                SpeakerPacketSecurity.AuthorizedTrack track =
                        SpeakerPacketSecurity.resolveAuthorizedTrack(player, packet.audioId);
                if (track != null) {
                    speaker.setSelectedAudio(track.audioId(), track.filename());
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
