package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.speakers.SpeakerLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPlaybackTrackerTest {

    private ClientPlaybackTracker<SpeakerLocation> tracker;
    private SpeakerLocation pos1;
    private SpeakerLocation pos2;

    @BeforeEach
    void setUp() {
        tracker = new ClientPlaybackTracker<>();
        pos1 = new SpeakerLocation("minecraft:overworld", 100, 64, 200);
        pos2 = new SpeakerLocation("minecraft:overworld", 105, 64, 200);
    }

    @Test
    void testInitialTrackRegistersAuthoritativeSettings() {
        tracker.track(pos1, "net_main", 32, 0.8f, 0.5f);

        assertTrue(tracker.isTracked(pos1));
        assertTrue(tracker.isNetworkActive("net_main"));
        assertEquals("net_main", tracker.getNetworkKey(pos1));

        ClientPlaybackTracker.EmitterSettings settings = tracker.getEmitter(pos1);
        assertNotNull(settings);
        assertEquals(32, settings.getMaxRange());
        assertEquals(0.8f, settings.getMaxVolume(), 1e-6);
        assertEquals(0.5f, settings.getAudioDropoff(), 1e-6);
    }

    @Test
    void testPresentThenMissingThenPresentMaintainsPlaybackLiveness() {
        tracker.track(pos1, "net_main", 32, 0.8f, 0.5f);

        // BE Present - updates settings
        tracker.updateFromBlockEntity(pos1, 48, 1.0f, 0.7f);
        assertEquals(48, tracker.getEmitter(pos1).getMaxRange());

        // BE Missing for 1 tick
        tracker.onBlockEntityMissing(pos1);
        assertTrue(tracker.isTracked(pos1), "Emitter must remain tracked after 1 missing BE tick");
        assertTrue(tracker.isNetworkActive("net_main"), "Network must remain active after 1 missing BE tick");
        assertEquals(48, tracker.getEmitter(pos1).getMaxRange(), "Cached settings must be preserved");

        // BE Present again - updates settings
        tracker.updateFromBlockEntity(pos1, 64, 0.9f, 0.6f);
        assertTrue(tracker.isTracked(pos1));
        assertEquals(64, tracker.getEmitter(pos1).getMaxRange());
    }

    @Test
    void testMissingBlockEntityAcross40And100TicksNeverCullsPlayback() {
        tracker.track(pos1, "net_main", 32, 0.8f, 0.5f);

        // Simulate 39 consecutive missing ticks
        for (int i = 0; i < 39; i++) {
            tracker.onBlockEntityMissing(pos1);
        }
        assertTrue(tracker.isTracked(pos1), "Emitter must remain tracked after 39 missing ticks");
        assertTrue(tracker.isNetworkActive("net_main"));

        // Simulate 40th missing tick (the old timeout threshold)
        tracker.onBlockEntityMissing(pos1);
        assertTrue(tracker.isTracked(pos1), "Emitter must NOT be culled after 40 missing ticks");
        assertTrue(tracker.isNetworkActive("net_main"), "Network must remain alive after 40 missing ticks");

        // Simulate 1000 missing ticks (extended chunk unload / lag)
        for (int i = 0; i < 1000; i++) {
            tracker.onBlockEntityMissing(pos1);
        }
        assertTrue(tracker.isTracked(pos1), "Emitter must remain tracked indefinitely until server stop");
        assertTrue(tracker.isNetworkActive("net_main"));
        assertEquals(32, tracker.getEmitter(pos1).getMaxRange());
    }

    @Test
    void testChunkChurnAndUnloadCyclesDoNotConsumeGraceBudget() {
        tracker.track(pos1, "net_main", 32, 0.8f, 0.5f);

        // Sequence: Loaded missing 20 ticks -> Unloaded 100 ticks -> Loaded missing 20 ticks
        for (int i = 0; i < 20; i++) {
            tracker.onBlockEntityMissing(pos1);
        }
        // Chunk unloaded: neither present nor incrementing, no-op
        for (int i = 0; i < 100; i++) {
            // chunk unloaded
        }
        for (int i = 0; i < 20; i++) {
            tracker.onBlockEntityMissing(pos1);
        }

        assertTrue(tracker.isTracked(pos1), "Repeated chunk churn must never cull playback emitter");
        assertTrue(tracker.isNetworkActive("net_main"));
        assertNotNull(tracker.getEmitter(pos1));
    }

    @Test
    void testMultiSpeakerNetworkDetachmentPreservesRemainingSpeakers() {
        tracker.track(pos1, "net_shared", 32, 0.8f, 0.5f);
        tracker.track(pos2, "net_shared", 32, 0.8f, 0.5f);

        assertEquals(Set.of(pos1, pos2), tracker.getPositions("net_shared"));

        // Stop pos1 (e.g. player walked out of pos1 range, but stays in pos2 range)
        ClientPlaybackTracker.DetachResult result1 = tracker.detachEmitter(pos1);
        assertEquals("net_shared", result1.networkKey());
        assertFalse(result1.networkEmpty(), "Network should remain active while pos2 is attached");
        assertTrue(tracker.isNetworkActive("net_shared"));
        assertFalse(tracker.isTracked(pos1));
        assertTrue(tracker.isTracked(pos2));

        // Stop pos2 (e.g. player walked out of pos2 range)
        ClientPlaybackTracker.DetachResult result2 = tracker.detachEmitter(pos2);
        assertEquals("net_shared", result2.networkKey());
        assertTrue(result2.networkEmpty(), "Network should be empty when all emitters are detached");
        assertFalse(tracker.isNetworkActive("net_shared"));
    }

    @Test
    void testExplicitStopNetworkDetachesAllEmitters() {
        tracker.track(pos1, "net_shared", 32, 0.8f, 0.5f);
        tracker.track(pos2, "net_shared", 32, 0.8f, 0.5f);

        Set<SpeakerLocation> stopped = tracker.stopNetwork("net_shared");
        assertEquals(Set.of(pos1, pos2), stopped);
        assertFalse(tracker.isNetworkActive("net_shared"));
        assertFalse(tracker.isTracked(pos1));
        assertFalse(tracker.isTracked(pos2));
        assertNull(tracker.getEmitter(pos1));
        assertNull(tracker.getEmitter(pos2));
    }

    @Test
    void testReassigningPositionToNewNetworkKeyDetachesFromOldNetwork() {
        tracker.track(pos1, "net_old", 32, 0.8f, 0.5f);
        assertTrue(tracker.isNetworkActive("net_old"));

        String oldKey = tracker.track(pos1, "net_new", 64, 1.0f, 1.0f);
        assertEquals("net_old", oldKey);
        assertFalse(tracker.isNetworkActive("net_old"), "Old network should become empty and inactive");
        assertTrue(tracker.isNetworkActive("net_new"));
        assertEquals("net_new", tracker.getNetworkKey(pos1));
        assertEquals(64, tracker.getEmitter(pos1).getMaxRange());
    }
}
