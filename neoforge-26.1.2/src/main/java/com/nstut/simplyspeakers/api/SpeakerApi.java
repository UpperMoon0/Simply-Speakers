package com.nstut.simplyspeakers.api;

import com.nstut.simplyspeakers.RedstoneMode;
import com.nstut.simplyspeakers.SpeakerAccess;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.playlist.RepeatMode;
import com.nstut.simplyspeakers.speakers.ServerSpeakerControlService;
import com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Public Java API for controlling speaker networks from other mods, KubeJS
 * scripts, or automation bridges. All operations route authority through
 * {@link ServerSpeakerControlService} and {@link ServerSpeakerRegistry}.
 */
public final class SpeakerApi {

    /** Raw transport action bytes; mirrors TransportControlPacketC2S constants. */
    public static final byte ACTION_PLAY = 0;
    public static final byte ACTION_PAUSE = 1;
    public static final byte ACTION_TOGGLE = 2;
    public static final byte ACTION_STOP = 3;
    public static final byte ACTION_RESTART = 4;
    public static final byte ACTION_NEXT = 5;
    public static final byte ACTION_PREVIOUS = 6;
    public static final byte ACTION_SEEK = 7;

    private SpeakerApi() {
    }

    /** Actor-less calls are untrusted automation, never an ownership bypass. */
    private static boolean authorized(Level level, BlockPos pos, boolean manage) {
        if (!(level instanceof ServerLevel) || pos == null) return false;
        SpeakerState state = getState(level, pos);
        return manage ? com.nstut.simplyspeakers.SpeakerPermissions.canAutomationManage(state)
                : com.nstut.simplyspeakers.SpeakerPermissions.canAutomationControl(state);
    }

    /** Resolves a public stream or the network owner's recording; ignores supplied filenames. */
    public static @Nullable com.nstut.simplyspeakers.audio.AudioFileMetadata resolveAutomationTrack(Level level, BlockPos pos, String id) {
        if (!authorized(level,pos,false) || id == null || id.isEmpty() || id.length()>256) return null;
        if (com.nstut.simplyspeakers.audio.StreamTracks.isHttpAudioUrl(id)) {
            if (!com.nstut.simplyspeakers.Config.isRemoteStreamingAllowed()
                    || !com.nstut.simplyspeakers.audio.StreamTracks.hasSupportedExtension(id)
                    || !com.nstut.simplyspeakers.audio.StreamTracks.isRemoteStreamUrlAllowed(id,false)) return null;
            return new com.nstut.simplyspeakers.audio.AudioFileMetadata(id,id);
        }
        var files=com.nstut.simplyspeakers.SimplySpeakers.getAudioFileManager();
        var state=getState(level,pos);
        if (files==null || state.getOwnerUuid()==null) return null;
        var track=files.getManifest().get(id);
        return track!=null && com.nstut.simplyspeakers.audio.AudioOwnership.isOwnedBy(track.getOwnerUUID(),state.getOwnerUuid().toString()) ? track : null;
    }

    /** Selection stays idle unless playback was already running. */
    public static boolean selectTrack(Level level,BlockPos pos,String id) {
        if (!authorized(level,pos,false)) return false;
        var track=id!=null && id.isEmpty()?null:resolveAutomationTrack(level,pos,id);
        if (track==null && (id==null || !id.isEmpty())) return false;
        var serverLevel=(ServerLevel)level;
        return ServerSpeakerControlService.selectAudio(serverLevel.getServer(),serverLevel,
            ServerSpeakerControlService.resolveFullStateKey(level,pos),id,track==null?"":track.getOriginalFilename());
    }

    // ------------------------------------------------------------------
    // Transport operations by BlockPos
    // ------------------------------------------------------------------

    public static boolean play(Level level, BlockPos pos) {
        return applyTransport(level, pos, ACTION_PLAY, 0.0f);
    }

    public static boolean pause(Level level, BlockPos pos) {
        return applyTransport(level, pos, ACTION_PAUSE, 0.0f);
    }

    public static boolean togglePause(Level level, BlockPos pos) {
        return applyTransport(level, pos, ACTION_TOGGLE, 0.0f);
    }

    public static boolean stop(Level level, BlockPos pos) {
        return applyTransport(level, pos, ACTION_STOP, 0.0f);
    }

    public static boolean restart(Level level, BlockPos pos) {
        return applyTransport(level, pos, ACTION_RESTART, 0.0f);
    }

    public static boolean next(Level level, BlockPos pos) {
        return applyTransport(level, pos, ACTION_NEXT, 0.0f);
    }

