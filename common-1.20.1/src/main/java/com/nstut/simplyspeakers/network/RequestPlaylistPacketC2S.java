package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.playlist.Playlist;
import com.nstut.simplyspeakers.playlist.PlaylistTrack;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class RequestPlaylistPacketC2S {

    private final BlockPos pos;

    public RequestPlaylistPacketC2S(BlockPos pos) {
        this.pos = pos;
    }

    public RequestPlaylistPacketC2S(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }

    public static void handle(RequestPlaylistPacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (!SpeakerPacketSecurity.canModify(player, pkt.pos)) return;
            if (!(SpeakerPacketSecurity.resolveTarget(player, pkt.pos) instanceof SpeakerBlockEntity speaker)) return;
            if(!SpeakerPacketSecurity.canControlSpeaker(player,pkt.pos)
                    && !com.nstut.simplyspeakers.SpeakerPermissions.canManage(speaker.getSpeakerState(),player.getUUID(),player.hasPermissions(2)))return;
            sendSnapshot(player, speaker, pkt.pos);
        });
    }

    private static void sendSnapshot(ServerPlayer player, SpeakerBlockEntity speaker, BlockPos pos) {
        SpeakerState state = speaker.getSpeakerState();
        if (state == null) return;
        PlaylistSyncPacketS2C.sendToPlayer(player, PlaylistSyncPacketS2C.fromState(pos, speaker.getFullStateKey(), state, player.serverLevel().getGameTime(), com.nstut.simplyspeakers.SimplySpeakers.getAudioFileManager()));
    }
}