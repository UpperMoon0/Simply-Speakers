package com.nstut.simplyspeakers.playlist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepeatModeTest {
    @Test
    void parsesIdsAndNamesLeniently() {
        assertEquals(RepeatMode.TRACK, RepeatMode.parse("track"));
        assertEquals(RepeatMode.PLAYLIST, RepeatMode.parse("PLAYLIST"));
        assertEquals(RepeatMode.NONE, RepeatMode.parse(" none "));
        assertEquals(RepeatMode.DEFAULT, RepeatMode.parse(null));
        assertEquals(RepeatMode.DEFAULT, RepeatMode.parse("bogus"));
    }

    @Test
    void fromIndexIsSafe() {
        assertEquals(RepeatMode.NONE, RepeatMode.fromIndex(-1));
        assertEquals(RepeatMode.NONE, RepeatMode.fromIndex(99));
        assertEquals(RepeatMode.TRACK, RepeatMode.fromIndex(1));
        assertEquals(RepeatMode.PLAYLIST, RepeatMode.fromIndex(2));
    }
}