    public static boolean previous(Level level, BlockPos pos) {
        return applyTransport(level, pos, ACTION_PREVIOUS, 0.0f);
    }

    public static boolean seek(Level level, BlockPos pos, float seconds) {
        return applyTransport(level, pos, ACTION_SEEK, Math.max(0.0f, seconds));
    }

    public static boolean applyTransport(Level level, BlockPos pos, byte action) {
        return applyTransport(level, pos, action, 0.0f);
    }

    public static boolean applyTransport(Level level, BlockPos pos, byte action, float seekSeconds) {
        if (!Float.isFinite(seekSeconds)) return false;
        if (!authorized(level,pos,false)) return false;
        if (!(level instanceof ServerLevel serverLevel) || pos == null) return false;
        String fullKey = ServerSpeakerControlService.resolveFullStateKey(level, pos);
        if (fullKey == null) return false;
        return ServerSpeakerControlService.applyTransport(serverLevel.getServer(), serverLevel, fullKey, action, seekSeconds);
    }

    // ------------------------------------------------------------------
    // Transport operations by Network Name / Full Key
    // ------------------------------------------------------------------

    public static boolean playNetwork(Level level, String networkOrFullKey) {
        return applyTransportNetwork(level, networkOrFullKey, ACTION_PLAY, 0.0f);
    }

    public static boolean pauseNetwork(Level level, String networkOrFullKey) {
        return applyTransportNetwork(level, networkOrFullKey, ACTION_PAUSE, 0.0f);
    }

    public static boolean togglePauseNetwork(Level level, String networkOrFullKey) {
        return applyTransportNetwork(level, networkOrFullKey, ACTION_TOGGLE, 0.0f);
    }

    public static boolean stopNetwork(Level level, String networkOrFullKey) {
        return applyTransportNetwork(level, networkOrFullKey, ACTION_STOP, 0.0f);
    }

    public static boolean restartNetwork(Level level, String networkOrFullKey) {
        return applyTransportNetwork(level, networkOrFullKey, ACTION_RESTART, 0.0f);
    }

    public static boolean nextNetwork(Level level, String networkOrFullKey) {
        return applyTransportNetwork(level, networkOrFullKey, ACTION_NEXT, 0.0f);
    }

    public static boolean previousNetwork(Level level, String networkOrFullKey) {
        return applyTransportNetwork(level, networkOrFullKey, ACTION_PREVIOUS, 0.0f);
    }

    public static boolean seekNetwork(Level level, String networkOrFullKey, float seconds) {
        return applyTransportNetwork(level, networkOrFullKey, ACTION_SEEK, Math.max(0.0f, seconds));
    }

    public static boolean applyTransportNetwork(Level level, String networkOrFullKey, byte action, float seekSeconds) {
        if (!(level instanceof ServerLevel serverLevel) || networkOrFullKey == null) return false;
        String fullKey = ServerSpeakerControlService.resolveFullStateKeyByNetwork(level, networkOrFullKey);
        if (fullKey == null || !Float.isFinite(seekSeconds)
                || !com.nstut.simplyspeakers.SpeakerPermissions.canAutomationControl(ServerSpeakerRegistry.getSpeakerStateByFullKey(fullKey))) return false;
        return ServerSpeakerControlService.applyTransport(serverLevel.getServer(), serverLevel, fullKey, action, seekSeconds);
    }

    // ------------------------------------------------------------------
    // State mutators (Settings, Policy, Playlists)
    // ------------------------------------------------------------------

    public static boolean setTrack(Level level, BlockPos pos, String audioId, String filename) {
        if (!authorized(level,pos,false)) return false;
        if (!(level instanceof ServerLevel serverLevel) || pos == null) return false;
        String fullKey = ServerSpeakerControlService.resolveFullStateKey(level, pos);
        if (fullKey == null) return false;
        boolean ok = selectTrack(level,pos,audioId);
        if (ok) ServerSpeakerControlService.play(serverLevel.getServer(), serverLevel, fullKey);
        return ok;
    }

    public static boolean setLooping(Level level, BlockPos pos, boolean looping) {
        if (!authorized(level,pos,false)) return false;
        if (!(level instanceof ServerLevel serverLevel) || pos == null) return false;
        String fullKey = ServerSpeakerControlService.resolveFullStateKey(level, pos);
        if (fullKey == null) return false;
        return ServerSpeakerControlService.setLooping(serverLevel.getServer(), serverLevel, fullKey, looping);
    }

