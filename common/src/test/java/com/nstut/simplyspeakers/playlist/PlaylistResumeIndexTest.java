package com.nstut.simplyspeakers.playlist;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaylistResumeIndexTest {
    private Playlist playlistWithThreeTracks() {
        Playlist p = new Playlist();
        p.add("a", "a.mp3");
        p.add("b", "b.mp3");
        p.add("c", "c.mp3");
        return p;
    }

    @Test
    void resumeIndexDefaultsToNone() {
        assertEquals(-1, playlistWithThreeTracks().getResumeIndex());
    }

    @Test
    void resumeIndexRoundTripsThroughAccessor() {
        Playlist p = playlistWithThreeTracks();
        p.setResumeIndex(2);
        assertEquals(2, p.getResumeIndex());
        p.setResumeIndex(0);
        assertEquals(0, p.getResumeIndex());
        p.setResumeIndex(-1);
        assertEquals(-1, p.getResumeIndex());
    }

    @Test
    void clearResetsResumeIndex() {
        Playlist p = playlistWithThreeTracks();
        p.setResumeIndex(2);
        p.clear();
        assertEquals(-1, p.getResumeIndex());
    }

    @Test
    void resumeIndexIsHonouredAfterQueuedTrackDrains() {
        Playlist p = playlistWithThreeTracks();
        p.selectIndex(0);
        p.queueNext("c");
        p.setResumeIndex(0);
        Playlist.Advance queued = p.next();
        assertEquals("c", queued.track().getAudioId());
        Playlist.Advance resumed = p.next();
        assertEquals("b", resumed.track().getAudioId());
        assertEquals(-1, p.getResumeIndex());
    }

    @Test
    void resumeIndexSurvivesTrackListReplacement() {
        Playlist p = playlistWithThreeTracks();
        p.selectIndex(0);
        p.queueNext("c");
        p.setResumeIndex(1);
        p.setTracks(List.of(
                PlaylistTrack.of("x", "x.mp3"),
                PlaylistTrack.of("b", "b.mp3"),
                PlaylistTrack.of("c", "c.mp3")));
        assertEquals(1, p.getResumeIndex());
        assertTrue(p.hasQueuedTracks());
    }
}
