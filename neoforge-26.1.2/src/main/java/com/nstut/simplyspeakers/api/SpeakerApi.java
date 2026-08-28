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
        if (fullKey == null) return false;
        return ServerSpeakerControlService.applyTransport(serverLevel.getServer(), serverLevel, fullKey, action, seekSeconds);
    }

    // ------------------------------------------------------------------
    // State mutators (Settings, Policy, Playlists)
    // ------------------------------------------------------------------

    public static boolean setTrack(Level level, BlockPos pos, String audioId, String filename) {
        if (!(level instanceof ServerLevel serverLevel) || pos == null) return false;
        String fullKey = ServerSpeakerControlService.resolveFullStateKey(level, pos);
        if (fullKey == null) return false;
        boolean ok = ServerSpeakerControlService.selectAudio(serverLevel.getServer(), serverLevel, fullKey, audioId, filename);
        if (ok) ServerSpeakerControlService.play(serverLevel.getServer(), serverLevel, fullKey);
        return ok;
    }

    public static boolean setLooping(Level level, BlockPos pos, boolean looping) {
        if (!(level instanceof ServerLevel serverLevel) || pos == null) return false;
        String fullKey = ServerSpeakerControlService.resolveFullStateKey(level, pos);
        if (fullKey == null) return false;
        return ServerSpeakerControlService.setLooping(serverLevel.getServer(), serverLevel, fullKey, looping);
    }

    public static boolean setVolume(Level level, BlockPos pos, float volume0to1) {
        if (!(level instanceof ServerLevel serverLevel) || pos == null) return false;
        String fullKey = ServerSpeakerControlService.resolveFullStateKey(level, pos);
        if (fullKey == null) return false;
        return ServerSpeakerControlService.setVolume(serverLevel.getServer(), serverLevel, fullKey, volume0to1);
    }

    public static boolean setRange(Level level, BlockPos pos, int blocks) {
        if (!(level instanceof ServerLevel serverLevel) || pos == null) return false;
        String fullKey = ServerSpeakerControlService.resolveFullStateKey(level, pos);
        if (fullKey == null) return false;
        return ServerSpeakerControlService.setRange(serverLevel.getServer(), serverLevel, fullKey, blocks);
    }

    public static boolean setRedstoneMode(Level level, BlockPos pos, RedstoneMode mode) {
        if (!(level instanceof ServerLevel serverLevel) || pos == null) return false;
        String fullKey = ServerSpeakerControlService.resolveFullStateKey(level, pos);
        if (fullKey == null) return false;
        return ServerSpeakerControlService.policyControl(serverLevel.getServer(), serverLevel, fullKey,
                com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.OP_REDSTONE_MODE,
                "", mode != null ? mode.ordinal() : 0, 0.0f, null);
    }

    public static boolean setAccessMode(Level level, BlockPos pos, SpeakerAccess access) {
        if (!(level instanceof ServerLevel serverLevel) || pos == null) return false;
        String fullKey = ServerSpeakerControlService.resolveFullStateKey(level, pos);
        if (fullKey == null) return false;
        return ServerSpeakerControlService.policyControl(serverLevel.getServer(), serverLevel, fullKey,
                com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.OP_ACCESS_MODE,
                "", access != null ? access.ordinal() : 0, 0.0f, null);
    }

    public static boolean setNetworkName(Level level, BlockPos pos, String name) {
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
        if (!(level instanceof ServerLevel serverLevel) || pos == null) return false;
        String fullKey = ServerSpeakerControlService.resolveFullStateKey(level, pos);
        if (fullKey == null) return false;
        return ServerSpeakerControlService.playlistControl(serverLevel.getServer(), serverLevel, fullKey, op, index, flag, audioId, filename);
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
            SpeakerState state = entry.getValue();
            if (state == null || !state.hasNetworkName()) continue;
            String status = !state.hasAudio() ? "empty"
                    : state.isPaused() ? "paused"
                    : state.isPlaying() ? "playing" : "stopped";
            String track = state.getAudioFilename().isEmpty() ? state.getAudioId() : state.getAudioFilename();
            result.putIfAbsent(state.getNetworkName(), status + ": " + track + " [" + entry.getKey() + "]");
        }
        return result;
    }

    /** Finds the first main-speaker position carrying the given network name in this dimension. */
    public static @Nullable BlockPos findNamedNetwork(Level level, String networkName) {
        if (networkName == null || networkName.isBlank()) return null;
        String prefix = ServerSpeakerRegistry.getDimension(level) + "/";
        for (Map.Entry<String, SpeakerState> entry : ServerSpeakerRegistry.getAllSpeakerStates().entrySet()) {
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

