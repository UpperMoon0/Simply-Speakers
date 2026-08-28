package com.nstut.simplyspeakers.api;

import com.nstut.simplyspeakers.RedstoneMode;
import com.nstut.simplyspeakers.SpeakerAccess;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.playlist.RepeatMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Public Java API for controlling speaker networks from other mods, KubeJS
 * scripts, or automation bridges. All operations run through the authoritative
 * ServerSpeakerRegistry rather than touching block entities directly.
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

    public static boolean play(Level level, BlockPos pos) {
        return applyTransport(level, pos, ACTION_PLAY);
    }

    public static boolean pause(Level level, BlockPos pos) {
        return applyTransport(level, pos, ACTION_PAUSE);
    }

    public static boolean togglePause(Level level, BlockPos pos) {
        return applyTransport(level, pos, ACTION_TOGGLE);
    }

    public static boolean stop(Level level, BlockPos pos) {
        return applyTransport(level, pos, ACTION_STOP);
    }

    public static boolean restart(Level level, BlockPos pos) {
        return applyTransport(level, pos, ACTION_RESTART);
    }

    public static boolean next(Level level, BlockPos pos) {
        return applyTransport(level, pos, ACTION_NEXT);
    }

    public static boolean previous(Level level, BlockPos pos) {
        return applyTransport(level, pos, ACTION_PREVIOUS);
    }

    public static boolean seek(Level level, BlockPos pos, float seconds) {
        return applyTransport(level, pos, ACTION_SEEK, Math.max(0.0f, seconds));
    }

    /** Applies a raw transport action byte. */
    public static boolean applyTransport(Level level, BlockPos pos, byte action) {
        return applyTransport(level, pos, action, 0.0f);
    }

    public static boolean applyTransport(Level level, BlockPos pos, byte action, float seekSeconds) {
        if (level == null || level.isClientSide() || pos == null) return false;
        if (level.getBlockEntity(pos) instanceof com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity speaker) {
            speaker.transportAction(level, action, seekSeconds);
            return true;
        }
        String fullKey = com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.getStateKey(level, pos);
        if (fullKey == null) return false;
        SpeakerState state = com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.getSpeakerStateByFullKey(fullKey);
        if (state == null) return false;

        long gameTime = level.getGameTime();
        float duration = trackDuration(state);
        switch (action) {
            case ACTION_PLAY -> {
                if (state.isPaused()) state.resumeAt(gameTime);
                else state.startPlaybackAt(gameTime, 0.0f);
                state.setPlaying(true);
            }
            case ACTION_PAUSE -> {
                if (state.isPlaying() && !state.isPaused()) state.pauseAt(gameTime);
            }
            case ACTION_TOGGLE -> {
                if (state.isPlaying()) {
                    if (state.isPaused()) state.resumeAt(gameTime);
                    else state.pauseAt(gameTime);
                } else {
                    state.startPlaybackAt(gameTime, 0.0f);
                    state.setPlaying(true);
                }
            }
            case ACTION_STOP -> {
                state.setPlaying(false);
                state.setPlaybackStartTick(-1);
                state.setPauseOffsetSeconds(0.0f);
            }
            case ACTION_RESTART -> {
                state.seekTo(0.0f, gameTime, duration);
                if (state.isPlaying() && !state.isPaused()) state.startPlaybackAt(gameTime, 0.0f);
            }
            case ACTION_NEXT -> {
                if (state.hasPlaylist()) {
                    var adv = state.getPlaylist().next();
                    if (adv.hasTrack()) {
                        state.setAudioId(adv.track().getAudioId());
                        state.setAudioFilename(adv.track().getFilename());
                        state.startPlaybackAt(gameTime, 0.0f);
                    } else {
                        state.setPlaying(false);
                        state.setPlaybackStartTick(-1);
                        state.setPauseOffsetSeconds(0.0f);
                    }
                }
            }
            case ACTION_PREVIOUS -> {
                if (state.hasPlaylist()) {
                    var adv = state.getPlaylist().previous();
                    if (adv.hasTrack()) {
                        state.setAudioId(adv.track().getAudioId());
                        state.setAudioFilename(adv.track().getFilename());
                        state.startPlaybackAt(gameTime, 0.0f);
                    } else {
                        state.setPlaying(false);
                        state.setPlaybackStartTick(-1);
                        state.setPauseOffsetSeconds(0.0f);
                    }
                }
            }
            case ACTION_SEEK -> state.seekTo(seekSeconds, gameTime, duration);
            default -> { return false; }
        }
        com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.updateSpeakerStateByFullKey(fullKey, state);
        com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.saveRegistry();
        broadcastStateChange(level, pos, state);
        return true;
    }

    /** Selects a track by audio id and starts it immediately. */
    public static boolean setTrack(Level level, BlockPos pos, String audioId, String filename) {
        if (withSpeaker(level, pos, speaker -> {
            speaker.selectAndPlay(audioId, filename);
            return true;
        })) return true;

        return mutateUnloadedState(level, pos, state -> {
            state.setAudioId(audioId != null ? audioId : "");
            state.setAudioFilename(filename != null ? filename : "");
            state.startPlaybackAt(level.getGameTime(), 0.0f);
            state.setPlaying(true);
        });
    }

    public static boolean setLooping(Level level, BlockPos pos, boolean looping) {
        if (withSpeaker(level, pos, speaker -> {
            speaker.setLooping(looping);
            return true;
        })) return true;
        return mutateUnloadedState(level, pos, state -> state.setLooping(looping));
    }

    public static boolean setVolume(Level level, BlockPos pos, float volume0to1) {
        if (withSpeaker(level, pos, speaker -> {
            speaker.setMaxVolume(volume0to1);
            return true;
        })) return true;
        return mutateUnloadedState(level, pos, state -> state.setMaxVolume(volume0to1));
    }

    public static boolean setRange(Level level, BlockPos pos, int blocks) {
        if (withSpeaker(level, pos, speaker -> {
            speaker.setMaxRange(blocks);
            return true;
        })) return true;
        return mutateUnloadedState(level, pos, state -> state.setMaxRange(blocks));
    }

    public static boolean setRedstoneMode(Level level, BlockPos pos, RedstoneMode mode) {
        if (withSpeaker(level, pos, speaker -> {
            speaker.setRedstoneMode(mode);
            return true;
        })) return true;
        return mutateUnloadedState(level, pos, state -> state.setRedstoneMode(mode));
    }

    public static boolean setAccessMode(Level level, BlockPos pos, SpeakerAccess access) {
        if (withSpeaker(level, pos, speaker -> {
            speaker.setAccessMode(access);
            return true;
        })) return true;
        return mutateUnloadedState(level, pos, state -> state.setAccessMode(access));
    }

    public static boolean setNetworkName(Level level, BlockPos pos, String name) {
        if (withSpeaker(level, pos, speaker -> {
            speaker.setNetworkName(name);
            return true;
        })) return true;
        return mutateUnloadedState(level, pos, state -> state.setNetworkName(name));
    }

    public static boolean playlistAdd(Level level, BlockPos pos, String audioId, String filename) {
        return playlistOp(level, pos, 0, -1, false, audioId, filename);
    }

    public static boolean playlistRemove(Level level, BlockPos pos, String audioId) {
        return playlistOp(level, pos, 1, -1, false, audioId, "");
    }

    public static boolean playlistClear(Level level, BlockPos pos) {
        return playlistOp(level, pos, 5, -1, false, "", "");
    }

    public static boolean playlistSetShuffle(Level level, BlockPos pos, boolean shuffle) {
        return playlistOp(level, pos, 7, -1, shuffle, "", "");
    }

    public static boolean playlistSetRepeat(Level level, BlockPos pos, RepeatMode mode) {
        return playlistOp(level, pos, 8, mode != null ? mode.ordinal() : 0, false, "", "");
    }

    public static boolean playlistQueueNext(Level level, BlockPos pos, String audioId) {
        return playlistOp(level, pos, 6, -1, false, audioId, "");
    }

    // Playlist op ordinals mirror PlaylistControlPacketC2S OP_* constants.
    private static boolean playlistOp(Level level, BlockPos pos, int op, int index,
                                      boolean flag, String audioId, String filename) {
        if (withSpeaker(level, pos, speaker -> {
            speaker.playlistControl(level, (byte) op, index, flag, audioId, filename);
            return true;
        })) return true;

        return mutateUnloadedState(level, pos, state -> {
            var playlist = state.getPlaylist();
            switch (op) {
                case 0 -> { if (audioId != null && !audioId.isEmpty()) playlist.add(audioId, filename); }
                case 1 -> { if (audioId != null && !audioId.isEmpty()) playlist.removeByAudioId(audioId); }
                case 5 -> playlist.clear();
                case 6 -> { if (audioId != null && !audioId.isEmpty()) playlist.queueNext(audioId); }
                case 7 -> playlist.setShuffle(flag);
                case 8 -> playlist.setRepeatMode(RepeatMode.values()[Math.max(0, Math.min(RepeatMode.values().length - 1, index))]);
                default -> { }
            }
        });
    }

    private interface StateMutator {
        void mutate(SpeakerState state);
    }

    private static boolean mutateUnloadedState(Level level, BlockPos pos, StateMutator mutator) {
        if (level == null || level.isClientSide() || pos == null) return false;
        String fullKey = com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.getStateKey(level, pos);
        if (fullKey == null) return false;
        SpeakerState state = com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.getSpeakerStateByFullKey(fullKey);
        if (state == null) return false;
        mutator.mutate(state);
        com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.updateSpeakerStateByFullKey(fullKey, state);
        com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.saveRegistry();
        broadcastStateChange(level, pos, state);
        return true;
    }

    private static float trackDuration(SpeakerState state) {
        var fm = com.nstut.simplyspeakers.SimplySpeakers.getAudioFileManager();
        if (fm == null) return 0.0f;
        var meta = fm.getManifest().get(state.getAudioId());
        return meta != null ? meta.getDurationSeconds() : 0.0f;
    }

    private static void broadcastStateChange(Level level, BlockPos pos, SpeakerState state) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        String action = (state.isPlaying() && !state.isPaused()) ? "play" : "stop";
        String stateKey = com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.getStateKey(level, pos);
        String speakerId = "";
        if (stateKey != null && stateKey.contains("/")) {
            String sub = stateKey.substring(stateKey.indexOf('/') + 1);
            if (sub.startsWith("net_")) speakerId = sub.substring("net_".length());
        }
        com.nstut.simplyspeakers.network.SpeakerStateUpdatePacketS2C packet =
                new com.nstut.simplyspeakers.network.SpeakerStateUpdatePacketS2C(
                        pos, speakerId, action, state.getAudioId(), state.getAudioFilename(),
                        state.getPlaybackStartTick(), state.isLooping());
        com.nstut.simplyspeakers.network.PacketRegistries.CHANNEL.sendToPlayers(serverLevel.players(), packet);

        if (!state.isPlaying() || state.isPaused()) {
            com.nstut.simplyspeakers.network.StopAudioPacketS2C stopPacket =
                    new com.nstut.simplyspeakers.network.StopAudioPacketS2C(pos);
            for (net.minecraft.server.level.ServerPlayer player : serverLevel.players()) {
                com.nstut.simplyspeakers.network.PacketRegistries.CHANNEL.sendToPlayer(player, stopPacket);
            }
        }
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
        SpeakerState state = com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.getSpeakerStateByPos(level, pos);
        if (state != null) return state;
        if (level.getBlockEntity(pos) instanceof com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity speaker) {
            return speaker.getSpeakerState();
        }
        return null;
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
        for (Map.Entry<String, SpeakerState> entry : com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.getAllSpeakerStates().entrySet()) {
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
        String prefix = com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.getDimension(level) + "/";
        for (Map.Entry<String, SpeakerState> entry : com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.getAllSpeakerStates().entrySet()) {
            SpeakerState state = entry.getValue();
            if (state == null || !networkName.equalsIgnoreCase(state.getNetworkName())) continue;
            if (!entry.getKey().startsWith(prefix)) continue;
            String stateKey = entry.getKey().substring(prefix.length());
            Set<BlockPos> found = com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry
                    .getSpeakerPositions(level, stateKey);
            if (!found.isEmpty()) return found.iterator().next();
        }
        return null;
    }

    private interface SpeakerConsumer {
        boolean accept(com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity speaker);
    }

    private static boolean withSpeaker(Level level, BlockPos pos, SpeakerConsumer consumer) {
        if (level == null || level.isClientSide() || pos == null) return false;
        if (!(level.getBlockEntity(pos) instanceof com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity speaker)) {
            return false;
        }
        try {
            return consumer.accept(speaker);
        } catch (Exception e) {
            com.nstut.simplyspeakers.SimplySpeakers.LOGGER.error("Speaker API call failed at {}", pos, e);
            return false;
        }
    }
}
