package com.nstut.simplyspeakers.speakers;

import com.nstut.simplyspeakers.RedstoneMode;
import com.nstut.simplyspeakers.SpeakerAccess;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.api.SpeakerEvents;
import com.nstut.simplyspeakers.audio.AudioFileManager;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.network.PacketRegistries;
import com.nstut.simplyspeakers.network.PlaylistControlPacketC2S;
import com.nstut.simplyspeakers.network.PlaylistSyncPacketS2C;
import com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S;
import com.nstut.simplyspeakers.network.SpeakerStateUpdatePacketS2C;
import com.nstut.simplyspeakers.playlist.Playlist;
import com.nstut.simplyspeakers.playlist.PlaylistTrack;
import com.nstut.simplyspeakers.playlist.RepeatMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Authoritative server-side transport and state mutation service.
 * All GUI packets, commands, Java API, CC:Tweaked peripherals, and redstone triggers
 * route through this single control service.
 */
public final class ServerSpeakerControlService {

    private ServerSpeakerControlService() {
    }

    public static String resolveFullStateKey(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        String stateKey = ServerSpeakerRegistry.getStateKey(level, pos);
        if (stateKey == null || stateKey.isEmpty()) return null;
        return ServerSpeakerRegistry.getRegistryKey(level, stateKey);
    }

    public static String resolveFullStateKeyByNetwork(Level level, String networkNameOrId) {
        if (level == null || networkNameOrId == null || networkNameOrId.trim().isEmpty()) return null;
        String trimmed = networkNameOrId.trim();
        String dimension = ServerSpeakerRegistry.getDimension(level);

        // 1. Direct net key
        if (trimmed.startsWith("net_")) {
            return dimension + "/" + trimmed;
        }

        // 2. Network ID without prefix
        String netKey = dimension + "/net_" + trimmed;
        if (ServerSpeakerRegistry.getSpeakerStateByFullKey(netKey) != null) {
            return netKey;
        }

        // 3. Search by human network name
        for (var entry : ServerSpeakerRegistry.getAllSpeakerStates().entrySet()) {
            if (entry.getKey().startsWith(dimension + "/")) {
                SpeakerState s = entry.getValue();
                if (s != null && trimmed.equalsIgnoreCase(s.getNetworkName())) {
                    return entry.getKey();
                }
            }
        }
        return netKey;
    }

    public static void play(MinecraftServer server, ServerLevel level, String fullStateKey) {
        if (fullStateKey == null) return;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return;

        if (state.isPlaying() && state.isPaused()) {
            state.resumeAt(level != null ? level.getGameTime() : 0);
            broadcastStateUpdate(level, fullStateKey, state, "play");
            ServerPlaybackManager.resyncState(server, level, fullStateKey);
            SpeakerEvents.fire(SpeakerEvents.Type.RESUMED, fullStateKey, state.getNetworkName(), state.getAudioId());
        } else {
            state.startPlaybackAt(level != null ? level.getGameTime() : 0, 0.0f);
            broadcastStateUpdate(level, fullStateKey, state, "play");
            ServerPlaybackManager.resyncState(server, level, fullStateKey);
            SpeakerEvents.fire(SpeakerEvents.Type.STARTED, fullStateKey, state.getNetworkName(), state.getAudioId());
        }
        ServerSpeakerRegistry.saveRegistry();
    }

    public static void pause(MinecraftServer server, ServerLevel level, String fullStateKey) {
        if (fullStateKey == null) return;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return;

        if (state.isPlaying() && !state.isPaused()) {
            state.pauseAt(level != null ? level.getGameTime() : 0);
            broadcastStateUpdate(level, fullStateKey, state, "pause");
            ServerPlaybackManager.resyncState(server, level, fullStateKey);
            SpeakerEvents.fire(SpeakerEvents.Type.PAUSED, fullStateKey, state.getNetworkName(), state.getAudioId());
            ServerSpeakerRegistry.saveRegistry();
        }
    }

    public static void togglePause(MinecraftServer server, ServerLevel level, String fullStateKey) {
        if (fullStateKey == null) return;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return;

        if (!state.isPlaying()) {
            play(server, level, fullStateKey);
        } else if (state.isPaused()) {
            play(server, level, fullStateKey);
        } else {
            pause(server, level, fullStateKey);
        }
    }

