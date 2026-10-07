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
    @Test void removalOfCanonicalAnchorDuringQueueResumesAtItsSuccessor() {
        for (boolean byId : new boolean[]{false,true}) {
            Playlist p=playlistWithThreeTracks();p.add("d","d.mp3");p.selectIndex(0);p.queueNext("d");
            assertEquals("d",p.next().track().getAudioId());
            if(byId)p.removeByAudioId("a");else p.removeAt(0);
            assertEquals("b",p.next().track().getAudioId());
        }
    }
    @Test void replacementMapsCanonicalAnchorInsteadOfQueuedSelection() {
        Playlist p=playlistWithThreeTracks();p.selectIndex(1);p.queueNext("c");p.next();
        p.setTracks(List.of(PlaylistTrack.of("b","b.mp3"),PlaylistTrack.of("a","a.mp3"),PlaylistTrack.of("c","c.mp3")));
        assertEquals(0,p.getResumeIndex());assertEquals("a",p.next().track().getAudioId());
    }
    @Test void replacingListWithoutAnchorContinuesAfterSurvivingPredecessor() {
        Playlist p=playlistWithThreeTracks();p.selectIndex(1);p.queueNext("c");p.next();
        p.setTracks(List.of(PlaylistTrack.of("a","a.mp3"),PlaylistTrack.of("c","c.mp3")));
        assertEquals("c",p.next().track().getAudioId());
        p.selectIndex(0);p.queueNext("c");p.next();p.setTracks(List.of(PlaylistTrack.of("c","c.mp3")));
        assertEquals("c",p.next().track().getAudioId());
    }
    @Test void swappingAnchorAndQueuedTrackKeepsCanonicalContinuation() {
        Playlist p=playlistWithThreeTracks();p.selectIndex(0);p.queueNext("c");p.next();p.moveDown(0);
        assertEquals(1,p.getResumeIndex());assertEquals("c",p.next().track().getAudioId());
    }
}
