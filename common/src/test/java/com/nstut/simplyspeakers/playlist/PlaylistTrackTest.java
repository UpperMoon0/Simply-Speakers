package com.nstut.simplyspeakers.playlist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaylistTrackTest {
    @Test
    void nullInputsAreSanitized() {
        PlaylistTrack track = new PlaylistTrack(null, null);
        assertEquals("", track.getAudioId());
        assertEquals("", track.getFilename());
    }

    @Test
    void keyCombinesIdAndFilename() {
        assertEquals("a|a.mp3", PlaylistTrack.of("a", "a.mp3").key());
    }

    @Test
    void equalityUsesAudioIdOnly() {
        assertEquals(PlaylistTrack.of("a", "one.mp3"), PlaylistTrack.of("a", "two.mp3"));
        assertNotEquals(PlaylistTrack.of("a", "a.mp3"), PlaylistTrack.of("b", "a.mp3"));
        assertFalse(PlaylistTrack.of("a", "a.mp3").equals(null));
    }

    @Test
    void sameAudioMatchesOnlyIdenticalIds() {
        assertTrue(PlaylistTrack.of("a", "x.mp3").sameAudio("a"));
        assertFalse(PlaylistTrack.of("a", "x.mp3").sameAudio("b"));
        assertFalse(PlaylistTrack.of("a", "x.mp3").sameAudio(null));
    }
}
