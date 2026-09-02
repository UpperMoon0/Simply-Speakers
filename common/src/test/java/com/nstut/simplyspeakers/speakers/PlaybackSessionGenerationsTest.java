package com.nstut.simplyspeakers.speakers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlaybackSessionGenerationsTest {

    @Test
    void semanticOccurrencesAlwaysAdvanceEvenForSameTrack() {
        PlaybackSessionGenerations generations = new PlaybackSessionGenerations();
        int first = generations.begin("minecraft:overworld/net_radio");
        int restart = generations.begin("minecraft:overworld/net_radio");
        int duplicatePlaylistEntry = generations.begin("minecraft:overworld/net_radio");

        assertEquals(1, first);
        assertNotEquals(first, restart);
        assertNotEquals(restart, duplicatePlaylistEntry);
        assertEquals(3, duplicatePlaylistEntry);
    }

    @Test
    void unrelatedNetworksHaveIndependentSequences() {
        PlaybackSessionGenerations generations = new PlaybackSessionGenerations();
        assertEquals(1, generations.begin("a"));
        assertEquals(2, generations.begin("a"));
        assertEquals(1, generations.begin("b"));
    }

    @Test
    void packetResyncReadsCurrentGenerationWithoutAdvancing() {
        PlaybackSessionGenerations generations = new PlaybackSessionGenerations();
        int session = generations.begin("state");
        assertEquals(session, generations.currentOrBegin("state"));
        assertEquals(session, generations.currentOrBegin("state"));
        assertEquals(session, generations.current("state"));
    }

    @Test
    void worldResetIsTheOnlyGenerationReset() {
        PlaybackSessionGenerations generations = new PlaybackSessionGenerations();
        generations.begin("state");
        generations.begin("state");
        generations.clear();
        assertNull(generations.current("state"));
        assertEquals(1, generations.currentOrBegin("state"));
    }
}