    public static boolean setVolume(Level level, BlockPos pos, float volume0to1) {
        if (!Float.isFinite(volume0to1)) return false;
        if (!authorized(level,pos,false)) return false;
        if (!(level instanceof ServerLevel serverLevel) || pos == null) return false;
        String fullKey = ServerSpeakerControlService.resolveFullStateKey(level, pos);
        if (fullKey == null) return false;
        return ServerSpeakerControlService.setVolume(serverLevel.getServer(), serverLevel, fullKey, volume0to1);
    }

    public static boolean setRange(Level level, BlockPos pos, int blocks) {
        if (!authorized(level,pos,false)) return false;
        if (!(level instanceof ServerLevel serverLevel) || pos == null) return false;
        String fullKey = ServerSpeakerControlService.resolveFullStateKey(level, pos);
        if (fullKey == null) return false;
        return ServerSpeakerControlService.setRange(serverLevel.getServer(), serverLevel, fullKey, Math.max(1,Math.min(com.nstut.simplyspeakers.Config.speakerRange,blocks)));
    }

    public static boolean setRedstoneMode(Level level, BlockPos pos, RedstoneMode mode) {
        if (!authorized(level,pos,true)) return false;
        if (!(level instanceof ServerLevel serverLevel) || pos == null) return false;
        String fullKey = ServerSpeakerControlService.resolveFullStateKey(level, pos);
        if (fullKey == null) return false;
        return ServerSpeakerControlService.policyControl(serverLevel.getServer(), serverLevel, fullKey,
                com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.OP_REDSTONE_MODE,
                "", mode != null ? mode.ordinal() : 0, 0.0f, null);
    }

    public static boolean setAccessMode(Level level, BlockPos pos, SpeakerAccess access) {
        if (!authorized(level,pos,true)) return false;
        if (!(level instanceof ServerLevel serverLevel) || pos == null) return false;
        String fullKey = ServerSpeakerControlService.resolveFullStateKey(level, pos);
        if (fullKey == null) return false;
        return ServerSpeakerControlService.policyControl(serverLevel.getServer(), serverLevel, fullKey,
                com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.OP_ACCESS_MODE,
                "", access != null ? access.ordinal() : 0, 0.0f, null);
    }

    public static boolean setNetworkName(Level level, BlockPos pos, String name) {
        if (!authorized(level,pos,true)) return false;
        if (!(level instanceof ServerLevel serverLevel) || pos == null) return false;
        String fullKey = ServerSpeakerControlService.resolveFullStateKey(level, pos);
        if (fullKey == null) return false;
        return ServerSpeakerControlService.policyControl(serverLevel.getServer(), serverLevel, fullKey,
                com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.OP_NETWORK_NAME,
                name, 0, 0.0f, null);
    }

    public static boolean playlistAdd(Level level, BlockPos pos, String audioId, String filename) {
        return playlistOp(level, pos, (byte) 0, -1, false, audioId, filename);
    }

    public static boolean playlistRemove(Level level, BlockPos pos, String audioId) {
        return playlistOp(level, pos, (byte) 1, -1, false, audioId, "");
    }

    public static boolean playlistClear(Level level, BlockPos pos) {
        return playlistOp(level, pos, (byte) 5, -1, false, "", "");
    }

    public static boolean playlistSetShuffle(Level level, BlockPos pos, boolean shuffle) {
        return playlistOp(level, pos, (byte) 7, -1, shuffle, "", "");
    }

    public static boolean playlistSetRepeat(Level level, BlockPos pos, RepeatMode mode) {
        return playlistOp(level, pos, (byte) 8, mode != null ? mode.ordinal() : 0, false, "", "");
    }

    public static boolean playlistQueueNext(Level level, BlockPos pos, String audioId) {
        return playlistOp(level, pos, (byte) 6, -1, false, audioId, "");
    }

    private static boolean playlistOp(Level level, BlockPos pos, byte op, int index,
                                      boolean flag, String audioId, String filename) {
        if (!authorized(level,pos,false)) return false;
        if (!(level instanceof ServerLevel serverLevel) || pos == null) return false;
        String fullKey = ServerSpeakerControlService.resolveFullStateKey(level, pos);
        if (fullKey == null) return false;
        if (op==com.nstut.simplyspeakers.network.PlaylistControlPacketC2S.OP_ADD || op==com.nstut.simplyspeakers.network.PlaylistControlPacketC2S.OP_QUEUE_NEXT || op==com.nstut.simplyspeakers.network.PlaylistControlPacketC2S.OP_QUEUE_LAST) {
            var track=resolveAutomationTrack(level,pos,audioId);if(track==null)return false;
            audioId=track.getUuid();filename=track.getOriginalFilename();
        }
        return ServerSpeakerControlService.playlistControl(serverLevel.getServer(), serverLevel, fullKey, op, index, flag, audioId, filename);
    }

