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
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    private ServerPlaybackManager() {
    }

    /** Clears all subscription state; called when the server stops. */
    public static synchronized void resetForWorld() {
        subscriptions.clear();
    }

    // ------------------------------------------------------------------
    // Player lifecycle
    // ------------------------------------------------------------------

    /**
     * Drops every subscription of a disconnecting player. No stop packets are sent
     * because the client is gone; any stale hysteresis state is removed so a
     * reconnecting player starts with a clean subscription slate.
     */
    public static void handlePlayerQuit(UUID playerId) {
        subscriptions.removePlayer(playerId);
    }

    /**
     * Drops all subscriptions for a player changing dimensions. No stop packets are
     * sent because the client already clears its playback state on dimension change;
     * clearing the server subscriptions ensures that subsequent range scans start
     * with a clean slate and immediately send replacement Play packets when in range.
     */
    public static void handlePlayerDimensionChange(UUID playerId) {
        subscriptions.removePlayer(playerId);
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

        BlockPos pos = new BlockPos(sourceEmitter.location().getX(), sourceEmitter.location().getY(), sourceEmitter.location().getZ());
        SpeakerStateUpdatePacketS2C statePacket = new SpeakerStateUpdatePacketS2C(
                pos,
                sourceEmitter.speakerIdForPacket(),
                "stop",
                state.getAudioId(),
                state.getAudioFilename(),
                -1,
                state.isLooping()
        );
        PacketSenders.sendStateUpdateToAll(level, statePacket);

        for (ServerEmitter emitter : ServerSpeakerRegistry.getEmitters()) {
            if (sourceEmitter.fullStateKey().equals(emitter.fullStateKey())) {
                stopEmitter(server, emitter.location());
            }
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
        if (server == null || server.getTickCount() % SCAN_INTERVAL_TICKS != 0) return;
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

    private static void scanEmitter(MinecraftServer server, ServerLevel level, ServerEmitter emitter) {
        if (!emitter.active()) {
            stopEmitter(server, emitter.location());
            return;
        }

        SpeakerState state = ServerSpeakerRegistry.getSpeakerStateByFullKey(emitter.fullStateKey());
        boolean playing = state != null
                && state.isPlaying()
                && state.getAudioId() != null
                && !state.getAudioId().isEmpty();

        if (!playing) {
            stopEmitter(server, emitter.location());
            return;
        }

        // Natural EOF for non-looping audio, even while the speaker's chunk is unloaded.
        if (!state.isLooping() && state.getPlaybackStartTick() >= 0) {
            float elapsedSeconds = (level.getGameTime() - state.getPlaybackStartTick()) / 20.0f;
            AudioFileManager audioFileManager = SimplySpeakers.getAudioFileManager();
            if (audioFileManager != null) {
                AudioFileMetadata meta = audioFileManager.getManifest().get(state.getAudioId());
                if (meta != null && meta.getDurationSeconds() > 0.0f && elapsedSeconds >= meta.getDurationSeconds()) {
                    stopPlaybackForState(server, level, emitter, state);
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
        return new PlayAudioPacketS2C(
                new BlockPos(emitter.location().getX(), emitter.location().getY(), emitter.location().getZ()),
                emitter.speakerIdForPacket(),
                state.getAudioId(),
                state.getAudioFilename(),
                playbackPositionSeconds,
                state.isLooping(),
                (int) effectiveRange,
                maxVolume,
                dropoff);
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
    }
}
