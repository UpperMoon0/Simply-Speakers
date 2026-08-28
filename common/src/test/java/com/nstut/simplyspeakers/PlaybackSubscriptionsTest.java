package com.nstut.simplyspeakers;

import com.nstut.simplyspeakers.speakers.PlaybackSubscriptions;
import com.nstut.simplyspeakers.speakers.SpeakerLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackSubscriptionsTest {

    private PlaybackSubscriptions subscriptions;
    private final UUID playerA = UUID.randomUUID();
    private final UUID playerB = UUID.randomUUID();
    private final SpeakerLocation loc1 = new SpeakerLocation("minecraft:overworld", 10, 64, 10);
    private final SpeakerLocation loc2 = new SpeakerLocation("minecraft:overworld", 50, 64, 50);

    @BeforeEach
    void setUp() {
        subscriptions = new PlaybackSubscriptions();
    }

    @Test
    void subscribeUpdatesBothIndexes() {
        subscriptions.subscribe(playerA, loc1);

        assertTrue(subscriptions.isSubscribed(playerA, loc1));
        assertEquals(Set.of(loc1), subscriptions.getEmitterLocationsForPlayer(playerA));
        assertEquals(Set.of(playerA), subscriptions.getSubscribers(loc1));
    }

    @Test
    void unsubscribeCleansBothIndexes() {
        subscriptions.subscribe(playerA, loc1);
        subscriptions.subscribe(playerA, loc2);
        subscriptions.unsubscribe(playerA, loc1);

        assertFalse(subscriptions.isSubscribed(playerA, loc1));
        assertTrue(subscriptions.isSubscribed(playerA, loc2));
        assertEquals(Set.of(loc2), subscriptions.getEmitterLocationsForPlayer(playerA));
        assertEquals(Set.of(), subscriptions.getSubscribers(loc1));
    }

    @Test
    void removePlayerPurgesAllEmitterAssociations() {
        subscriptions.subscribe(playerA, loc1);
        subscriptions.subscribe(playerA, loc2);
        subscriptions.subscribe(playerB, loc1);

        Set<SpeakerLocation> removedLocations = subscriptions.removePlayer(playerA);

        assertEquals(Set.of(loc1, loc2), removedLocations);
        assertEquals(Set.of(), subscriptions.getEmitterLocationsForPlayer(playerA));
        assertEquals(Set.of(playerB), subscriptions.getSubscribers(loc1));
        assertEquals(Set.of(), subscriptions.getSubscribers(loc2));
    }

    @Test
    void removeEmitterPurgesAllPlayerSubscriptions() {
        subscriptions.subscribe(playerA, loc1);
        subscriptions.subscribe(playerB, loc1);
        subscriptions.subscribe(playerA, loc2);

        Set<UUID> removedPlayers = subscriptions.removeEmitter(loc1);

        assertEquals(Set.of(playerA, playerB), removedPlayers);
        assertEquals(Set.of(), subscriptions.getSubscribers(loc1));
        assertEquals(Set.of(loc2), subscriptions.getEmitterLocationsForPlayer(playerA));
        assertEquals(Set.of(), subscriptions.getEmitterLocationsForPlayer(playerB));
    }

    @Test
    void clearWipesAllData() {
        subscriptions.subscribe(playerA, loc1);
        subscriptions.subscribe(playerB, loc2);
        subscriptions.clear();

        assertEquals(Set.of(), subscriptions.getEmitterLocationsForPlayer(playerA));
        assertEquals(Set.of(), subscriptions.getEmitterLocationsForPlayer(playerB));
        assertEquals(Set.of(), subscriptions.getSubscribers(loc1));
        assertEquals(Set.of(), subscriptions.getSubscribers(loc2));
    }
}
