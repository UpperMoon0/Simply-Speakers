package com.nstut.simplyspeakers.speakers;

import com.nstut.simplyspeakers.SimplySpeakers;
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
 * snapshots to {@link ServerSpeakerRegistry}; this manager performs the actual
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

    /**
     * Drops every subscription of a disconnecting player. No stop packets are sent
     * because the client is gone; any stale hysteresis state is removed so a
     * reconnecting player starts with a clean subscription slate. Pending remote
     * EOF quorums are re-evaluated so a network whose last unreported listener
     * disconnected still advances instead of stalling forever.
     */
    public static void handlePlayerQuit(MinecraftServer server, UUID playerId) {
        subscriptions.removePlayer(playerId);
        reevaluateAllPendingRemoteEof(server);
    }

    /**
     * Drops all subscriptions for a player changing dimensions. No stop packets are
     * sent because the client already clears its playback state on dimension change;
     * clearing the server subscriptions ensures that subsequent range scans start
     * with a clean slate and immediately send replacement Play packets when in range.
     * Pending remote EOF quorums are re-evaluated for the same reason as
     * {@link #handlePlayerQuit}.
     */
    public static void handlePlayerDimensionChange(MinecraftServer server, UUID playerId) {
        subscriptions.removePlayer(playerId);
        reevaluateAllPendingRemoteEof(server);
    }

    // ------------------------------------------------------------------
    // Emitter lifecycle
    // ------------------------------------------------------------------

    /**
     * Sends stop packets to every subscriber of an emitter and clears the subscriptions.
     * Called when the emitter is destroyed, unlinked, powered off, or its network stops.
     */
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
        // The emitter's audience shrank (destroy, unlink, power off, unload):
        // remaining subscribers of its shared state may already all have reported EOF.
        reevaluateAllPendingRemoteEof(server);
    }

    /**
     * Unregisters an emitter snapshot and tears down its active subscriptions atomically.
     */
    public static void unregisterEmitter(MinecraftServer server, SpeakerLocation location) {
        if (location == null) return;
        if (server != null) {
            stopEmitter(server, location);
        }
        ServerSpeakerRegistry.removeEmitter(location);
    }

    /**
     * Immediately scans a single emitter, used so playback starts on activation without
     * waiting for the next periodic pass.
     */
    public static void onEmitterActivated(MinecraftServer server, ServerEmitter emitter) {
        if (server == null || emitter == null) return;
        ServerLevel level = findLevel(server, emitter.location().dimension());
        if (level == null) return;
        scanEmitter(server, level, emitter);
    }

    /**
     * Handles authoritative termination of a playback stream (e.g. natural EOF).
     * Updates the server state in {@link ServerSpeakerRegistry}, broadcasts
     * {@link SpeakerStateUpdatePacketS2C} to notify client registries and UI,
     * and stops all emitter subscriptions sharing this state.
     */
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

    /**
     * Advances the state's playlist to the next track, or stops playback and fires
     * the FINISHED event when the playlist is exhausted. Shared by the manifest
     * duration EOF path (local files) and the remote URL EOF report path.
     */
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

    /**
     * Computes the state-wide subscriber union across ALL emitters registered for
     * the given state, plus the emitter/level used to advance it. Shared by the
     * EOF report path and the churn re-evaluation path so quorum semantics can
     * never diverge.
     */
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

    /**
     * Re-evaluates pending remote EOF quorum after the subscriber set of a state
     * shrank (player quit, range exit, dimension change, emitter removal). If every
     * REMAINING subscriber has already reported EOF for the pending session, the
     * network advances or stops instead of waiting forever on a listener that is
     * gone. A no-op when nothing is pending or the session is stale.
     */
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

    /**
     * Re-evaluates every pending remote EOF session; used after events that can
     * shrink multiple states' audiences at once (player quit, dimension change,
     * emitter removal). Cheap no-op when nothing is pending.
     */
    private static void reevaluateAllPendingRemoteEof(MinecraftServer server) {
        if (server == null || pendingRemoteEof.isEmpty()) return;
        for (String key : pendingRemoteEof.keySet().toArray(new String[0])) {
            reevaluateRemoteEofQuorum(server, key);
        }
    }

    /**
     * Handles a client report that a remote HTTP(S) stream reached natural EOF.
     * The report targets the shared state by the server-assigned full state key
     * and is only honored when the state is currently playing the reported
     * (non-looping) URL track with the reported playback generation. Quorum is
     * evaluated against the UNION of subscriber sets across ALL emitters
     * registered for that state, so linked speakers cannot advance the network
     * based on one emitter's audience alone. Unlike transport controls this can
     * only end or advance a track that has actually finished streaming on the
     * reporting clients.
     */
    public static void handleRemoteStreamEofReport(ServerPlayer player, String fullStateKey, int playbackGeneration, String audioId) {
        if (player == null || audioId == null || audioId.isEmpty()) return;
        if (fullStateKey == null || fullStateKey.isEmpty()) return;
        if (!com.nstut.simplyspeakers.audio.StreamTracks.isHttpAudioUrl(audioId)) return;
        MinecraftServer server = player.getServer();
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

    /**
     * Server-tick entry point. Throttled to {@link #SCAN_INTERVAL_TICKS}; iterates all
     * emitter snapshots against all online players, so listener entry/exit does not
     * depend on speaker chunks ticking.
     */
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
                // The emitter's dimension is not loaded; drop subscriptions so a
                // later re-registration starts with a clean slate.
                stopEmitter(server, emitter.location());
            }
        }
    }

    private static ServerLevel findLevel(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (ServerSpeakerRegistry.getDimension(level).equals(dimension)) {
                return level;
            }
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

        // Natural EOF for non-looping audio, even while the speaker's chunk is unloaded.
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

        // For non-proxy speakers, derive settings from live SpeakerState to prevent stale values when unloaded
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
            if (sameDimension) {
                distanceSq = player.position().distanceToSqr(emitterPos);
            }
            observations.add(new ServerPlaybackPlanner.ListenerObservation(player.getUUID(), sameDimension, true, distanceSq));
        }

        ServerPlaybackPlanner.ScanPlan plan = ServerPlaybackPlanner.plan(
                new ServerPlaybackPlanner.EmitterObservation(emitter.active(), playing, effectiveRange, subscribed),
                observations);

        for (UUID removeId : plan.stopListeners()) {
            subscriptions.unsubscribe(removeId, emitter.location());
            ServerPlayer player = server.getPlayerList().getPlayer(removeId);
            if (player != null) {
                PacketSenders.sendStop(player, new StopAudioPacketS2C(
                        new BlockPos(emitter.location().getX(), emitter.location().getY(), emitter.location().getZ())));
            }
        }

        if (!plan.startListeners().isEmpty()) {
            PlayAudioPacketS2C packet = buildPlayPacket(emitter, state, level, effectiveRange, maxVolume, dropoff);
            for (UUID addId : plan.startListeners()) {
                subscriptions.subscribe(addId, emitter.location());
                ServerPlayer player = server.getPlayerList().getPlayer(addId);
                if (player != null) {
                    AudioFileManager audioFileManager = SimplySpeakers.getAudioFileManager();
                    if (audioFileManager != null) audioFileManager.grantPlaybackDownload(player, state.getAudioId());
                    PacketSenders.sendPlay(player, packet);
                }
            }
        }

        // Range exits shrink the state's audience: if every remaining listener of
        // this state already reported remote EOF, advance now instead of stalling.
        reevaluateRemoteEofQuorum(server, emitter.fullStateKey());
    }

    private static void broadcastStateUpdate(ServerLevel level, ServerEmitter emitter, SpeakerState state, String action) {
        SpeakerStateUpdatePacketS2C packet = new SpeakerStateUpdatePacketS2C(
                new BlockPos(emitter.location().getX(), emitter.location().getY(), emitter.location().getZ()),
                emitter.speakerIdForPacket(),
                action,
                state.getAudioId(),
                state.getAudioFilename(),
                state.getPlaybackStartTick(),
                state.isLooping(),
                emitter.fullStateKey()
        );
        PacketSenders.sendStateUpdateToAll(level, packet);
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
            // Directionality parameters always come from the live shared state so policy
            // changes reach existing listeners on resync; only the physical facing is
            // per-emitter snapshot data. This also prevents stale emitter snapshots from
            // overriding the network's current directionality (proxy fallback fix).
            byte facing = emitter.directionalExtras() != null
                    ? emitter.directionalExtras().facingOrdinal()
                    : (byte) 2; // NORTH
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

    // ------------------------------------------------------------------
    // Subscription queries
    // ------------------------------------------------------------------

    public static Set<SpeakerLocation> getEmitterLocationsForPlayer(UUID playerId) {
        return subscriptions.getEmitterLocationsForPlayer(playerId);
    }

    public static Set<UUID> getSubscribers(SpeakerLocation location) {
        return subscriptions.getSubscribers(location);
    }

    /**
     * Version-neutral packet dispatch seam so the rest of the manager can be kept
     * identical across loader versions.
     */
    private static final class PacketSenders {
        private PacketSenders() {
        }

        static void sendPlay(ServerPlayer player, PlayAudioPacketS2C packet) {
            com.nstut.simplyspeakers.network.PacketRegistries.CHANNEL.sendToPlayer(player, packet);
        }

        static void sendStop(ServerPlayer player, StopAudioPacketS2C packet) {
            com.nstut.simplyspeakers.network.PacketRegistries.CHANNEL.sendToPlayer(player, packet);
        }

        static void sendStateUpdateToAll(ServerLevel level, SpeakerStateUpdatePacketS2C packet) {
            com.nstut.simplyspeakers.network.PacketRegistries.CHANNEL.sendToPlayers(level.players(), packet);
        }

        static void sendPlaylistSyncToAll(ServerLevel level, com.nstut.simplyspeakers.network.PlaylistSyncPacketS2C packet) {
            com.nstut.simplyspeakers.network.PacketRegistries.CHANNEL.sendToPlayers(level.players(), packet);
        }
    }
}