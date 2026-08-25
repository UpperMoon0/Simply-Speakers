package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioLibraryInfoTest {
    private static final String FILENAME = "boss_theme.mp3";

    @Test
    void displayNameFallsBackToOriginalFilename() {
        AudioLibraryInfo info = new AudioLibraryInfo();
        assertEquals(FILENAME, info.effectiveDisplayName(FILENAME));
        info.setDisplayName("  Boss Theme ");
        assertEquals("Boss Theme", info.effectiveDisplayName(FILENAME));
    }

    @Test
    void matchesQueryAcrossAllTextualFieldsCaseInsensitively() {
        AudioLibraryInfo info = new AudioLibraryInfo();
        info.setDisplayName("Final Boss");
        info.setCategory("Combat");
        info.setTags(Arrays.asList("Epic", "Orchestral"));

        assertTrue(info.matchesQuery("epic", FILENAME));
        assertTrue(info.matchesQuery("BOSS", FILENAME));      // display name
        assertTrue(info.matchesQuery("combat", FILENAME));    // category
        assertTrue(info.matchesQuery("theme.mp3", FILENAME)); // filename
        assertFalse(info.matchesQuery("ambient", FILENAME));
        assertFalse(info.matchesQuery(null, FILENAME));
        assertFalse(info.matchesQuery("   ", FILENAME));
    }

    @Test
    void setTagsTrimsAndDropsBlankEntries() {
        AudioLibraryInfo info = new AudioLibraryInfo();
        info.setTags(Arrays.asList("  a ", "", "   ", "b"));
        assertEquals(List.of("a", "b"), info.getTags());
        info.setTags(null);
        assertTrue(info.getTags().isEmpty());
    }

    @Test
    void hasLibraryDataReflectsAnySetField() {
        AudioLibraryInfo info = new AudioLibraryInfo();
        assertFalse(info.hasLibraryData());
        info.setUploaderName("Steve");
        assertTrue(info.hasLibraryData());
    }

    @Test
    void copyIsDeepForTags() {
        AudioLibraryInfo info = new AudioLibraryInfo();
        info.setTags(Arrays.asList("one", "two"));
        AudioLibraryInfo clone = info.copy();
        clone.getTags().add("three");
        assertEquals(2, info.getTags().size());
        assertEquals(3, clone.getTags().size());
    }
}
