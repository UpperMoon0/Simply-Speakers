package com.nstut.simplyspeakers.speakers;

import com.nstut.simplyspeakers.RedstoneMode;
import com.nstut.simplyspeakers.SpeakerAccess;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.api.SpeakerEvents;
import com.nstut.simplyspeakers.audio.AudioFileManager;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
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

/** Authoritative server-side transport and state mutation service. */
public final class ServerSpeakerControlService {
    private ServerSpeakerControlService() {}

    public static String resolveFullStateKey(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        return ServerSpeakerRegistry.getFullStateKeyAt(level, pos);
    }

    public static String resolveFullStateKeyByNetwork(Level level, String networkOrFullKey) {
        if (level == null || networkOrFullKey == null || networkOrFullKey.trim().isEmpty()) return null;
        String trimmed = networkOrFullKey.trim();
        String dimension = ServerSpeakerRegistry.getDimension(level);
        if (trimmed.contains("/") && ServerSpeakerRegistry.getSpeakerStateByFullKey(trimmed) != null) return trimmed;
        if (trimmed.startsWith("net_")) {
            String fullKey = dimension + "/" + trimmed;
            return ServerSpeakerRegistry.getSpeakerStateByFullKey(fullKey) != null ? fullKey : null;
        }
        String netKey = dimension + "/net_" + trimmed;
        if (ServerSpeakerRegistry.getSpeakerStateByFullKey(netKey) != null) return netKey;
        String matchedKey = null;
        boolean ambiguous = false;
        for (var entry : ServerSpeakerRegistry.getAllSpeakerStates().entrySet()) {
            if (!entry.getKey().startsWith(dimension + "/")) continue;
            SpeakerState s = entry.getValue();
            if (s != null && trimmed.equalsIgnoreCase(s.getNetworkName())) {
                if (matchedKey != null) { ambiguous = true; break; }
                matchedKey = entry.getKey();
            }
        }
        return !ambiguous ? matchedKey : null;
    }

    /** Starts a local clip once without editing the saved playlist or repeat preference. */
    public static boolean playAnnouncement(MinecraftServer server, ServerLevel level, String key,
                                          String audioId, UUID actor, boolean restart) {
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(key);
        AudioFileManager files = ServerPlaybackEnvironment.audioFiles();
        AudioFileMetadata meta = files == null ? null : files.getManifest().get(audioId);
        if (actor == null || state == null || meta == null || meta.getDurationSeconds() <= 0
            || !com.nstut.simplyspeakers.audio.AudioOwnership.isOwnedBy(meta.getOwnerUUID(), actor.toString())) return false;
        if (!restart && state.isPlaying() && state.isOneShotPlayback() && audioId.equals(state.getAudioId())) return true;
        ServerPlaybackManager.beginNewPlaybackSession(key);
        state.setAudioId(meta.getUuid()); state.setAudioFilename(meta.getOriginalFilename());
        state.startPlaybackAt(level.getGameTime(), 0);
        state.setOneShotPlayback(true);
        broadcastStateUpdate(level, key, state, "play");
        ServerPlaybackManager.resyncState(server, level, key);
        ServerSpeakerRegistry.markDirty();
        SpeakerEvents.fire(SpeakerEvents.Type.STARTED, key, state.getNetworkName(), audioId);
        return true;
    }

