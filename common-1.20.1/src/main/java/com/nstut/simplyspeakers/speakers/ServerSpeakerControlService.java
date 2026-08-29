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

    /**
     * Resolves the dimension-qualified full state key for a physical speaker position.
     * {@link ServerSpeakerRegistry#getFullStateKeyAt(Level, BlockPos)} already returns the
     * dimension-prefixed key, so it must not be run through {@code getRegistryKey} again.
     */
    public static String resolveFullStateKey(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        return ServerSpeakerRegistry.getFullStateKeyAt(level, pos);
    }

    /**
     * Resolves a network reference to a full state key. Accepts, in priority order:
     * an exact full key ("dim/net_x" or "dim/internal_uuid"), a raw net id ("net_x"),
     * a bare id ("x"), or a human network name ("Lobby"). Returns null when nothing matches.
     */
    public static String resolveFullStateKeyByNetwork(Level level, String networkOrFullKey) {
        if (level == null || networkOrFullKey == null || networkOrFullKey.trim().isEmpty()) return null;
        String trimmed = networkOrFullKey.trim();
        String dimension = ServerSpeakerRegistry.getDimension(level);

        // 0. Exact full key (dimension-qualified registry key)
        if (trimmed.contains("/") && ServerSpeakerRegistry.getSpeakerStateByFullKey(trimmed) != null) {
            return trimmed;
        }

        // 1. Direct net key
        if (trimmed.startsWith("net_")) {
            String fullKey = dimension + "/" + trimmed;
            return ServerSpeakerRegistry.getSpeakerStateByFullKey(fullKey) != null ? fullKey : null;
        }

        // 2. Network ID without prefix
        String netKey = dimension + "/net_" + trimmed;
        if (ServerSpeakerRegistry.getSpeakerStateByFullKey(netKey) != null) {
            return netKey;
        }

        // 3. Search by human network name (must be unique within this dimension).
        String matchedKey = null;
        boolean ambiguous = false;
        for (var entry : ServerSpeakerRegistry.getAllSpeakerStates().entrySet()) {
            if (entry.getKey().startsWith(dimension + "/")) {
                SpeakerState s = entry.getValue();
                if (s != null && trimmed.equalsIgnoreCase(s.getNetworkName())) {
                    if (matchedKey != null) {
                        ambiguous = true;
                        break;
                    }
                    matchedKey = entry.getKey();
                }
            }
        }
        if (!ambiguous && matchedKey != null) return matchedKey;
        return null;
    }

    /**
     * Starts or resumes playback. Idempotent by design: when the network is already
     * playing this is a no-op that never touches transport time — use
     * {@link #restart(MinecraftServer, ServerLevel, String)} to force a restart.
     * Linked speakers powering on mid-song therefore do not reset everyone's position.
     */
    public static boolean play(MinecraftServer server, ServerLevel level, String fullStateKey) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;
        if (!state.hasAudio()) return false;

        if (state.isPlaying() && !state.isPaused()) {
            // Already playing: keep transport position untouched (play != restart).
            return true;
        }
        long now = level != null ? level.getGameTime() : 0;
        if (state.isPlaying() && state.isPaused()) {
            state.resumeAt(now);
            broadcastStateUpdate(level, fullStateKey, state, "play");
            ServerPlaybackManager.resyncState(server, level, fullStateKey);
            SpeakerEvents.fire(SpeakerEvents.Type.RESUMED, fullStateKey, state.getNetworkName(), state.getAudioId());
        } else {
            state.startPlaybackAt(now, 0.0f);
            broadcastStateUpdate(level, fullStateKey, state, "play");
            ServerPlaybackManager.resyncState(server, level, fullStateKey);
            SpeakerEvents.fire(SpeakerEvents.Type.STARTED, fullStateKey, state.getNetworkName(), state.getAudioId());
        }
        ServerSpeakerRegistry.markDirty();
        return true;
    }

    public static boolean pause(MinecraftServer server, ServerLevel level, String fullStateKey) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;

        if (state.isPlaying() && !state.isPaused()) {
            state.pauseAt(level != null ? level.getGameTime() : 0);
            broadcastStateUpdate(level, fullStateKey, state, "pause");
            ServerPlaybackManager.resyncState(server, level, fullStateKey);
            SpeakerEvents.fire(SpeakerEvents.Type.PAUSED, fullStateKey, state.getNetworkName(), state.getAudioId());
            ServerSpeakerRegistry.markDirty();
            return true;
        }
        return false;
    }

    public static boolean togglePause(MinecraftServer server, ServerLevel level, String fullStateKey) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;

        if (!state.isPlaying() || state.isPaused()) {
            return play(server, level, fullStateKey);
        }
        return pause(server, level, fullStateKey);
    }

    public static boolean stop(MinecraftServer server, ServerLevel level, String fullStateKey) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;

        if (state.isPlaying() || state.isPaused()) {
            state.stopPlayback();
            broadcastStateUpdate(level, fullStateKey, state, "stop");
            ServerPlaybackManager.resyncState(server, level, fullStateKey);
            SpeakerEvents.fire(SpeakerEvents.Type.STOPPED, fullStateKey, state.getNetworkName(), state.getAudioId());
            ServerSpeakerRegistry.markDirty();
        }
        return true;
    }

    public static boolean restart(MinecraftServer server, ServerLevel level, String fullStateKey) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;
        if (!state.hasAudio()) return false;

        state.startPlaybackAt(level != null ? level.getGameTime() : 0, 0.0f);
        broadcastStateUpdate(level, fullStateKey, state, "play");
        ServerPlaybackManager.resyncState(server, level, fullStateKey);
        SpeakerEvents.fire(SpeakerEvents.Type.STARTED, fullStateKey, state.getNetworkName(), state.getAudioId());
        ServerSpeakerRegistry.markDirty();
        return true;
    }

    public static boolean seekRelative(MinecraftServer server, ServerLevel level, String fullStateKey, float deltaSeconds) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;
        if (!state.isPlaying()) return false;
        float current = state.getPlaybackPositionSeconds(level != null ? level.getGameTime() : 0);
        return seek(server, level, fullStateKey, Math.max(0.0f, current + deltaSeconds));
    }

    public static boolean seek(MinecraftServer server, ServerLevel level, String fullStateKey, float seconds) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;

        float duration = 0.0f;
        AudioFileManager afm = SimplySpeakers.getAudioFileManager();
        if (afm != null) {
            AudioFileMetadata meta = afm.getManifest().get(state.getAudioId());
            if (meta != null) duration = meta.getDurationSeconds();
        }
        state.seekTo(seconds, level != null ? level.getGameTime() : 0, duration);
        broadcastStateUpdate(level, fullStateKey, state, state.isPaused() ? "pause" : "play");
        ServerPlaybackManager.resyncState(server, level, fullStateKey);
        ServerSpeakerRegistry.markDirty();
        return true;
    }

    public static boolean next(MinecraftServer server, ServerLevel level, String fullStateKey) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;

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
            ServerSpeakerRegistry.markDirty();
            return true;
        }
        return false;
    }

    public static boolean previous(MinecraftServer server, ServerLevel level, String fullStateKey) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;

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
            ServerSpeakerRegistry.markDirty();
            return true;
        }
        return false;
    }

    public static boolean selectAudio(MinecraftServer server, ServerLevel level, String fullStateKey, String audioId, String filename) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;

        state.setAudioId(audioId != null ? audioId : "");
        state.setAudioFilename(filename != null ? filename : "");
        if (state.hasAudio()) {
            if (state.isPlaying()) {
                state.startPlaybackAt(level != null ? level.getGameTime() : 0, 0.0f);
            }
        } else {
            // Selected audio cleared: a speaker with no audio cannot be "playing".
            state.stopPlayback();
        }
        broadcastStateUpdate(level, fullStateKey, state, state.isPlaying() ? (state.isPaused() ? "pause" : "play") : "update");
        if (state.isPlaying() && !state.isPaused()) {
            ServerPlaybackManager.resyncState(server, level, fullStateKey);
        }
        SpeakerEvents.fire(SpeakerEvents.Type.TRACK_CHANGED, fullStateKey, state.getNetworkName(), state.getAudioId());
        ServerSpeakerRegistry.markDirty();
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
        ServerSpeakerRegistry.markDirty();
        return true;
    }

    /**
     * Applies a policy mutation. Directional audio parameters additionally resync active
     * playback so existing listeners hear the change immediately instead of only after the
     * next restart/re-entry.
     */
    public static boolean policyControl(MinecraftServer server, ServerLevel level, String fullStateKey, byte op, String strValue, int intValue, float floatValue, UUID playerUuid) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;

        boolean directional = false;
        switch (op) {
            case SpeakerPolicyPacketC2S.OP_CLAIM_OWNER -> {
                // First-come ownership: only an unowned speaker can be claimed.
                if (state.getOwnerUuid() == null && playerUuid != null) {
                    state.claimOwnershipIfAbsent(playerUuid);
                }
            }
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
                directional = true;
            }
            case SpeakerPolicyPacketC2S.OP_CONE_ANGLE -> {
                state.setConeAngleDegrees(Math.max(5, Math.min(350, intValue)));
                directional = true;
            }
            case SpeakerPolicyPacketC2S.OP_REAR_ATTENUATION -> {
                state.setRearAttenuation(Math.max(0.0f, Math.min(1.0f, floatValue)));
                directional = true;
            }
        }
        broadcastStateUpdate(level, fullStateKey, state, "update");
        if (directional) {
            ServerPlaybackManager.resyncState(server, level, fullStateKey);
        }
        ServerSpeakerRegistry.markDirty();
        return true;
    }

    /**
     * Applies a transport action to a resolved full state key. Returns true only when the
     * state exists and the action was applied (or was already satisfied), so API callers
     * can distinguish an accepted command from a no-op against a missing network.
     */
    public static boolean applyTransport(MinecraftServer server, ServerLevel level, String fullStateKey, byte action, float seekSeconds) {
        return switch (action) {
            case 0 -> play(server, level, fullStateKey);        // ACTION_PLAY
            case 1 -> pause(server, level, fullStateKey);       // ACTION_PAUSE
            case 2 -> togglePause(server, level, fullStateKey); // ACTION_TOGGLE
            case 3 -> stop(server, level, fullStateKey);        // ACTION_STOP
            case 4 -> restart(server, level, fullStateKey);     // ACTION_RESTART
            case 5 -> next(server, level, fullStateKey);        // ACTION_NEXT
            case 6 -> previous(server, level, fullStateKey);    // ACTION_PREVIOUS
            case 7 -> seek(server, level, fullStateKey, seekSeconds); // ACTION_SEEK
            case 8 -> seekRelative(server, level, fullStateKey, seekSeconds); // ACTION_SEEK_RELATIVE
            default -> false;
        };
    }

    public static boolean setVolume(MinecraftServer server, ServerLevel level, String fullStateKey, float volume) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;
        state.setMaxVolume(Math.max(0.0f, Math.min(1.0f, volume)));
        broadcastStateUpdate(level, fullStateKey, state, "update");
        ServerPlaybackManager.resyncState(server, level, fullStateKey);
        ServerSpeakerRegistry.markDirty();
        return true;
    }

    public static boolean setRange(MinecraftServer server, ServerLevel level, String fullStateKey, int range) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;
        state.setMaxRange(Math.max(1, range));
        broadcastStateUpdate(level, fullStateKey, state, "update");
        ServerPlaybackManager.resyncState(server, level, fullStateKey);
        ServerSpeakerRegistry.markDirty();
        return true;
    }

    public static boolean setLooping(MinecraftServer server, ServerLevel level, String fullStateKey, boolean looping) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;
        state.setLooping(looping);
        broadcastStateUpdate(level, fullStateKey, state, "update");
        ServerPlaybackManager.resyncState(server, level, fullStateKey);
        ServerSpeakerRegistry.markDirty();
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

    /**
     * Broadcasts a transport-state update to all players in the level.
     * The packet carries the authoritative full state key so open GUIs can match their
     * network even when the packet's physical position is not the GUI's speaker.
     * For standalone states a concrete position is resolved from the registry so the
     * client can update the correct block's client-side state.
     */
    private static void broadcastStateUpdate(ServerLevel level, String fullStateKey, SpeakerState state, String action) {
        if (level == null || fullStateKey == null || state == null) return;
        String speakerId = fullStateKey.contains("/net_") ? fullStateKey.substring(fullStateKey.indexOf("/net_") + 5) : "";
        BlockPos pos = speakerId.isEmpty() ? ServerSpeakerRegistry.findFirstSpeakerPosition(fullStateKey) : null;
        SpeakerStateUpdatePacketS2C packet = new SpeakerStateUpdatePacketS2C(
                pos,
                speakerId,
                action,
                state.getAudioId(),
                state.getAudioFilename(),
                state.getPlaybackStartTick(),
                state.isLooping(),
                fullStateKey
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
                fullStateKey,
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

