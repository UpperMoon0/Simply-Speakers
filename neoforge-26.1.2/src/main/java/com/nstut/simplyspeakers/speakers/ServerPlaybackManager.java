package com.nstut.simplyspeakers.speakers;

import com.nstut.simplyspeakers.SimplySpeakers;
import dev.architectury.networking.NetworkManager;
import com.nstut.simplyspeakers.SpeakerSettings;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.audio.AudioFileManager;
import com.nstut.simplyspeakers.network.PlayAudioPacketS2C;
import com.nstut.simplyspeakers.network.SpeakerStateUpdatePacketS2C;
import com.nstut.simplyspeakers.network.StopAudioPacketS2C;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized server playback and listener management. Owns player-to-emitter
 * subscriptions so that range scanning and lifecycle handling are independent of
 * whether a speaker's chunk is loaded. Block entities only publish emitter
 * snapshots to {@link ServerSpeakerRegistry}; This manager performs the actual
 * {@code PlayAudioPacketS2C}/{@code StopAudioPacketS2C} dispatch.
 */
public final class ServerPlaybackManager {

    private static final int SCAN_INTERVAL_TICKS = 4;

    private static final PlaybackSubscriptions subscriptions = new PlaybackSubscriptions();

    /**
     * Natural-EOF reports for remote URL tracks, keyed by full state key. Each
     * entry also carries the playback generation and audio id it belongs to, so
     * stale reports from a previous session are never counted. A URL track has
     * no server-known duration, so playlist advancement happens once every
     * subscribed player of the state reports that the stream finished.
     */
    private static final Map<String, RemoteEofReports> pendingRemoteEof = new ConcurrentHashMap<>();

    private static final class RemoteEofReports {
        final int playbackGeneration;
        final String audioId;
        final Set<UUID> reported = ConcurrentHashMap.newKeySet();

        RemoteEofReports(int playbackGeneration, String audioId) {
            this.playbackGeneration = playbackGeneration;
            this.audioId = audioId;
        }

        boolean matches(int reportedGeneration, String reportedAudioId) {
            return playbackGeneration == reportedGeneration && audioId.equals(reportedAudioId);
        }
    }

    private ServerPlaybackManager() {
    }

    /** Clears all subscription state; called when the server stops. */
    public static synchronized void resetForWorld() {
        subscriptions.clear();
        pendingRemoteEof.clear();
    }

    // ------------------------------------------------------------------
    // Player lifecycle
    // ------------------------------------------------------------------

    public static void handlePlayerQuit(MinecraftServer server, UUID playerId) {
        subscriptions.removePlayer(playerId);
        reevaluateAllPendingRemoteEof(server);
    }

    public static void handlePlayerDimensionChange(MinecraftServer server, UUID playerId) {
        subscriptions.removePlayer(playerId);
        reevaluateAllPendingRemoteEof(server);
    }

    // ------------------------------------------------------------------
    // Emitter lifecycle
    // ------------------------------------------------------------------

