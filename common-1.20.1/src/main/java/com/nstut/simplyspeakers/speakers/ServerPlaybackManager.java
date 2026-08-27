package com.nstut.simplyspeakers.speakers;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerSettings;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.audio.AudioFileManager;
import com.nstut.simplyspeakers.network.PlayAudioPacketS2C;
import com.nstut.simplyspeakers.network.StopAudioPacketS2C;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
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

    /** player UUID -> emitter locations that player is currently listening to. */
    private static final Map<UUID, Set<SpeakerLocation>> playerToEmitters = new ConcurrentHashMap<>();
    /** emitter location -> player UUIDs currently listening to that emitter. */
    private static final Map<SpeakerLocation, Set<UUID>> emitterToPlayers = new ConcurrentHashMap<>();

    private ServerPlaybackManager() {
    }

    /** Clears all subscription state; called when the server stops. */
    public static synchronized void resetForWorld() {
        playerToEmitters.clear();
        emitterToPlayers.clear();
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
        if (playerId == null) return;
        Set<SpeakerLocation> locations = playerToEmitters.remove(playerId);
        if (locations == null) return;
        for (SpeakerLocation location : locations) {
            Set<UUID> players = emitterToPlayers.get(location);
            if (players != null) {
                players.remove(playerId);
                if (players.isEmpty()) emitterToPlayers.remove(location, players);
            }
        }
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
        Set<UUID> players = emitterToPlayers.remove(location);
        if (players == null || players.isEmpty()) return;
        StopAudioPacketS2C stopPacket = new StopAudioPacketS2C(new BlockPos(location.getX(), location.getY(), location.getZ()));
        for (UUID playerId : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                PacketSenders.sendStop(player, stopPacket);
            }
            Set<SpeakerLocation> locations = playerToEmitters.get(playerId);
            if (locations != null) {
                locations.remove(location);
                if (locations.isEmpty()) playerToEmitters.remove(playerId, locations);
            }
        }
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
        if (!state.isLooping() && state.getPlaybackStartTick() > 0) {
            float elapsedSeconds = (level.getGameTime() - state.getPlaybackStartTick()) / 20.0f;
            AudioFileManager audioFileManager = SimplySpeakers.getAudioFileManager();
            if (audioFileManager != null) {
                AudioFileMetadata meta = audioFileManager.getManifest().get(state.getAudioId());
                if (meta != null && meta.getDurationSeconds() > 0.0f && elapsedSeconds >= meta.getDurationSeconds()) {
                    state.setPlaying(false);
                    state.setPlaybackStartTick(-1);
                    ServerSpeakerRegistry.updateSpeakerStateByFullKey(emitter.fullStateKey(), state);
                    stopEmitter(server, emitter.location());
                    return;
                }
            }
        }

        double effectiveRange = SpeakerSettings.effectiveRange(emitter.maxRange());
        Vec3 emitterPos = Vec3.atCenterOf(new BlockPos(emitter.location().getX(), emitter.location().getY(), emitter.location().getZ()));

        Set<UUID> subscribed = emitterToPlayers.getOrDefault(emitter.location(), Set.of());
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
            PacketSenders.sendPlay(player, buildPlayPacket(emitter, state, level, effectiveRange));
            subscribe(startId, emitter.location());
        }

        for (UUID stopId : plan.stopListeners()) {
            ServerPlayer player = server.getPlayerList().getPlayer(stopId);
            if (player != null) {
                PacketSenders.sendStop(player, new StopAudioPacketS2C(new BlockPos(emitter.location().getX(), emitter.location().getY(), emitter.location().getZ())));
            }
            unsubscribe(stopId, emitter.location());
        }
    }

    private static PlayAudioPacketS2C buildPlayPacket(ServerEmitter emitter, SpeakerState state, ServerLevel level, double effectiveRange) {
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
                emitter.maxVolume(),
                emitter.dropoff());
    }

    // ------------------------------------------------------------------
    // Subscription indexes
    // ------------------------------------------------------------------

    private static void subscribe(UUID playerId, SpeakerLocation location) {
        emitterToPlayers.computeIfAbsent(location, k -> ConcurrentHashMap.newKeySet()).add(playerId);
        playerToEmitters.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(location);
    }

    private static void unsubscribe(UUID playerId, SpeakerLocation location) {
        Set<UUID> players = emitterToPlayers.get(location);
        if (players != null) {
            players.remove(playerId);
            if (players.isEmpty()) emitterToPlayers.remove(location, players);
        }
        Set<SpeakerLocation> locations = playerToEmitters.get(playerId);
        if (locations != null) {
            locations.remove(location);
            if (locations.isEmpty()) playerToEmitters.remove(playerId, locations);
        }
    }

    public static Set<SpeakerLocation> getEmitterLocationsForPlayer(UUID playerId) {
        Set<SpeakerLocation> locations = playerToEmitters.get(playerId);
        return locations != null ? Set.copyOf(locations) : Set.of();
    }

    public static Set<UUID> getSubscribers(SpeakerLocation location) {
        Set<UUID> players = emitterToPlayers.get(location);
        return players != null ? Set.copyOf(players) : Set.of();
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
    }
}
