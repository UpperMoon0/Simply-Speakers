package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.playlist.Playlist;
import com.nstut.simplyspeakers.playlist.PlaylistTrack;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Client -> server request for a speaker's current playlist snapshot. */
public class RequestPlaylistPacketC2S implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestPlaylistPacketC2S> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "request_playlist"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestPlaylistPacketC2S> STREAM_CODEC =
        StreamCodec.of(RequestPlaylistPacketC2S::encode, RequestPlaylistPacketC2S::decode);

    private final BlockPos pos;

    public RequestPlaylistPacketC2S(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, RequestPlaylistPacketC2S packet) {
        buffer.writeBlockPos(packet.pos);
    }

    public static RequestPlaylistPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new RequestPlaylistPacketC2S(buffer.readBlockPos());
    }

    public static void handle(RequestPlaylistPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (!SpeakerPacketSecurity.canModify(player, packet.pos)) return;
            if (!(SpeakerPacketSecurity.resolveTarget(player, packet.pos) instanceof SpeakerBlockEntity speaker)) return;
            if(!SpeakerPacketSecurity.canControlSpeaker(player,packet.pos)
                    && !com.nstut.simplyspeakers.SpeakerPermissions.canManage(speaker.getSpeakerState(),player.getUUID(),player.hasPermissions(2)))return;
            sendSnapshot(player, speaker, packet.pos);
        });
    }

    private static void sendSnapshot(ServerPlayer player, SpeakerBlockEntity speaker, BlockPos pos) {
        SpeakerState state = speaker.getSpeakerState();
        if (state == null) return;
        PlaylistSyncPacketS2C.sendToPlayer(player, PlaylistSyncPacketS2C.fromState(pos, speaker.getFullStateKey(), state, player.serverLevel().getGameTime(), com.nstut.simplyspeakers.SimplySpeakers.getAudioFileManager()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}