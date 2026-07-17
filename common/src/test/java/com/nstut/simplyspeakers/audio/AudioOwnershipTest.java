package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioOwnershipTest {
    private record Entry(String id, String ownerUUID) {
    }

    @Test
    void ownershipRequiresAnExactNonNullPlayerMatch() {
        assertTrue(AudioOwnership.isOwnedBy("player-a", "player-a"));
        assertFalse(AudioOwnership.isOwnedBy("player-a", "player-b"));
        assertFalse(AudioOwnership.isOwnedBy(null, "player-a"));
        assertFalse(AudioOwnership.isOwnedBy("player-a", null));
    }

    @Test
    void playerListExcludesOtherPlayersAndOwnerlessLegacyEntries() {
        List<Entry> entries = List.of(
                new Entry("mine", "player-a"),
                new Entry("theirs", "player-b"),
                new Entry("legacy", null));

        assertEquals(
                List.of(entries.get(0)),
                AudioOwnership.ownedBy(entries, Entry::ownerUUID, "player-a"));
    }
}
