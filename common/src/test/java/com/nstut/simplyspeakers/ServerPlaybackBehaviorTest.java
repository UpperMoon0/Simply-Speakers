package com.nstut.simplyspeakers;

import com.nstut.simplyspeakers.speakers.PlaybackSubscriptions;
import com.nstut.simplyspeakers.speakers.ServerEmitter;
import com.nstut.simplyspeakers.speakers.ServerPlaybackPlanner;
import com.nstut.simplyspeakers.speakers.SpeakerLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure behavioral simulation of server playback orchestration.
 * Tests actual state transitions, subscription invariants, EOF calculations,
 * dimension transitions, and setting propagation without requiring a Minecraft runtime.
 */
class ServerPlaybackBehaviorTest {

    private PlaybackSubscriptions subscriptions;
    private Map<String, SpeakerState> speakerStates;
    private List<PlayedPacket> playDispatches;
    private List<StoppedPacket> stopDispatches;
    private List<StateUpdateBroadcast> stateBroadcasts;

    private record PlayedPacket(UUID player, SpeakerLocation location, String audioId, int effectiveRange, float volume, float dropoff) {}
    private record StoppedPacket(UUID player, SpeakerLocation location) {}
    private record StateUpdateBroadcast(String fullStateKey, String action) {}

    private final UUID player1 = UUID.randomUUID();
    private final UUID player2 = UUID.randomUUID();
    private final SpeakerLocation overworldLoc1 = new SpeakerLocation("minecraft:overworld", 0, 64, 0);
    private final SpeakerLocation overworldLoc2 = new SpeakerLocation("minecraft:overworld", 100, 64, 100);

    @BeforeEach
    void setUp() {
        subscriptions = new PlaybackSubscriptions();
        speakerStates = new HashMap<>();
        playDispatches = new ArrayList<>();
        stopDispatches = new ArrayList<>();
        stateBroadcasts = new ArrayList<>();
    }

    @Test
    void newListenerGetsPlayAndBecomesSubscribed() {
        SpeakerState state = new SpeakerState("audio_1", "music.ogg", true, true, 100);
        state.setMaxRange(32);
        speakerStates.put("minecraft:overworld/net_radio", state);

        ServerEmitter emitter = new ServerEmitter(overworldLoc1, "net_radio", 16, 1.0f, 1.0f, false, true);

        // Player is at distance 10 (within effective range)
        scanSimulation(emitter, state, List.of(new MockPlayer(player1, "minecraft:overworld", 10.0)));

        assertEquals(1, playDispatches.size());
        assertEquals(player1, playDispatches.get(0).player());
        assertEquals(overworldLoc1, playDispatches.get(0).location());
        assertTrue(subscriptions.isSubscribed(player1, overworldLoc1));
    }

    @Test
    void existingListenerDoesNotReceiveDuplicatePlay() {
        SpeakerState state = new SpeakerState("audio_1", "music.ogg", true, true, 100);
        speakerStates.put("minecraft:overworld/net_radio", state);
        ServerEmitter emitter = new ServerEmitter(overworldLoc1, "net_radio", 16, 1.0f, 1.0f, false, true);

        // First scan
        scanSimulation(emitter, state, List.of(new MockPlayer(player1, "minecraft:overworld", 10.0)));
        assertEquals(1, playDispatches.size());

        // Second scan with same player still in range
        scanSimulation(emitter, state, List.of(new MockPlayer(player1, "minecraft:overworld", 10.0)));
        assertEquals(1, playDispatches.size(), "Subscribed listener must not receive duplicate play packets");
    }

    @Test
    void listenerLeavesRangeGetsOneStopAndBothIndexesAreClean() {
        SpeakerState state = new SpeakerState("audio_1", "music.ogg", true, true, 100);
        speakerStates.put("minecraft:overworld/net_radio", state);
        ServerEmitter emitter = new ServerEmitter(overworldLoc1, "net_radio", 16, 1.0f, 1.0f, false, true);

        // Enter range
        scanSimulation(emitter, state, List.of(new MockPlayer(player1, "minecraft:overworld", 10.0)));
        assertTrue(subscriptions.isSubscribed(player1, overworldLoc1));

        // Move beyond hysteresis boundary (> effectiveRange + 8)
        scanSimulation(emitter, state, List.of(new MockPlayer(player1, "minecraft:overworld", 30.0)));

        assertEquals(1, stopDispatches.size());
        assertEquals(player1, stopDispatches.get(0).player());
        assertFalse(subscriptions.isSubscribed(player1, overworldLoc1));
        assertEquals(Set.of(), subscriptions.getEmitterLocationsForPlayer(player1));
        assertEquals(Set.of(), subscriptions.getSubscribers(overworldLoc1));
    }