    public static boolean setAudioDropoff(Level level,BlockPos pos,float value) {
        if (!Float.isFinite(value) || !authorized(level,pos,false)) return false;
        var serverLevel=(ServerLevel)level;
        return ServerSpeakerControlService.setAudioDropoff(serverLevel.getServer(),serverLevel,ServerSpeakerControlService.resolveFullStateKey(level,pos),value);
    }
    private static boolean directionalPolicy(Level level,BlockPos pos,byte op,int integer,float value) {
        if (!Float.isFinite(value) || !authorized(level,pos,true)) return false;
        var serverLevel=(ServerLevel)level;
        return ServerSpeakerControlService.policyControl(serverLevel.getServer(),serverLevel,ServerSpeakerControlService.resolveFullStateKey(level,pos),op,"",integer,value,null);
    }
    public static boolean setDirectionality(Level level,BlockPos pos,float value) { return directionalPolicy(level,pos,com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.OP_DIRECTIONALITY,0,value); }
    public static boolean setConeAngle(Level level,BlockPos pos,int degrees) { return directionalPolicy(level,pos,com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.OP_CONE_ANGLE,degrees,0); }
    public static boolean setRearAttenuation(Level level,BlockPos pos,float value) { return directionalPolicy(level,pos,com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.OP_REAR_ATTENUATION,0,value); }
    public static boolean playlistQueueLast(Level level,BlockPos pos,String id) { return playlistOp(level,pos,com.nstut.simplyspeakers.network.PlaylistControlPacketC2S.OP_QUEUE_LAST,0,false,id,""); }
    public static boolean playlistClearQueue(Level level,BlockPos pos) { return playlistOp(level,pos,com.nstut.simplyspeakers.network.PlaylistControlPacketC2S.OP_CLEAR_QUEUE,0,false,"",""); }
    public static boolean playlistRemoveQueued(Level level,BlockPos pos,int index) {
        var state=getState(level,pos);if(state==null || index<0 || index>=state.getPlaylist().getQueue().size())return false;
        return playlistOp(level,pos,com.nstut.simplyspeakers.network.PlaylistControlPacketC2S.OP_REMOVE_QUEUED,index,false,"","");
    }
    public static boolean playlistMoveQueued(Level level,BlockPos pos,int index,int delta) {
        var state=getState(level,pos);if(state==null || Math.abs(delta)!=1 || index<0 || index+delta<0
            || index>=state.getPlaylist().getQueue().size() || index+delta>=state.getPlaylist().getQueue().size())return false;
        return playlistOp(level,pos,delta<0?com.nstut.simplyspeakers.network.PlaylistControlPacketC2S.OP_QUEUE_UP:com.nstut.simplyspeakers.network.PlaylistControlPacketC2S.OP_QUEUE_DOWN,index,false,"","");
    }
    public static boolean playlistPlay(Level level,BlockPos pos) { return playlistOp(level,pos,com.nstut.simplyspeakers.network.PlaylistControlPacketC2S.OP_PLAY_PLAYLIST,0,false,"",""); }
    public static boolean playlistSelect(Level level,BlockPos pos,int index) { return playlistOp(level,pos,com.nstut.simplyspeakers.network.PlaylistControlPacketC2S.OP_SELECT_INDEX,index,true,"",""); }

    public static java.util.List<com.nstut.simplyspeakers.audio.AudioFileMetadata> getLibrary(Level level,BlockPos pos) {
        if(!authorized(level,pos,false))return java.util.List.of();
        var state=getState(level,pos);var files=com.nstut.simplyspeakers.SimplySpeakers.getAudioFileManager();
        if(files==null || state.getOwnerUuid()==null)return java.util.List.of();
        return files.getManifest().values().stream().filter(audio -> com.nstut.simplyspeakers.audio.AudioOwnership.isOwnedBy(audio.getOwnerUUID(),state.getOwnerUuid().toString()))
            .sorted(java.util.Comparator.comparing(com.nstut.simplyspeakers.audio.AudioFileMetadata::getOriginalFilename)).toList();
    }