    public static void stopEmitter(MinecraftServer server, SpeakerLocation location) {
        if (server == null || location == null) return;
        Set<UUID> players = subscriptions.removeEmitter(location);
        if (players == null || players.isEmpty()) return;
        StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(new BlockPos(location.getX(), location.getY(), location.getZ()));
        for (UUID playerId : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                PacketSenders.sendStop(player, stopPacket);
            }
        }
        reevaluateAllPendingRemoteEof(server);
    }

    public static void unregisterEmitter(MinecraftServer server, SpeakerLocation location) {
        if (location == null) return;
        if (server != null) stopEmitter(server, location);
        ServerSpeakerRegistry.removeEmitter(location);
    }

    public static void onEmitterActivated(MinecraftServer server, ServerEmitter emitter) {
        if (server == null || emitter == null) return;
        ServerLevel level = findLevel(server, emitter.location().dimension());
        if (level == null) return;
        scanEmitter(server, level, emitter);
    }

    public static void stopPlaybackForState(MinecraftServer server, ServerLevel level, ServerEmitter sourceEmitter, SpeakerState state) {
        if (server == null || sourceEmitter == null || state == null) return;
        state.setPlaying(false);
        state.setPlaybackStartTick(-1);
        ServerSpeakerRegistry.updateSpeakerStateByFullKey(sourceEmitter.fullStateKey(), state);
        pendingRemoteEof.remove(sourceEmitter.fullStateKey());

        BlockPos pos = new BlockPos(sourceEmitter.location().getX(), sourceEmitter.location().getY(), sourceEmitter.location().getZ());
        SpeakerStateUpdatePacketS2C statePacket = new SpeakerStateUpdatePacketS2C(
                pos,
                sourceEmitter.speakerIdForPacket(),
                "stop",
                state.getAudioId(),
                state.getAudioFilename(),
                -1,
                state.isLooping(),
                sourceEmitter.fullStateKey()
        );
        PacketSenders.sendStateUpdateToAll(level, statePacket);

        for (ServerEmitter emitter : ServerSpeakerRegistry.getEmitters()) {
            if (sourceEmitter.fullStateKey().equals(emitter.fullStateKey())) {
                stopEmitter(server, emitter.location());
            }
        }
    }

    private static void advancePlaylistOrStop(MinecraftServer server, ServerLevel level, ServerEmitter emitter, SpeakerState state) {
        if (state.hasPlaylist() && state.getPlaylist().size() > 0) {
            com.nstut.simplyspeakers.playlist.Playlist.Advance adv = state.getPlaylist().next();
            if (adv.hasTrack()) {
                com.nstut.simplyspeakers.playlist.PlaylistTrack nextTrack = adv.track();
                state.setAudioId(nextTrack.getAudioId());
                state.setAudioFilename(nextTrack.getFilename());
                beginNewPlaybackSession(emitter.fullStateKey());
                state.startPlaybackAt(level.getGameTime(), 0.0f);
                ServerSpeakerRegistry.updateSpeakerStateByFullKey(emitter.fullStateKey(), state);
                ServerSpeakerRegistry.markDirty();

                broadcastStateUpdate(level, emitter, state, "play");
                broadcastPlaylistSync(level, emitter, state);
                resyncState(server, level, emitter.fullStateKey());
                com.nstut.simplyspeakers.api.SpeakerEvents.fire(
                        com.nstut.simplyspeakers.api.SpeakerEvents.Type.TRACK_CHANGED,
                        emitter.fullStateKey(), state.getNetworkName(), state.getAudioId());
                return;
            }
        }
        stopPlaybackForState(server, level, emitter, state);
        com.nstut.simplyspeakers.api.SpeakerEvents.fire(
                com.nstut.simplyspeakers.api.SpeakerEvents.Type.FINISHED,
                emitter.fullStateKey(), state.getNetworkName(), state.getAudioId());
    }

    // ------------------------------------------------------------------
    // Remote URL track EOF
    // ------------------------------------------------------------------

    /** Clears pending EOF reports at a semantic playback boundary. */
    public static synchronized void beginNewPlaybackSession(String fullStateKey) {
        if (fullStateKey != null) pendingRemoteEof.remove(fullStateKey);
    }

    private record StateQuorum(Set<UUID> quorum, ServerEmitter emitter, ServerLevel level) {
    }

    private static StateQuorum computeStateQuorum(MinecraftServer server, String fullStateKey) {
        Set<UUID> quorum = new HashSet<>();
        ServerEmitter advanceEmitter = null;
        ServerLevel advanceLevel = null;
        for (ServerEmitter emitter : ServerSpeakerRegistry.getEmitters()) {
            if (!fullStateKey.equals(emitter.fullStateKey())) continue;
            quorum.addAll(subscriptions.getSubscribers(emitter.location()));
            if (advanceEmitter == null) {
                advanceLevel = findLevel(server, emitter.location().dimension());
                if (advanceLevel != null) advanceEmitter = emitter;
            }
        }
        if (advanceEmitter == null) return null;
        return new StateQuorum(quorum, advanceEmitter, advanceLevel);
    }

    private static void reevaluateRemoteEofQuorum(MinecraftServer server, String fullStateKey) {
        if (server == null || fullStateKey == null) return;
        RemoteEofReports reports = pendingRemoteEof.get(fullStateKey);
        if (reports == null || reports.reported.isEmpty()) return;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null || !state.isPlaying() || state.isPaused() || state.isLooping()
                || !com.nstut.simplyspeakers.audio.StreamTracks.isHttpAudioUrl(state.getAudioId())
                || !state.getAudioId().equals(reports.audioId)) {
            pendingRemoteEof.remove(fullStateKey, reports);
            return;
        }
        int currentGeneration = state.ensurePlaybackSessionGeneration();
        if (currentGeneration != reports.playbackGeneration) {
            pendingRemoteEof.remove(fullStateKey, reports);
            return;
        }
        StateQuorum target = computeStateQuorum(server, fullStateKey);
        if (target == null) return;
        if (RemoteEofQuorumEvaluator.shouldAdvance(reports.reported, target.quorum())
                && pendingRemoteEof.remove(fullStateKey, reports)) {
            advancePlaylistOrStop(server, target.level(), target.emitter(), state);
        }
    }

    private static void reevaluateAllPendingRemoteEof(MinecraftServer server) {
        if (server == null || pendingRemoteEof.isEmpty()) return;
        for (String key : pendingRemoteEof.keySet().toArray(new String[0])) {
            reevaluateRemoteEofQuorum(server, key);
        }
    }

    public static void handleRemoteStreamEofReport(ServerPlayer player, String fullStateKey, int playbackGeneration, String audioId) {
        if (player == null || audioId == null || audioId.isEmpty()) return;
        if (fullStateKey == null || fullStateKey.isEmpty()) return;
        if (!com.nstut.simplyspeakers.audio.StreamTracks.isHttpAudioUrl(audioId)) return;
        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        if (state == null || !state.isPlaying() || state.isPaused() || state.isLooping()) return;
        if (!audioId.equals(state.getAudioId())) return;
        if (!com.nstut.simplyspeakers.audio.StreamTracks.isHttpAudioUrl(state.getAudioId())) return;
        if (state.ensurePlaybackSessionGeneration() != playbackGeneration) return;

        StateQuorum target = computeStateQuorum(server, fullStateKey);
        if (target == null || target.quorum().isEmpty() || !target.quorum().contains(player.getUUID())) return;

        RemoteEofReports reports = pendingRemoteEof.compute(fullStateKey,
                (key, existing) -> existing != null && existing.matches(playbackGeneration, audioId)
                        ? existing
                        : new RemoteEofReports(playbackGeneration, audioId));
        reports.reported.add(player.getUUID());
        if (RemoteEofQuorumEvaluator.shouldAdvance(reports.reported, target.quorum())
                && pendingRemoteEof.remove(fullStateKey, reports)) {
            advancePlaylistOrStop(server, target.level(), target.emitter(), state);
        }
    }

    // ------------------------------------------------------------------
    // Periodic scanning
    // ------------------------------------------------------------------

    public static void serverTick(MinecraftServer server) {
        if (server == null) return;
        if (server.getTickCount() % 6000 == 0) {
            ServerSpeakerRegistry.flushDirty();
        }
        if (server.getTickCount() % SCAN_INTERVAL_TICKS != 0) return;
        for (ServerEmitter emitter : ServerSpeakerRegistry.getEmitters()) {
            ServerLevel level = findLevel(server, emitter.location().dimension());
            if (level != null) {
                scanEmitter(server, level, emitter);
            } else {
                stopEmitter(server, emitter.location());
            }
        }
    }

    private static ServerLevel findLevel(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (ServerSpeakerRegistry.getDimension(level).equals(dimension)) return level;
        }
        return null;
    }

    public static void resyncState(MinecraftServer server, ServerLevel level, String fullStateKey) {
        if (server == null || level == null || fullStateKey == null) return;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(fullStateKey);
        for (ServerEmitter emitter : ServerSpeakerRegistry.getEmitters()) {
            if (fullStateKey.equals(emitter.fullStateKey())) {
                if (state == null || !state.isPlaying() || state.isPaused() || !emitter.active()) {
                    stopEmitter(server, emitter.location());
                } else {
                    stopEmitter(server, emitter.location());
                    scanEmitter(server, level, emitter);
                }
            }
        }
    }

    private static void scanEmitter(MinecraftServer server, ServerLevel level, ServerEmitter emitter) {
        if (!emitter.active()) {
            stopEmitter(server, emitter.location());
            return;
        }

        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(emitter.fullStateKey());
        boolean playing = state != null
                && state.isPlaying()
                && !state.isPaused()
                && state.getAudioId() != null
                && !state.getAudioId().isEmpty();

        if (!playing) {
            stopEmitter(server, emitter.location());
            return;
        }

        if (!state.isLooping() && state.getPlaybackStartTick() >= 0) {
            float elapsedSeconds = state.getPlaybackPositionSeconds(level.getGameTime());
            AudioFileManager audioFileManager = SimplySpeakers.getAudioFileManager();
            if (audioFileManager != null) {
                AudioFileMetadata meta = audioFileManager.getManifest().get(state.getAudioId());
                if (meta != null && meta.getDurationSeconds() > 0.0f && elapsedSeconds >= meta.getDurationSeconds()) {
                    advancePlaylistOrStop(server, level, emitter, state);
                    return;
                }
            }
        }

        int maxRange = emitter.proxy() ? emitter.maxRange() : state.getMaxRange();
        float maxVolume = emitter.proxy() ? emitter.maxVolume() : state.getMaxVolume();
        float dropoff = emitter.proxy() ? emitter.dropoff() : state.getAudioDropoff();

        double effectiveRange = SpeakerSettings.effectiveRange(maxRange);
        Vec3 emitterPos = Vec3.atCenterOf(new BlockPos(emitter.location().getX(), emitter.location().getY(), emitter.location().getZ()));

        Set<UUID> subscribed = subscriptions.getSubscribers(emitter.location());
        List<ServerPlaybackPlanner.ListenerObservation> observations = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean sameDimension = ServerSpeakerRegistry.getDimension(player.level()).equals(emitter.location().dimension());
            double distanceSq = Double.MAX_VALUE;
            if (sameDimension) distanceSq = player.position().distanceToSqr(emitterPos);
            observations.add(new ServerPlaybackPlanner.ListenerObservation(player.getUUID(), sameDimension, true, distanceSq));
        }

        ServerPlaybackPlanner.ScanPlan plan = ServerPlaybackPlanner.plan(
                new ServerPlaybackPlanner.EmitterObservation(emitter.active(), playing, effectiveRange, subscribed),
                observations);

        AudioFileManager audioFileManager = SimplySpeakers.getAudioFileManager();
        for (UUID startId : plan.startListeners()) {
            ServerPlayer player = server.getPlayerList().getPlayer(startId);
            if (player == null) continue;
            if (audioFileManager != null) audioFileManager.grantPlaybackDownload(player, state.getAudioId());
            PacketSenders.sendPlay(player, buildPlayPacket(emitter, state, level, effectiveRange, maxVolume, dropoff));
            subscriptions.subscribe(startId, emitter.location());
        }

        for (UUID stopId : plan.stopListeners()) {
            ServerPlayer player = server.getPlayerList().getPlayer(stopId);
            if (player != null) {
                PacketSenders.sendStop(player, new StopAudioPacketS2C(new BlockPos(emitter.location().getX(), emitter.location().getY(), emitter.location().getZ())));
            }
            subscriptions.unsubscribe(stopId, emitter.location());
        }

        reevaluateRemoteEofQuorum(server, emitter.fullStateKey());
    }

    private static void broadcastStateUpdate(ServerLevel level, ServerEmitter emitter, SpeakerState state, String action) {
        SpeakerStateUpdatePacketS2C statePacket = new SpeakerStateUpdatePacketS2C(
                new BlockPos(emitter.location().getX(), emitter.location().getY(), emitter.location().getZ()),
                emitter.speakerIdForPacket(),
                action,
                state.getAudioId(),
                state.getAudioFilename(),
                state.getPlaybackStartTick(),
                state.isLooping(),
                emitter.fullStateKey()
        );
        PacketSenders.sendStateUpdateToAll(level, statePacket);
    }

    private static void broadcastPlaylistSync(ServerLevel level, ServerEmitter emitter, SpeakerState state) {
        if (state == null) return;
        com.nstut.simplyspeakers.playlist.Playlist pl = state.getPlaylist();
        List<String> audioIds = new ArrayList<>();
        List<String> filenames = new ArrayList<>();
        for (com.nstut.simplyspeakers.playlist.PlaylistTrack track : pl.getTracks()) {
            audioIds.add(track.getAudioId());
            filenames.add(track.getFilename());
        }
        int playingIndex = state.isPlaying() ? pl.getCurrentIndex() : -1;
        com.nstut.simplyspeakers.network.PlaylistSyncPacketS2C packet = new com.nstut.simplyspeakers.network.PlaylistSyncPacketS2C(
                new BlockPos(emitter.location().getX(), emitter.location().getY(), emitter.location().getZ()),
                emitter.fullStateKey(),
                audioIds,
                filenames,
                pl.getCurrentIndex(),
                pl.isShuffle(),
                pl.getRepeatMode().ordinal(),
                playingIndex,
                state.isPaused()
        );
        PacketSenders.sendPlaylistSyncToAll(level, packet);
    }

    private static PlayAudioPacketS2C buildPlayPacket(
            ServerEmitter emitter,
            SpeakerState state,
            ServerLevel level,
            double effectiveRange,
            float maxVolume,
            float dropoff) {
        float elapsedSeconds = state.getPlaybackPositionSeconds(level.getGameTime());
        if (elapsedSeconds < 0) elapsedSeconds = 0;
        float playbackPositionSeconds = elapsedSeconds;
        AudioFileManager audioFileManager = SimplySpeakers.getAudioFileManager();
        if (audioFileManager != null) {
            AudioFileMetadata meta = audioFileManager.getManifest().get(state.getAudioId());
            if (meta != null && meta.getDurationSeconds() > 0.0f && state.isLooping()) {
                playbackPositionSeconds = elapsedSeconds % meta.getDurationSeconds();
            }
        }
        PlayAudioPacketS2C packet = new PlayAudioPacketS2C(
                new BlockPos(emitter.location().getX(), emitter.location().getY(), emitter.location().getZ()),
                emitter.speakerIdForPacket(),
                state.getAudioId(),
                state.getAudioFilename(),
                playbackPositionSeconds,
                state.isLooping(),
                (int) effectiveRange,
                maxVolume,
                dropoff);
        if (state.getDirectionality() > 0.001f) {
            byte facing = emitter.directionalExtras() != null
                    ? emitter.directionalExtras().facingOrdinal()
                    : (byte) 2;
            packet.withExtras(new com.nstut.simplyspeakers.audio.DirectionalAudio.Extras(
                    state.getDirectionality(), state.getConeAngleDegrees(), state.getRearAttenuation(), facing));
        }
        if (com.nstut.simplyspeakers.audio.StreamTracks.isHttpAudioUrl(state.getAudioId())) {
            int generation = state.ensurePlaybackSessionGeneration();
            RemoteEofReports pending = pendingRemoteEof.get(emitter.fullStateKey());
            if (pending != null && !pending.matches(generation, state.getAudioId())) {
                pendingRemoteEof.remove(emitter.fullStateKey(), pending);
            }
            packet.withRemoteIdentity(emitter.fullStateKey(), generation);
        } else {
            pendingRemoteEof.remove(emitter.fullStateKey());
        }
        return packet;
    }

    public static Set<SpeakerLocation> getEmitterLocationsForPlayer(UUID playerId) {
        return subscriptions.getEmitterLocationsForPlayer(playerId);
    }

    public static Set<UUID> getSubscribers(SpeakerLocation location) {
        return subscriptions.getSubscribers(location);
    }

    private static final class PacketSenders {
        private PacketSenders() {
        }

        static void sendPlay(ServerPlayer player, PlayAudioPacketS2C packet) {
            NetworkManager.sendToPlayer(player, packet);
        }

        static void sendStop(ServerPlayer player, StopAudioPacketS2C packet) {
            NetworkManager.sendToPlayer(player, packet);
        }

        static void sendStateUpdateToAll(ServerLevel level, SpeakerStateUpdatePacketS2C packet) {
            NetworkManager.sendToPlayers(level.players(), packet);
        }

        static void sendPlaylistSyncToAll(ServerLevel level, com.nstut.simplyspeakers.network.PlaylistSyncPacketS2C packet) {
            NetworkManager.sendToPlayers(level.players(), packet);
        }
    }
}