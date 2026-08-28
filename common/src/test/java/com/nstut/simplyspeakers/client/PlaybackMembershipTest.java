package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.SpeakerSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PlaybackMembershipTest {

    private PlaybackMembership<String> membership;

    @BeforeEach
    void setUp() {
        membership = new PlaybackMembership<>();
    }

    @Test
    void trackingAndLiveSettingsUpdates() {
        String pos = "pos1";
        String netKey = "net_123";
        SpeakerSettings initial = new SpeakerSettings(0.8f, 32, 1.0f);

        membership.track(pos, netKey, initial);
        assertTrue(membership.isTracking(pos));
        assertEquals(netKey, membership.getNetworkKey(pos));
        assertEquals(initial, membership.getSettings(pos));

        // Live setting update (e.g. BE ticked with new settings)
        SpeakerSettings updated = new SpeakerSettings(1.0f, 64, 0.5f);
        membership.updateSettings(pos, updated);
        assertEquals(updated, membership.getSettings(pos));
    }

    @Test
    void missingBlockEntityAcrossManyTicksPreservesCachedSettingsAndMembership() {
        String pos = "speaker_at_chunk_border";
        String netKey = "net_main";
        SpeakerSettings cached = new SpeakerSettings(0.9f, 512, 1.0f);

        membership.track(pos, netKey, cached);

        // Simulate 1000 client ticks where BlockEntity is missing / chunk unloaded
        for (int tick = 0; tick < 1000; tick++) {
            // BE missing: do nothing or no update
            assertTrue(membership.isTracking(pos));
            assertEquals(cached, membership.getSettings(pos));
            assertEquals(1, membership.size());
        }

        // Block entity reappears with modified volume
        SpeakerSettings refreshed = new SpeakerSettings(0.5f, 512, 0.8f);
        membership.updateSettings(pos, refreshed);

        assertEquals(refreshed, membership.getSettings(pos));
        assertTrue(membership.isTracking(pos));
    }

    @Test
    void multiSpeakerNetworkPartialAndTotalDetachment() {
        String pos1 = "speaker1";
        String pos2 = "speaker2";
        String netKey = "net_shared";

        membership.track(pos1, netKey, new SpeakerSettings(1.0f, 16, 1.0f));
        membership.track(pos2, netKey, new SpeakerSettings(1.0f, 32, 1.0f));

        assertEquals(2, membership.getPositions(netKey).size());

        // Detach speaker 1
        PlaybackMembership.DetachResult result1 = membership.detach(pos1);
        assertTrue(result1.wasTracked());
        assertEquals(netKey, result1.networkKey());
        assertFalse(result1.networkEmpty(), "Network should still have speaker2");
        assertFalse(membership.isTracking(pos1));
        assertTrue(membership.isTracking(pos2));

        // Detach speaker 2
        PlaybackMembership.DetachResult result2 = membership.detach(pos2);
        assertTrue(result2.wasTracked());
        assertEquals(netKey, result2.networkKey());
        assertTrue(result2.networkEmpty(), "Network should be empty now");
        assertFalse(membership.isTracking(pos2));
    }

    @Test
    void detachNetworkRemovesAllPositions() {
        String pos1 = "p1";
        String pos2 = "p2";
        String netKey = "net_group";

        membership.track(pos1, netKey, new SpeakerSettings(1.0f, 16, 1.0f));
        membership.track(pos2, netKey, new SpeakerSettings(1.0f, 32, 1.0f));

        Set<String> detached = membership.detachNetwork(netKey);
        assertEquals(Set.of(pos1, pos2), detached);
        assertEquals(0, membership.size());
        assertFalse(membership.isTracking(pos1));
        assertFalse(membership.isTracking(pos2));
    }

    @Test
    void networkReassignmentCleansUpOldNetwork() {
        String pos = "p1";
        membership.track(pos, "net_old", new SpeakerSettings(1.0f, 16, 1.0f));
        assertEquals(1, membership.getPositions("net_old").size());

        membership.track(pos, "net_new", new SpeakerSettings(1.0f, 32, 1.0f));
        assertEquals("net_new", membership.getNetworkKey(pos));
        assertTrue(membership.getPositions("net_old").isEmpty());
        assertEquals(Set.of(pos), membership.getPositions("net_new"));
    }
}
