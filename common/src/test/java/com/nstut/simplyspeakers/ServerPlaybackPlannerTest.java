package com.nstut.simplyspeakers;

import com.nstut.simplyspeakers.speakers.ServerPlaybackPlanner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerPlaybackPlannerTest {
    private static final UUID LISTENER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_LISTENER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final double RANGE = 16.0;

    @Test
    void unloadedChunkEmitterContinuesStreamingToInRangePlayers() {
        // An emitter whose block entity chunk is unloaded is no longer ticking, but its
        // snapshot stays active and the authoritative state stays playing. The central
        // scan must still start listeners that come into range.
        ServerPlaybackPlanner.ScanPlan plan = ServerPlaybackPlanner.plan(
                new ServerPlaybackPlanner.EmitterObservation(true, true, RANGE, Set.of()),
                List.of(new ServerPlaybackPlanner.ListenerObservation(LISTENER, true, true, 4.0)));

        assertEquals(Set.of(LISTENER), plan.startListeners());
        assertTrue(plan.stopListeners().isEmpty());
        assertFalse(plan.stopEmitter());
    }

    @Test
    void listenerExitUsesHysteresisMargin() {
        Set<UUID> subscribed = Set.of(LISTENER);

        // Just past the edge: within the 2-block exit margin, keep listening.
        ServerPlaybackPlanner.ScanPlan withinMargin = ServerPlaybackPlanner.plan(
                new ServerPlaybackPlanner.EmitterObservation(true, true, RANGE, subscribed),
                List.of(new ServerPlaybackPlanner.ListenerObservation(LISTENER, true, true,
                        (RANGE + 1) * (RANGE + 1))));
        assertTrue(withinMargin.stopListeners().isEmpty());

        // Beyond the margin: stop and unsubscribe.
        ServerPlaybackPlanner.ScanPlan beyondMargin = ServerPlaybackPlanner.plan(
                new ServerPlaybackPlanner.EmitterObservation(true, true, RANGE, subscribed),
                List.of(new ServerPlaybackPlanner.ListenerObservation(LISTENER, true, true,
                        (RANGE + 3) * (RANGE + 3))));
        assertEquals(Set.of(LISTENER), beyondMargin.stopListeners());
    }

    @Test
    void dimensionChangeStopsSubscription() {
        ServerPlaybackPlanner.ScanPlan plan = ServerPlaybackPlanner.plan(
                new ServerPlaybackPlanner.EmitterObservation(true, true, RANGE, Set.of(LISTENER)),
                List.of(new ServerPlaybackPlanner.ListenerObservation(LISTENER, false, true, 1.0)));

        assertEquals(Set.of(LISTENER), plan.stopListeners());
        assertTrue(plan.startListeners().isEmpty());
        assertFalse(plan.stopEmitter());
    }

    @Test
    void disconnectedPlayerIsSilentlyUnsubscribed() {
        ServerPlaybackPlanner.ScanPlan plan = ServerPlaybackPlanner.plan(
                new ServerPlaybackPlanner.EmitterObservation(true, true, RANGE, Set.of(LISTENER)),
                List.of(new ServerPlaybackPlanner.ListenerObservation(LISTENER, true, false, 1.0)));

        assertEquals(Set.of(LISTENER), plan.stopListeners());
        assertFalse(plan.stopEmitter());
    }

    @Test
    void inactiveEmitterStopsAllSubscribers() {
        ServerPlaybackPlanner.ScanPlan plan = ServerPlaybackPlanner.plan(
                new ServerPlaybackPlanner.EmitterObservation(false, true, RANGE, Set.of(LISTENER, OTHER_LISTENER)),
                List.of(new ServerPlaybackPlanner.ListenerObservation(LISTENER, true, true, 1.0)));

        assertTrue(plan.startListeners().isEmpty());
        assertEquals(Set.of(LISTENER, OTHER_LISTENER), plan.stopListeners());
        assertTrue(plan.stopEmitter());
    }

    @Test
    void inactiveEmitterWithNoSubscribersRequiresNoStopPackets() {
        ServerPlaybackPlanner.ScanPlan plan = ServerPlaybackPlanner.plan(
                new ServerPlaybackPlanner.EmitterObservation(false, false, RANGE, Set.of()),
                List.of(new ServerPlaybackPlanner.ListenerObservation(LISTENER, true, true, 1.0)));

        assertTrue(plan.startListeners().isEmpty());
        assertTrue(plan.stopListeners().isEmpty());
        assertFalse(plan.stopEmitter());
    }

    @Test
    void stoppedNetworkStateStopsAllSubscribers() {
        ServerPlaybackPlanner.ScanPlan plan = ServerPlaybackPlanner.plan(
                new ServerPlaybackPlanner.EmitterObservation(true, false, RANGE, Set.of(LISTENER)),
                List.of(new ServerPlaybackPlanner.ListenerObservation(LISTENER, true, true, 1.0),
                        new ServerPlaybackPlanner.ListenerObservation(OTHER_LISTENER, true, true, 2.0)));

        assertEquals(Set.of(LISTENER), plan.stopListeners());
        assertTrue(plan.stopEmitter());
    }

    @Test
    void outOfRangeNewPlayerIsNeverStarted() {
        ServerPlaybackPlanner.ScanPlan plan = ServerPlaybackPlanner.plan(
                new ServerPlaybackPlanner.EmitterObservation(true, true, RANGE, Set.of()),
                List.of(new ServerPlaybackPlanner.ListenerObservation(LISTENER, true, true,
                        (RANGE + 10) * (RANGE + 10))));

        assertTrue(plan.startListeners().isEmpty());
        assertTrue(plan.stopListeners().isEmpty());
    }
}