    public static void stop(MinecraftServer server, ServerLevel level, String fullStateKey) {
        if (fullStateKey == null) return;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return;

        if (state.isPlaying() || state.isPaused()) {
            state.stopPlayback();
            broadcastStateUpdate(level, fullStateKey, state, "stop");
            ServerPlaybackManager.resyncState(server, level, fullStateKey);
            SpeakerEvents.fire(SpeakerEvents.Type.STOPPED, fullStateKey, state.getNetworkName(), state.getAudioId());
            ServerSpeakerRegistry.saveRegistry();
        }
    }

    public static void restart(MinecraftServer server, ServerLevel level, String fullStateKey) {
        if (fullStateKey == null) return;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return;

        state.startPlaybackAt(level != null ? level.getGameTime() : 0, 0.0f);
        broadcastStateUpdate(level, fullStateKey, state, "play");
        ServerPlaybackManager.resyncState(server, level, fullStateKey);
        SpeakerEvents.fire(SpeakerEvents.Type.STARTED, fullStateKey, state.getNetworkName(), state.getAudioId());
        ServerSpeakerRegistry.saveRegistry();
    }

    public static void seek(MinecraftServer server, ServerLevel level, String fullStateKey, float seconds) {
        if (fullStateKey == null) return;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return;

        float duration = 0.0f;
        AudioFileManager afm = SimplySpeakers.getAudioFileManager();
        if (afm != null) {
            AudioFileMetadata meta = afm.getManifest().get(state.getAudioId());
            if (meta != null) duration = meta.getDurationSeconds();
        }
        state.seekTo(seconds, level != null ? level.getGameTime() : 0, duration);
        broadcastStateUpdate(level, fullStateKey, state, state.isPaused() ? "pause" : "play");
        ServerPlaybackManager.resyncState(server, level, fullStateKey);
        ServerSpeakerRegistry.saveRegistry();
    }

    public static void next(MinecraftServer server, ServerLevel level, String fullStateKey) {
        if (fullStateKey == null) return;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return;

        if (state.hasPlaylist()) {
            Playlist.Advance adv = state.getPlaylist().next();
            if (adv.hasTrack()) {
                state.setAudioId(adv.track().getAudioId());
                state.setAudioFilename(adv.track().getFilename());
                state.startPlaybackAt(level != null ? level.getGameTime() : 0, 0.0f);
                broadcastPlaylistSync(level, fullStateKey, state);
                broadcastStateUpdate(level, fullStateKey, state, "play");
                ServerPlaybackManager.resyncState(server, level, fullStateKey);
                SpeakerEvents.fire(SpeakerEvents.Type.TRACK_CHANGED, fullStateKey, state.getNetworkName(), state.getAudioId());
            } else {
                state.stopPlayback();
                broadcastPlaylistSync(level, fullStateKey, state);
                broadcastStateUpdate(level, fullStateKey, state, "stop");
                ServerPlaybackManager.resyncState(server, level, fullStateKey);
                SpeakerEvents.fire(SpeakerEvents.Type.FINISHED, fullStateKey, state.getNetworkName(), state.getAudioId());
            }
        }
        ServerSpeakerRegistry.saveRegistry();
    }

    public static void previous(MinecraftServer server, ServerLevel level, String fullStateKey) {
        if (fullStateKey == null) return;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return;

        if (state.hasPlaylist()) {
            Playlist.Advance adv = state.getPlaylist().previous();
            if (adv.hasTrack()) {
                state.setAudioId(adv.track().getAudioId());
                state.setAudioFilename(adv.track().getFilename());
                state.startPlaybackAt(level != null ? level.getGameTime() : 0, 0.0f);
                broadcastPlaylistSync(level, fullStateKey, state);
                broadcastStateUpdate(level, fullStateKey, state, "play");
                ServerPlaybackManager.resyncState(server, level, fullStateKey);
                SpeakerEvents.fire(SpeakerEvents.Type.TRACK_CHANGED, fullStateKey, state.getNetworkName(), state.getAudioId());
            } else {
                state.stopPlayback();
                broadcastPlaylistSync(level, fullStateKey, state);
                broadcastStateUpdate(level, fullStateKey, state, "stop");
                ServerPlaybackManager.resyncState(server, level, fullStateKey);
                SpeakerEvents.fire(SpeakerEvents.Type.FINISHED, fullStateKey, state.getNetworkName(), state.getAudioId());
            }
        }
        ServerSpeakerRegistry.saveRegistry();
    }

    public static boolean selectAudio(MinecraftServer server, ServerLevel level, String fullStateKey, String audioId, String filename) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;

