package com.nstut.simplyspeakers.speakers;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.audio.AudioFileManager;
import com.nstut.simplyspeakers.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Loader I/O boundary. Playback orchestration and registry state stay in their real services. */
final class ServerPlaybackEnvironment {
    private ServerPlaybackEnvironment() {}
    static AudioFileManager audioFiles() { return SimplySpeakers.getAudioFileManager(); }
    static Vec3 emitterPosition(ServerLevel level, BlockPos pos) { return com.nstut.simplyspeakers.compat.sable.SpeakerSpatialResolver.resolveLogical(level, pos); }
    static Vec3 listenerPosition(ServerLevel level, ServerPlayer player) { return com.nstut.simplyspeakers.compat.sable.SpeakerSpatialResolver.resolveLogical(level, player.position()); }
    static void sendPlay(ServerPlayer player, PlayAudioPacketS2C packet) { dev.architectury.networking.NetworkManager.sendToPlayer(player, packet); }
    static void sendStop(ServerPlayer player, StopAudioPacketS2C packet) { dev.architectury.networking.NetworkManager.sendToPlayer(player, packet); }
    static void sendState(ServerLevel level, SpeakerStateUpdatePacketS2C packet) { dev.architectury.networking.NetworkManager.sendToPlayers(level.players(), packet); }
    static void sendPlaylist(ServerLevel level, PlaylistSyncPacketS2C packet) { dev.architectury.networking.NetworkManager.sendToPlayers(level.players(), packet); }
}