    public static boolean play(MinecraftServer server, ServerLevel level, String fullStateKey) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;
        if (!state.hasAudio()) return state.hasPlaybackContinuation() && next(server, level, fullStateKey);
        if (state.isPlaying() && !state.isPaused()) return true;
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
        return (!state.isPlaying() || state.isPaused()) ? play(server, level, fullStateKey) : pause(server, level, fullStateKey);
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
        if (state == null || !state.hasAudio()) return false;
        ServerPlaybackManager.beginNewPlaybackSession(fullStateKey);
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
        if (state == null || !state.isPlaying()) return false;
        float current = state.getPlaybackPositionSeconds(level != null ? level.getGameTime() : 0);
        return seek(server, level, fullStateKey, Math.max(0.0f, current + deltaSeconds));
    }

    public static boolean seek(MinecraftServer server, ServerLevel level, String fullStateKey, float seconds) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null || !state.isPlaying() || !Float.isFinite(seconds)) return false;
        float duration = 0.0f;
        AudioFileManager afm = ServerPlaybackEnvironment.audioFiles();
        if (afm != null) {
            AudioFileMetadata meta = afm.getManifest().get(state.getAudioId());
            if (meta != null) duration = meta.getDurationSeconds();
        }
        state.seekTo(Math.max(0.0f, seconds), level != null ? level.getGameTime() : 0, duration);
        broadcastStateUpdate(level, fullStateKey, state, state.isPaused() ? "pause" : "play");
        ServerPlaybackManager.resyncState(server, level, fullStateKey);
        ServerSpeakerRegistry.markDirty();
        return true;
    }

    public static boolean next(MinecraftServer server, ServerLevel level, String fullStateKey) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null || (!state.hasAudio() && !state.hasPlaybackContinuation())) return false;
        Playlist.Advance adv = state.hasPlaybackContinuation() ? state.getPlaylist().nextRequested()
                : new Playlist.Advance(Playlist.AdvanceResult.EXHAUSTED, null);
        if (adv.hasTrack()) {
            state.setAudioId(adv.track().getAudioId());
            state.setAudioFilename(filenameFor(adv.track()));
            ServerPlaybackManager.beginNewPlaybackSession(fullStateKey);
            state.startPlaybackAt(level != null ? level.getGameTime() : 0, 0.0f);
            broadcastStateUpdate(level, fullStateKey, state, "play");
            ServerPlaybackManager.resyncState(server, level, fullStateKey);
            SpeakerEvents.fire(SpeakerEvents.Type.TRACK_CHANGED, fullStateKey, state.getNetworkName(), state.getAudioId());
        } else {
            state.stopPlayback();
            broadcastStateUpdate(level, fullStateKey, state, "stop");
            ServerPlaybackManager.resyncState(server, level, fullStateKey);
            SpeakerEvents.fire(SpeakerEvents.Type.FINISHED, fullStateKey, state.getNetworkName(), state.getAudioId());
        }
        ServerSpeakerRegistry.markDirty();
        return true;
    }

    public static boolean previous(MinecraftServer server, ServerLevel level, String fullStateKey) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;
        if (!state.isPlaylistSourceActive() || state.getPlaylist().isEmpty()) return restart(server, level, fullStateKey);
        if (!state.hasPlaylist()) return false;
        Playlist.Advance adv = state.getPlaylist().previous();
        if (adv.hasTrack()) {
            state.setAudioId(adv.track().getAudioId());
            state.setAudioFilename(filenameFor(adv.track()));
            ServerPlaybackManager.beginNewPlaybackSession(fullStateKey);
            state.startPlaybackAt(level != null ? level.getGameTime() : 0, 0.0f);
            broadcastStateUpdate(level, fullStateKey, state, "play");
            ServerPlaybackManager.resyncState(server, level, fullStateKey);
            SpeakerEvents.fire(SpeakerEvents.Type.TRACK_CHANGED, fullStateKey, state.getNetworkName(), state.getAudioId());
        } else {
            state.stopPlayback();
            broadcastStateUpdate(level, fullStateKey, state, "stop");
            ServerPlaybackManager.resyncState(server, level, fullStateKey);
            SpeakerEvents.fire(SpeakerEvents.Type.FINISHED, fullStateKey, state.getNetworkName(), state.getAudioId());
        }
        ServerSpeakerRegistry.markDirty();
        return true;
    }

    public static boolean selectAudio(MinecraftServer server, ServerLevel level, String fullStateKey, String audioId, String filename) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;
        state.setAudioId(audioId != null ? audioId : "");
        state.setAudioFilename(filename != null ? filename : "");
        if (state.hasAudio()) {
            if (state.isPlaying()) {
                ServerPlaybackManager.beginNewPlaybackSession(fullStateKey);
                state.startPlaybackAt(level != null ? level.getGameTime() : 0, 0.0f);
            }
        } else {
            state.stopPlayback();
        }
        broadcastStateUpdate(level, fullStateKey, state, state.isPlaying() ? (state.isPaused() ? "pause" : "play") : "update");
        if (state.isPlaying() && !state.isPaused()) ServerPlaybackManager.resyncState(server, level, fullStateKey);
        SpeakerEvents.fire(SpeakerEvents.Type.TRACK_CHANGED, fullStateKey, state.getNetworkName(), state.getAudioId());
        ServerSpeakerRegistry.markDirty();
        return true;
    }

    public static boolean playlistControl(MinecraftServer server, ServerLevel level, String fullStateKey, byte op, int index, boolean flag, String audioId, String filename) {
        return playlistControl(server,level,fullStateKey,op,index,flag,audioId,filename,"");
    }
    public static boolean playlistControl(MinecraftServer server, ServerLevel level, String fullStateKey, byte op, int index, boolean flag, String audioId,String filename,String playlistId) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;
        boolean queueOperation=op>=PlaylistControlPacketC2S.OP_QUEUE_NEXT && op<=PlaylistControlPacketC2S.OP_QUEUE_DOWN && op!=PlaylistControlPacketC2S.OP_SET_SHUFFLE && op!=PlaylistControlPacketC2S.OP_SET_REPEAT;
        boolean activeOperation=queueOperation || op==PlaylistControlPacketC2S.OP_SET_SHUFFLE || op==PlaylistControlPacketC2S.OP_SET_REPEAT || op==PlaylistControlPacketC2S.OP_PLAY_AUDIO;
        String targetId=playlistId==null || playlistId.isEmpty() || activeOperation ? state.getActivePlaylistId() : playlistId;
        var saved=state.findSavedPlaylist(targetId);
        if(saved==null && !activeOperation && !state.isPersonalPlaylistSource() && op!=PlaylistControlPacketC2S.OP_CREATE_PLAYLIST)return false;
        Playlist playlist=state.isPersonalPlaylistSource() || saved==null?state.getPlaylist():saved.getPlaylist();
        boolean playbackLoopingBefore = state.isPlaybackLooping();
        boolean trackChanged = false;
        switch (op) {
            case PlaylistControlPacketC2S.OP_ADD -> {
                if(audioId==null || audioId.isEmpty() || playlist.size()>=Playlist.MAX_ENTRIES || state.savedTrackCount()>=SpeakerState.MAX_SAVED_TRACKS) return false;
                playlist.add(audioId,filename==null?"":filename);
            }
            case PlaylistControlPacketC2S.OP_CREATE_PLAYLIST -> { if(state.createSavedPlaylist(filename)==null) return false; }
            case PlaylistControlPacketC2S.OP_RENAME_PLAYLIST -> { if(!state.renameSavedPlaylist(targetId,filename)) return false; }
            case PlaylistControlPacketC2S.OP_DUPLICATE_PLAYLIST -> { if(state.duplicateSavedPlaylist(targetId,filename)==null) return false; }
            case PlaylistControlPacketC2S.OP_DELETE_PLAYLIST -> {

                boolean active=state.getActivePlaylistId().equals(targetId);
                if(active) stop(server,level,fullStateKey);
                if(!state.deleteSavedPlaylist(targetId)) return false;
                trackChanged=active;
            }
            case PlaylistControlPacketC2S.OP_REMOVE_AUDIO -> { if (audioId != null && !audioId.isEmpty()) playlist.removeByAudioId(audioId); }
            case PlaylistControlPacketC2S.OP_REMOVE_INDEX -> playlist.removeAt(index);
            case PlaylistControlPacketC2S.OP_MOVE_UP -> playlist.moveUp(index);
            case PlaylistControlPacketC2S.OP_MOVE_DOWN -> playlist.moveDown(index);
            case PlaylistControlPacketC2S.OP_CLEAR -> playlist.clear();
            case PlaylistControlPacketC2S.OP_SET_SHUFFLE -> playlist.setShuffle(flag);
            case PlaylistControlPacketC2S.OP_SET_REPEAT -> playlist.setRepeatMode(RepeatMode.values()[Math.max(0, Math.min(RepeatMode.values().length - 1, index))]);
            case PlaylistControlPacketC2S.OP_SELECT_INDEX -> {
                if (index < 0 || index >= playlist.size()) return false;
                if(!state.getActivePlaylistId().equals(targetId) && !flag) { playlist.selectIndex(index);break; }
                state.activateSavedPlaylist(targetId);
                if(!state.isPersonalPlaylistSource())playlist.clearQueue();
                playlist.setResumeIndex(-1);
                PlaylistTrack selected = playlist.selectIndex(index);
                state.setPlaylistSourceActive(true);
                if (selected != null) {
                    state.setAudioId(selected.getAudioId());
                    state.setAudioFilename(selected.getFilename());
                    trackChanged = true;
                    if (flag) {
                        ServerPlaybackManager.beginNewPlaybackSession(fullStateKey);
                        state.startPlaybackAt(level != null ? level.getGameTime() : 0, 0.0f);
                    }
                }
            }
            case PlaylistControlPacketC2S.OP_QUEUE_NEXT -> {
                if(audioId==null || audioId.isEmpty() || audioId.length()>256 || playlist.getQueue().size()>=Playlist.MAX_ENTRIES)return false;
                playlist.queueNext(audioId);
            }
            case PlaylistControlPacketC2S.OP_QUEUE_LAST -> {
                if(audioId==null || audioId.isEmpty() || audioId.length()>256 || playlist.getQueue().size()>=Playlist.MAX_ENTRIES)return false;
                playlist.queueLast(audioId);
            }
            case PlaylistControlPacketC2S.OP_CLEAR_QUEUE -> playlist.clearQueue();
            case PlaylistControlPacketC2S.OP_REMOVE_QUEUED -> playlist.removeQueued(index);
            case PlaylistControlPacketC2S.OP_QUEUE_UP -> playlist.moveQueued(index, -1);
            case PlaylistControlPacketC2S.OP_QUEUE_DOWN -> playlist.moveQueued(index, 1);
            case PlaylistControlPacketC2S.OP_PLAY_PLAYLIST -> {
                if (playlist.size() == 0) return false;
                state.activateSavedPlaylist(targetId);
                var requests=state.isPersonalPlaylistSource()?new java.util.ArrayList<>(playlist.getQueue()):java.util.List.<String>of();
                PlaylistTrack selected = playlist.playFromStart();
                for(String request:requests)playlist.queueLast(request);
                state.setPlaylistSourceActive(true);
                if (selected == null) return false;
                state.setAudioId(selected.getAudioId());
                state.setAudioFilename(selected.getFilename());
                ServerPlaybackManager.beginNewPlaybackSession(fullStateKey);
                state.startPlaybackAt(level != null ? level.getGameTime() : 0, 0);
                trackChanged = true;
            }
            case PlaylistControlPacketC2S.OP_PLAY_AUDIO -> {
                if (audioId == null || audioId.isEmpty()) return false;
                playlist.clearQueue(); playlist.setResumeIndex(-1);
                state.setPlaylistSourceActive(false);
                state.setAudioId(audioId); state.setAudioFilename(filename);
                ServerPlaybackManager.beginNewPlaybackSession(fullStateKey);
                state.startPlaybackAt(level != null ? level.getGameTime() : 0, 0);
                trackChanged = true;
            }
            default -> { return false; }
        }
        if (!trackChanged) broadcastPlaylistSync(level, fullStateKey, state);
        if (!trackChanged && playbackLoopingBefore != state.isPlaybackLooping()) {
            broadcastStateUpdate(level, fullStateKey, state, "update");
            if (state.isPlaying() && !state.isPaused()) ServerPlaybackManager.resyncState(server,level,fullStateKey);
        }
        if (trackChanged) {
            boolean activelyPlaying = state.isPlaying() && !state.isPaused();
            broadcastStateUpdate(level, fullStateKey, state, activelyPlaying ? "play" : "update");
            if (activelyPlaying) ServerPlaybackManager.resyncState(server, level, fullStateKey);
            SpeakerEvents.fire(SpeakerEvents.Type.TRACK_CHANGED, fullStateKey, state.getNetworkName(), state.getAudioId());
        }
        ServerSpeakerRegistry.markDirty();
        return true;
    }

    public static boolean policyControl(MinecraftServer server, ServerLevel level, String fullStateKey, byte op, String strValue, int intValue, float floatValue, UUID playerUuid) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;
        boolean directional = false;
        switch (op) {
            case SpeakerPolicyPacketC2S.OP_CLAIM_OWNER -> { if (state.getOwnerUuid() == null && playerUuid != null) state.claimOwnershipIfAbsent(playerUuid); }
            case SpeakerPolicyPacketC2S.OP_TRANSFER_OWNER -> { if(playerUuid==null)return false;state.setOwnerUuid(playerUuid); }
            case SpeakerPolicyPacketC2S.OP_NETWORK_NAME -> state.setNetworkName(strValue != null ? strValue.trim() : "");
            case SpeakerPolicyPacketC2S.OP_ACCESS_MODE -> {
                state.claimOwnershipIfAbsent(playerUuid);
                state.setAccessMode(SpeakerAccess.fromIndex(intValue));
            }
            case SpeakerPolicyPacketC2S.OP_TRUST_CHANGE -> { if (playerUuid != null) { if (intValue > 0) state.trustPlayer(playerUuid); else state.distrustPlayer(playerUuid); } }
            case SpeakerPolicyPacketC2S.OP_REDSTONE_MODE -> { return false; } // Legacy packets cannot enable native redstone.
            case SpeakerPolicyPacketC2S.OP_DIRECTIONALITY -> { state.setDirectionality(Math.max(0.0f, Math.min(1.0f, floatValue))); directional = true; }
            case SpeakerPolicyPacketC2S.OP_CONE_ANGLE -> { state.setConeAngleDegrees(Math.max(5, Math.min(350, intValue))); directional = true; }
            case SpeakerPolicyPacketC2S.OP_REAR_ATTENUATION -> { state.setRearAttenuation(Math.max(0.0f, Math.min(1.0f, floatValue))); directional = true; }
        }
        broadcastStateUpdate(level, fullStateKey, state, "update");
        if (directional) ServerPlaybackManager.refreshSettings(server, level, fullStateKey);
        ServerSpeakerRegistry.markDirty();
        return true;
    }

    public static boolean applyTransport(MinecraftServer server, ServerLevel level, String fullStateKey, byte action, float seekSeconds) {
        return switch (action) {
            case 0 -> play(server, level, fullStateKey);
            case 1 -> pause(server, level, fullStateKey);
            case 2 -> togglePause(server, level, fullStateKey);
            case 3 -> stop(server, level, fullStateKey);
            case 4 -> restart(server, level, fullStateKey);
            case 5 -> next(server, level, fullStateKey);
            case 6 -> previous(server, level, fullStateKey);
            case 7 -> seek(server, level, fullStateKey, seekSeconds);
            case 8 -> seekRelative(server, level, fullStateKey, seekSeconds);
            default -> false;
        };
    }

    public static boolean setVolume(MinecraftServer server, ServerLevel level, String fullStateKey, float volume) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;
        state.setMaxVolume(Math.max(0.0f, Math.min(1.0f, volume)));
        ServerPlaybackManager.refreshSettings(server, level, fullStateKey);
        broadcastStateUpdate(level, fullStateKey, state, "update");
        ServerSpeakerRegistry.markDirty();
        return true;
    }

    public static boolean setAudioDropoff(MinecraftServer server,ServerLevel level,String key,float value) {
        if (!Float.isFinite(value)) return false;
        var state=ServerSpeakerRegistry.getSpeakerStateByFullKey(key);if(state==null)return false;
        state.setAudioDropoff(Math.max(0,Math.min(1,value)));
        ServerPlaybackManager.refreshSettings(server,level,key);
        broadcastStateUpdate(level,key,state,"update");ServerSpeakerRegistry.markDirty();return true;
    }

    /** Controller volume is transient; saved/manual volume survives unload and restart. */
    public static boolean setControllerVolume(MinecraftServer server, ServerLevel level, String fullStateKey, Float volume) {
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;
        state.setControllerVolume(volume);
        ServerPlaybackManager.refreshSettings(server, level, fullStateKey);
        broadcastStateUpdate(level, fullStateKey, state, "update");
        return true;
    }

    public static boolean setRange(MinecraftServer server, ServerLevel level, String fullStateKey, int range) {
        if (fullStateKey == null) return false;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null) return false;
        state.setMaxRange(Math.max(1, range));
        ServerPlaybackManager.refreshSettings(server, level, fullStateKey);
        broadcastStateUpdate(level, fullStateKey, state, "update");
        ServerSpeakerRegistry.markDirty();
        return true;
    }

    public static boolean setLooping(MinecraftServer server, ServerLevel level, String fullStateKey, boolean looping) {
        return playlistControl(server,level,fullStateKey,PlaylistControlPacketC2S.OP_SET_REPEAT,
            (looping?RepeatMode.TRACK:RepeatMode.NONE).ordinal(),false,"","");
    }

    public static void selectPlaylistSlot(MinecraftServer server, ServerLevel level, String fullStateKey, int slotIndex) {
        if (fullStateKey == null) return;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state != null && state.hasPlaylist() && slotIndex >= 0 && slotIndex < state.getPlaylist().size()) {
            playlistControl(server, level, fullStateKey, PlaylistControlPacketC2S.OP_SELECT_INDEX, slotIndex, true, "", "");
        }
    }

    static String filenameFor(PlaylistTrack track) {
        var files = ServerPlaybackEnvironment.audioFiles();
        var metadata = files == null ? null : files.getManifest().get(track.getAudioId());
        return metadata == null ? track.getFilename() : metadata.effectiveDisplayName();
    }

    /** Cascade file deletion without interrupting speakers playing a different track. */
    public static void removeDeletedAudio(MinecraftServer server, String audioId) {
        boolean catalogChanged=com.nstut.simplyspeakers.playlist.PlayerPlaylistStore.purgeAudio(audioId);
        for (var entry : ServerSpeakerRegistry.getAllSpeakerStates().entrySet()) {
            String key=entry.getKey();SpeakerState state=entry.getValue();
            boolean current=audioId.equals(state.getAudioId());
            boolean changed=state.purgeAudioReferences(audioId);
            if(!current && !changed && !catalogChanged)continue;
            if(current) {
                state.stopPlayback();state.setAudioId("");state.setAudioFilename("");
                ServerPlaybackManager.beginNewPlaybackSession(key);
            }
            if(server!=null)for(var world:server.getAllLevels()) {
                if(!key.startsWith(ServerSpeakerRegistry.getDimension(world)+"/"))continue;
                if(current)ServerPlaybackManager.resyncState(server,world,key);
                if(current || changed)broadcastStateUpdate(world,key,state,current?"stop":"update");
                else broadcastPlaylistSync(world,key,state);
            }
        }
        ServerSpeakerRegistry.markDirty();
        com.nstut.simplyspeakers.playlist.PlayerPlaylistStore.save();
    }

    private static void broadcastStateUpdate(ServerLevel level, String fullStateKey, SpeakerState state, String action) {
        if (level == null || fullStateKey == null || state == null) return;
        String speakerId = fullStateKey.contains("/net_") ? fullStateKey.substring(fullStateKey.indexOf("/net_") + 5) : "";
        BlockPos pos = speakerId.isEmpty() ? ServerSpeakerRegistry.findFirstSpeakerPosition(fullStateKey) : null;
        SpeakerStateUpdatePacketS2C packet = new SpeakerStateUpdatePacketS2C(
                pos, speakerId, action, state.getAudioId(), state.getAudioFilename(),
                state.getPlaybackStartTick(), state.isPlaybackLooping(), fullStateKey).withSettings(state);
        ServerPlaybackEnvironment.sendState(level, packet);
        broadcastPlaylistSync(level, fullStateKey, state);
    }

    private static void broadcastPlaylistSync(ServerLevel level, String fullStateKey, SpeakerState state) {
        if (level == null || fullStateKey == null || state == null) return;
        PlaylistSyncPacketS2C packet = PlaylistSyncPacketS2C.fromState(BlockPos.ZERO, fullStateKey, state, level.getGameTime(), ServerPlaybackEnvironment.audioFiles());
        ServerPlaybackEnvironment.sendPlaylist(level, packet);
    }
}