        state.setAudioId(audioId != null ? audioId : "");
        state.setAudioFilename(filename != null ? filename : "");
        if (state.isPlaying()) {
            state.startPlaybackAt(level != null ? level.getGameTime() : 0, 0.0f);
        }
        broadcastStateUpdate(level, fullStateKey, state, state.isPlaying() ? (state.isPaused() ? "pause" : "play") : "update");
        if (state.isPlaying() && !state.isPaused()) {
            ServerPlaybackManager.resyncState(server, level, fullStateKey);
        }
        SpeakerEvents.fire(SpeakerEvents.Type.TRACK_CHANGED, fullStateKey, state.getNetworkName(), state.getAudioId());
        ServerSpeakerRegistry.saveRegistry();
        return true;
    }

    public static boolean playlistControl(MinecraftServer server, ServerLevel level, String fullStateKey, byte op, int index, boolean flag, String audioId, String filename) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;

        Playlist playlist = state.getPlaylist();
        boolean trackChanged = false;

        switch (op) {
            case PlaylistControlPacketC2S.OP_ADD -> {
                if (audioId != null && !audioId.isEmpty()) {
                    playlist.add(audioId, filename != null ? filename : "");
                }
            }
            case PlaylistControlPacketC2S.OP_REMOVE_AUDIO -> {
                if (audioId != null && !audioId.isEmpty()) {
                    playlist.removeByAudioId(audioId);
                }
            }
            case PlaylistControlPacketC2S.OP_MOVE_UP -> {
                playlist.moveUp(index);
            }
            case PlaylistControlPacketC2S.OP_MOVE_DOWN -> {
                playlist.moveDown(index);
            }
            case PlaylistControlPacketC2S.OP_CLEAR -> {
                playlist.clear();
            }
            case PlaylistControlPacketC2S.OP_SET_SHUFFLE -> {
                playlist.setShuffle(flag);
            }
            case PlaylistControlPacketC2S.OP_SET_REPEAT -> {
                playlist.setRepeatMode(RepeatMode.values()[Math.max(0, Math.min(RepeatMode.values().length - 1, index))]);
            }
            case PlaylistControlPacketC2S.OP_SELECT_INDEX -> {
                PlaylistTrack selected = playlist.selectIndex(index);
                if (selected != null) {
                    state.setAudioId(selected.getAudioId());
                    state.setAudioFilename(selected.getFilename());
                    trackChanged = true;
                    if (flag) { // autoplay
                        state.startPlaybackAt(level != null ? level.getGameTime() : 0, 0.0f);
                    }
                }
            }
            case PlaylistControlPacketC2S.OP_QUEUE_NEXT -> {
                if (audioId != null && !audioId.isEmpty()) {
                    playlist.queueNext(audioId);
                }
            }
        }

        broadcastPlaylistSync(level, fullStateKey, state);
        if (trackChanged) {
            broadcastStateUpdate(level, fullStateKey, state, "play");
            ServerPlaybackManager.resyncState(server, level, fullStateKey);
            SpeakerEvents.fire(SpeakerEvents.Type.TRACK_CHANGED, fullStateKey, state.getNetworkName(), state.getAudioId());
        }
        ServerSpeakerRegistry.saveRegistry();
        return true;
    }

    public static boolean policyControl(MinecraftServer server, ServerLevel level, String fullStateKey, byte op, String strValue, int intValue, float floatValue, UUID playerUuid) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;

        switch (op) {
            case SpeakerPolicyPacketC2S.OP_NETWORK_NAME -> {
                state.setNetworkName(strValue != null ? strValue.trim() : "");
            }
            case SpeakerPolicyPacketC2S.OP_ACCESS_MODE -> {
                state.setAccessMode(SpeakerAccess.fromIndex(intValue));
            }
            case SpeakerPolicyPacketC2S.OP_TRUST_CHANGE -> {
                if (playerUuid != null) {
                    if (intValue > 0) state.trustPlayer(playerUuid);
                    else state.distrustPlayer(playerUuid);
                }
            }
            case SpeakerPolicyPacketC2S.OP_REDSTONE_MODE -> {
                state.setRedstoneMode(RedstoneMode.fromIndex(intValue));
            }
            case SpeakerPolicyPacketC2S.OP_DIRECTIONALITY -> {
                state.setDirectionality(Math.max(0.0f, Math.min(1.0f, floatValue)));
            }
            case SpeakerPolicyPacketC2S.OP_CONE_ANGLE -> {
                state.setConeAngleDegrees(Math.max(5, Math.min(350, intValue)));
            }
            case SpeakerPolicyPacketC2S.OP_REAR_ATTENUATION -> {
                state.setRearAttenuation(Math.max(0.0f, Math.min(1.0f, floatValue)));
            }
        }
        broadcastStateUpdate(level, fullStateKey, state, "update");
        ServerSpeakerRegistry.saveRegistry();
        return true;
    }

    public static boolean applyTransport(MinecraftServer server, ServerLevel level, String fullStateKey, byte action, float seekSeconds) {
        switch (action) {
            case 0 -> play(server, level, fullStateKey);        // ACTION_PLAY
            case 1 -> pause(server, level, fullStateKey);       // ACTION_PAUSE
            case 2 -> togglePause(server, level, fullStateKey); // ACTION_TOGGLE
            case 3 -> stop(server, level, fullStateKey);        // ACTION_STOP
            case 4 -> restart(server, level, fullStateKey);     // ACTION_RESTART
            case 5 -> next(server, level, fullStateKey);        // ACTION_NEXT
            case 6 -> previous(server, level, fullStateKey);    // ACTION_PREVIOUS
            case 7 -> seek(server, level, fullStateKey, seekSeconds); // ACTION_SEEK
            default -> { return false; }
        }
        return true;
    }

    public static boolean setVolume(MinecraftServer server, ServerLevel level, String fullStateKey, float volume) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;
        state.setMaxVolume(Math.max(0.0f, Math.min(1.0f, volume)));
        broadcastStateUpdate(level, fullStateKey, state, "update");
        ServerPlaybackManager.resyncState(server, level, fullStateKey);
        ServerSpeakerRegistry.saveRegistry();
        return true;
    }

    public static boolean setRange(MinecraftServer server, ServerLevel level, String fullStateKey, int range) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;
        state.setMaxRange(Math.max(1, range));
        broadcastStateUpdate(level, fullStateKey, state, "update");
        ServerPlaybackManager.resyncState(server, level, fullStateKey);
        ServerSpeakerRegistry.saveRegistry();
        return true;
    }

    public static boolean setLooping(MinecraftServer server, ServerLevel level, String fullStateKey, boolean looping) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;
        state.setLooping(looping);
        broadcastStateUpdate(level, fullStateKey, state, "update");
        ServerPlaybackManager.resyncState(server, level, fullStateKey);
        ServerSpeakerRegistry.saveRegistry();
        return true;
    }

    public static void selectPlaylistSlot(MinecraftServer server, ServerLevel level, String fullStateKey, int slotIndex) {
        if (fullStateKey == null) return;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return;
        if (state.hasPlaylist() && slotIndex >= 0 && slotIndex < state.getPlaylist().size()) {
            playlistControl(server, level, fullStateKey, PlaylistControlPacketC2S.OP_SELECT_INDEX, slotIndex, true, "", "");
        }
    }

    private static void broadcastStateUpdate(ServerLevel level, String fullStateKey, SpeakerState state, String action) {
        if (level == null || fullStateKey == null || state == null) return;
        String speakerId = fullStateKey.contains("/net_") ? fullStateKey.substring(fullStateKey.indexOf("/net_") + 5) : "";
        SpeakerStateUpdatePacketS2C packet = new SpeakerStateUpdatePacketS2C(
                BlockPos.ZERO,
                speakerId,
                action,
                state.getAudioId(),
                state.getAudioFilename(),
                state.getPlaybackStartTick(),
                state.isLooping()
        );
        PacketRegistries.CHANNEL.sendToPlayers(level.players(), packet);
    }

    private static void broadcastPlaylistSync(ServerLevel level, String fullStateKey, SpeakerState state) {
        if (level == null || fullStateKey == null || state == null || !state.hasPlaylist()) return;
        Playlist pl = state.getPlaylist();
        List<String> audioIds = new ArrayList<>();
        List<String> filenames = new ArrayList<>();
        for (PlaylistTrack track : pl.getTracks()) {
            audioIds.add(track.getAudioId());
            filenames.add(track.getFilename());
        }
        int playingIndex = state.isPlaying() ? pl.getCurrentIndex() : -1;
        PlaylistSyncPacketS2C packet = new PlaylistSyncPacketS2C(
                BlockPos.ZERO,
                audioIds,
                filenames,
                pl.getCurrentIndex(),
                pl.isShuffle(),
                pl.getRepeatMode().ordinal(),
                playingIndex,
                state.isPaused()
        );
        PacketRegistries.CHANNEL.sendToPlayers(level.players(), packet);
    }
}