    /** Read-only catalog belonging to the owner who opted this network into public automation. */
    public static java.util.List<com.nstut.simplyspeakers.playlist.PlaylistLibrarySnapshot.Entry> getSavedPlaylists(Level level,BlockPos pos) {
        if(!authorized(level,pos,false))return java.util.List.of();
        var state=getState(level,pos);if(state.getOwnerUuid()==null)return java.util.List.of();
        return com.nstut.simplyspeakers.playlist.PlaylistLibrarySnapshot.capture(
            com.nstut.simplyspeakers.playlist.PlayerPlaylistStore.library(state.getOwnerUuid())).entries();
    }
    /** Copies an owner's template into this speaker without changing their saved list or queue. */
    public static boolean playSavedPlaylist(Level level,BlockPos pos,String id) {
        if(!authorized(level,pos,false) || id==null)return false;
        var state=getState(level,pos);if(state.getOwnerUuid()==null)return false;
        var saved=com.nstut.simplyspeakers.playlist.PlayerPlaylistStore.library(state.getOwnerUuid()).findSavedPlaylist(id);
        if(saved==null || saved.getPlaylist().isEmpty())return false;
        for(var track:saved.getPlaylist().getTracks())if(resolveAutomationTrack(level,pos,track.getAudioId())==null)return false;
        state.usePlayerPlaylist(state.getOwnerUuid(),id,saved.getPlaylist());
        return playlistPlay(level,pos);
    }

    // ------------------------------------------------------------------
    // Read-only queries
    // ------------------------------------------------------------------

    public static @Nullable SpeakerState getState(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        if (level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity speaker) {
                return speaker.getSpeakerState();
            }
            return null;
        }
        String fullKey = ServerSpeakerControlService.resolveFullStateKey(level, pos);
        return fullKey != null ? ServerSpeakerRegistry.getSpeakerStateByFullKey(fullKey) : null;
    }

    public static @Nullable SpeakerState getStateNetwork(Level level, String networkOrFullKey) {
        if (level == null || networkOrFullKey == null) return null;
        String fullKey = ServerSpeakerControlService.resolveFullStateKeyByNetwork(level, networkOrFullKey);
        return fullKey != null ? ServerSpeakerRegistry.getSpeakerStateByFullKey(fullKey) : null;
    }

    public static boolean isPlaying(Level level, BlockPos pos) {
        SpeakerState state = getState(level, pos);
        return state != null && state.isPlaying() && !state.isPaused();
    }

    public static boolean isPaused(Level level, BlockPos pos) {
        SpeakerState state = getState(level, pos);
        return state != null && state.isPaused();
    }

    public static float getPositionSeconds(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return 0.0f;
        SpeakerState state = getState(level, pos);
        return state != null ? state.getPlaybackPositionSeconds(serverLevel.getGameTime()) : 0.0f;
    }

    public static String getTrackId(Level level, BlockPos pos) {
        SpeakerState state = getState(level, pos);
        return state != null ? state.getAudioId() : "";
    }

    /** Lists every named network known to the registry: name -> summary. */
    public static Map<String, String> listNamedNetworks(Level level) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, SpeakerState> entry : ServerSpeakerRegistry.getAllSpeakerStates().entrySet()) {
            if (com.nstut.simplyspeakers.portable.PortableSpeakerManager.isBackupStateKey(entry.getKey())) continue;
            SpeakerState state = entry.getValue();
            if (state == null || !state.hasNetworkName()) continue;
            String status = !state.hasAudio() ? "empty"
                    : state.isPaused() ? "paused"
                    : state.isPlaying() ? "playing" : "stopped";
            String track = state.getAudioFilename().isEmpty() ? state.getAudioId() : state.getAudioFilename();
            String name = state.getNetworkName();
            if (result.containsKey(name)) {
                // Duplicate human network name: disambiguate with the owning dimension.
                String dim = entry.getKey().contains("/") ? entry.getKey().substring(0, entry.getKey().indexOf('/')) : "?";
                name = name + " [" + dim + "]";
            }
            result.put(name, status + ": " + track + " [" + entry.getKey() + "]");
        }
        return result;
    }

    /** Finds the first main-speaker position carrying the given network name in this dimension. */
    public static @Nullable BlockPos findNamedNetwork(Level level, String networkName) {
        if (networkName == null || networkName.isBlank()) return null;
        String prefix = ServerSpeakerRegistry.getDimension(level) + "/";
        for (Map.Entry<String, SpeakerState> entry : ServerSpeakerRegistry.getAllSpeakerStates().entrySet()) {
            if (com.nstut.simplyspeakers.portable.PortableSpeakerManager.isBackupStateKey(entry.getKey())) continue;
            SpeakerState state = entry.getValue();
            if (state == null || !networkName.equalsIgnoreCase(state.getNetworkName())) continue;
            if (!entry.getKey().startsWith(prefix)) continue;
            String stateKey = entry.getKey().substring(prefix.length());
            Set<BlockPos> found = ServerSpeakerRegistry.getSpeakerPositions(level, stateKey);
            if (!found.isEmpty()) return found.iterator().next();
        }
        return null;
    }
}