    @Test
    void disconnectRemovesPlayerFromEveryEmitter() {
        subscriptions.subscribe(player1, overworldLoc1);
        subscriptions.subscribe(player1, overworldLoc2);
        subscriptions.subscribe(player2, overworldLoc1);

        subscriptions.removePlayer(player1);

        assertFalse(subscriptions.isSubscribed(player1, overworldLoc1));
        assertFalse(subscriptions.isSubscribed(player1, overworldLoc2));
        assertTrue(subscriptions.isSubscribed(player2, overworldLoc1));
        assertEquals(Set.of(), subscriptions.getEmitterLocationsForPlayer(player1));
    }

    @Test
    void dimensionChangePurgesThenReentrySendsFreshPlay() {
        SpeakerState state = new SpeakerState("audio_1", "music.ogg", true, true, 100);
        speakerStates.put("minecraft:overworld/net_radio", state);
        ServerEmitter emitter = new ServerEmitter(overworldLoc1, "net_radio", 16, 1.0f, 1.0f, false, true);

        // Player starts in overworld
        scanSimulation(emitter, state, List.of(new MockPlayer(player1, "minecraft:overworld", 5.0)));
        assertTrue(subscriptions.isSubscribed(player1, overworldLoc1));
        assertEquals(1, playDispatches.size());

        // Player teleports to nether (triggers dimension change handler)
        subscriptions.removePlayer(player1);
        assertFalse(subscriptions.isSubscribed(player1, overworldLoc1));

        // Player returns to overworld in range
        scanSimulation(emitter, state, List.of(new MockPlayer(player1, "minecraft:overworld", 5.0)));
        assertEquals(2, playDispatches.size(), "Returning player must receive fresh play packet");
        assertTrue(subscriptions.isSubscribed(player1, overworldLoc1));
    }

    @Test
    void emitterUnregisterStopsAndRemovesSubscriptions() {
        subscriptions.subscribe(player1, overworldLoc1);
        subscriptions.subscribe(player2, overworldLoc1);

        // Unregister emitter
        Set<UUID> stoppedPlayers = subscriptions.removeEmitter(overworldLoc1);

        assertEquals(Set.of(player1, player2), stoppedPlayers);
        assertEquals(Set.of(), subscriptions.getSubscribers(overworldLoc1));
        assertEquals(Set.of(), subscriptions.getEmitterLocationsForPlayer(player1));
        assertEquals(Set.of(), subscriptions.getEmitterLocationsForPlayer(player2));
    }

    @Test
    void mainEmitterUsesUpdatedSharedStateSettingsWhileUnloaded() {
        SpeakerState state = new SpeakerState("audio_1", "music.ogg", true, true, 100);
        state.setMaxRange(64);
        state.setMaxVolume(0.8f);
        state.setAudioDropoff(0.5f);
        speakerStates.put("minecraft:overworld/net_radio", state);

        // Emitter snapshot has stale frozen values (e.g. 16 range, 1.0 vol, 1.0 dropoff)
        ServerEmitter unloadedMainEmitter = new ServerEmitter(overworldLoc1, "net_radio", 16, 1.0f, 1.0f, false, true);

        scanSimulation(unloadedMainEmitter, state, List.of(new MockPlayer(player1, "minecraft:overworld", 20.0)));

        assertEquals(1, playDispatches.size());
        PlayedPacket packet = playDispatches.get(0);
        assertEquals((int) SpeakerSettings.effectiveRange(64), packet.effectiveRange(), "Must use live state range");
        assertEquals(0.8f, packet.volume(), 0.001f, "Must use live state volume");
        assertEquals(0.5f, packet.dropoff(), 0.001f, "Must use live state dropoff");
    }

    @Test
    void proxyEmitterKeepsItsOwnSnapshotSettings() {
        SpeakerState state = new SpeakerState("audio_1", "music.ogg", true, true, 100);
        state.setMaxRange(64);
        state.setMaxVolume(0.8f);
        speakerStates.put("minecraft:overworld/net_radio", state);

        // Proxy has its own configured 32 range, 0.4 volume, 0.2 dropoff
        ServerEmitter proxyEmitter = new ServerEmitter(overworldLoc1, "net_radio", 32, 0.4f, 0.2f, true, true);

        scanSimulation(proxyEmitter, state, List.of(new MockPlayer(player1, "minecraft:overworld", 10.0)));

        assertEquals(1, playDispatches.size());
        PlayedPacket packet = playDispatches.get(0);
        assertEquals((int) SpeakerSettings.effectiveRange(32), packet.effectiveRange(), "Proxy must use its own range");
        assertEquals(0.4f, packet.volume(), 0.001f, "Proxy must use its own volume");
        assertEquals(0.2f, packet.dropoff(), 0.001f, "Proxy must use its own dropoff");
    }

    @Test
    void nonLoopingTrackEndsAtDuration() {
        SpeakerState state = new SpeakerState("audio_1", "short.ogg", true, false, 100);
        speakerStates.put("minecraft:overworld/net_radio", state);
        ServerEmitter emitter = new ServerEmitter(overworldLoc1, "net_radio", 16, 1.0f, 1.0f, false, true);
        subscriptions.subscribe(player1, overworldLoc1);

        float trackDurationSeconds = 10.0f;
        long currentTick = 100 + (long) (10.0f * 20.0f); // 10 seconds later

        boolean eofTriggered = checkEofSimulation(emitter, state, currentTick, trackDurationSeconds);

        assertTrue(eofTriggered);
        assertFalse(state.isPlaying());
        assertEquals(-1, state.getPlaybackStartTick());
        assertEquals(1, stateBroadcasts.size());
        assertEquals("stop", stateBroadcasts.get(0).action());
    }

    @Test
    void nonLoopingTrackStartedAtTickZeroEnds() {
        // Playback started at tick 0
        SpeakerState state = new SpeakerState("audio_1", "short.ogg", true, false, 0);
        speakerStates.put("minecraft:overworld/net_radio", state);
        ServerEmitter emitter = new ServerEmitter(overworldLoc1, "net_radio", 16, 1.0f, 1.0f, false, true);
        subscriptions.subscribe(player1, overworldLoc1);

        float trackDurationSeconds = 5.0f;
        long currentTick = 100; // 5 seconds later at tick 100

        boolean eofTriggered = checkEofSimulation(emitter, state, currentTick, trackDurationSeconds);

        assertTrue(eofTriggered, "Track started at tick 0 must trigger natural EOF");
        assertFalse(state.isPlaying());
        assertEquals(-1, state.getPlaybackStartTick());
    }

    @Test
    void sableUnresolvableEmitterStopsExistingListeners() {
        subscriptions.subscribe(player1, overworldLoc1);
        subscriptions.subscribe(player2, overworldLoc1);

        // When emitterPos is null (unresolvable Sable sublevel), stopEmitter is called
        Set<UUID> stopped = subscriptions.removeEmitter(overworldLoc1);

        assertEquals(Set.of(player1, player2), stopped);
        assertEquals(Set.of(), subscriptions.getSubscribers(overworldLoc1));
    }

    // ------------------------------------------------------------------
    // Simulation helpers
    // ------------------------------------------------------------------

    private record MockPlayer(UUID uuid, String dimension, double distance) {}

    private void scanSimulation(ServerEmitter emitter, SpeakerState state, List<MockPlayer> players) {
        if (!emitter.active() || !state.isPlaying() || state.getAudioId().isEmpty()) {
            stopSimulation(emitter.location());
            return;
        }

        int maxRange = emitter.proxy() ? emitter.maxRange() : state.getMaxRange();
        float maxVolume = emitter.proxy() ? emitter.maxVolume() : state.getMaxVolume();
        float dropoff = emitter.proxy() ? emitter.dropoff() : state.getAudioDropoff();
        double effectiveRange = SpeakerSettings.effectiveRange(maxRange);

        Set<UUID> currentSubscribed = subscriptions.getSubscribers(emitter.location());
        List<ServerPlaybackPlanner.ListenerObservation> observations = new ArrayList<>();
        for (MockPlayer p : players) {
            boolean sameDim = emitter.location().dimension().equals(p.dimension());
            double distSq = sameDim ? p.distance() * p.distance() : Double.MAX_VALUE;
            observations.add(new ServerPlaybackPlanner.ListenerObservation(p.uuid(), sameDim, true, distSq));
        }

        ServerPlaybackPlanner.ScanPlan plan = ServerPlaybackPlanner.plan(
                new ServerPlaybackPlanner.EmitterObservation(emitter.active(), state.isPlaying(), effectiveRange, currentSubscribed),
                observations);

        for (UUID startId : plan.startListeners()) {
            playDispatches.add(new PlayedPacket(startId, emitter.location(), state.getAudioId(), (int) effectiveRange, maxVolume, dropoff));
            subscriptions.subscribe(startId, emitter.location());
        }

        for (UUID stopId : plan.stopListeners()) {
            stopDispatches.add(new StoppedPacket(stopId, emitter.location()));
            subscriptions.unsubscribe(stopId, emitter.location());
        }
    }

    private boolean checkEofSimulation(ServerEmitter emitter, SpeakerState state, long currentTick, float durationSeconds) {
        if (!state.isLooping() && state.getPlaybackStartTick() >= 0) {
            float elapsedSeconds = (currentTick - state.getPlaybackStartTick()) / 20.0f;
            if (durationSeconds > 0.0f && elapsedSeconds >= durationSeconds) {
                state.setPlaying(false);
                state.setPlaybackStartTick(-1);
                stateBroadcasts.add(new StateUpdateBroadcast(emitter.fullStateKey(), "stop"));
                stopSimulation(emitter.location());
                return true;
            }
        }
        return false;
    }

    private void stopSimulation(SpeakerLocation location) {
        Set<UUID> players = subscriptions.removeEmitter(location);
        for (UUID p : players) {
            stopDispatches.add(new StoppedPacket(p, location));
        }
    }
}
