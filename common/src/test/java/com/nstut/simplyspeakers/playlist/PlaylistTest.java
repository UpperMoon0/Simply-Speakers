package com.nstut.simplyspeakers.playlist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaylistTest {
    private Playlist playlist;

    @BeforeEach
    void setUp() {
        playlist = new Playlist();
        playlist.add("a", "a.mp3");
        playlist.add("b", "b.mp3");
        playlist.add("c", "c.mp3");
    }

    @Test
    void emptyPlaylistAdvancingIsExhausted() {
        Playlist empty = new Playlist();
        assertEquals(Playlist.AdvanceResult.EXHAUSTED, empty.next().result());
    }

    @Test
    void firstNextStartsAtFirstTrack() {
        Playlist.Advance advance = playlist.next();
        assertEquals(Playlist.AdvanceResult.ADVANCED, advance.result());
        assertEquals("a", advance.track().getAudioId());
    }

    @Test
    void sequentialNextWalksInOrder() {
        playlist.next();
        playlist.next();
        assertEquals("b", playlist.current().getAudioId());
        playlist.next();
        assertEquals("c", playlist.current().getAudioId());
    }

    @Test
    void endWithoutRepeatIsExhausted() {
        playlist.next(); playlist.next(); playlist.next();
        Playlist.Advance advance = playlist.next();
        assertEquals(Playlist.AdvanceResult.EXHAUSTED, advance.result());
        assertEquals("c", playlist.current().getAudioId());
    }

    @Test
    void repeatPlaylistWrapsToFirstTrack() {
        playlist.setRepeatMode(RepeatMode.PLAYLIST);
        playlist.next(); playlist.next(); playlist.next();
        Playlist.Advance advance = playlist.next();
        assertEquals(Playlist.AdvanceResult.WRAPPED, advance.result());
        assertEquals("a", advance.track().getAudioId());
    }

    @Test
    void repeatTrackStaysOnCurrent() {
        playlist.setRepeatMode(RepeatMode.TRACK);
        playlist.next();
        for (int i = 0; i < 4; i++) {
            Playlist.Advance advance = playlist.next();
            assertEquals(Playlist.AdvanceResult.ADVANCED, advance.result());
            assertEquals("a", advance.track().getAudioId());
        }
    }

    @Test
    void previousMovesBackThroughOrder() {
        playlist.next(); playlist.next();
        playlist.previous();
        assertEquals("a", playlist.current().getAudioId());
    }

    @Test
    void previousOnFirstTrackRestartsIt() {
        playlist.next();
        playlist.previous();
        assertEquals("a", playlist.current().getAudioId());
    }

    @Test
    void previousWithPlaylistRepeatWrapsToLast() {
        playlist.setRepeatMode(RepeatMode.PLAYLIST);
        playlist.next();
        Playlist.Advance advance = playlist.previous();
        assertEquals(Playlist.AdvanceResult.WRAPPED, advance.result());
        assertEquals("c", advance.track().getAudioId());
    }

    @Test
    void queuePlaysBeforeNormalOrderingAndIsOneShot() {
        playlist.next();
        playlist.queueNext("c");
        Playlist.Advance advance = playlist.next();
        assertEquals("c", advance.track().getAudioId());
        Playlist.Advance nextAdvance = playlist.next();
        assertFalse(nextAdvance.hasTrack() && "c".equals(nextAdvance.track().getAudioId()));
    }

    @Test
    void queuedUnknownIdsAreSkipped() {
        playlist.queueNext("missing");
        playlist.queueNext("b");
        Playlist.Advance advance = playlist.next();
        assertEquals("b", advance.track().getAudioId());
        Playlist.Advance nextAdvance = playlist.next();
        assertEquals("c", nextAdvance.track().getAudioId());
    }

    @Test
    void queueNextResumesCanonicalWalkAfterQueuedTrack() {
        playlist.next();
        playlist.next();
        assertEquals("b", playlist.current().getAudioId());
        playlist.queueNext("a");
        Playlist.Advance advance = playlist.next();
        assertEquals("a", advance.track().getAudioId());
        Playlist.Advance resume = playlist.next();
        assertEquals("c", resume.track().getAudioId());
        Playlist.Advance after = playlist.next();
        assertEquals(Playlist.AdvanceResult.EXHAUSTED, after.result());
    }

    @Test
    void queueNextInShuffleResumesCanonicalWalk() {
        Playlist walk = new Playlist();
        walk.add("a", "a.mp3");
        walk.add("b", "b.mp3");
        walk.add("c", "c.mp3");
        walk.setShuffle(true, 42L);
        walk.next();

        playlist.setShuffle(true, 42L);
        playlist.next();
        assertEquals(walk.getCurrentIndex(), playlist.getCurrentIndex());
        playlist.queueNext("a");
        Playlist.Advance advance = playlist.next();
        assertEquals("a", advance.track().getAudioId());
        Playlist.Advance resume = playlist.next();
        Playlist.Advance expected = walk.next();
        assertEquals(expected.result(), resume.result());
        assertEquals(expected.hasTrack() ? expected.track().getAudioId() : null,
                resume.hasTrack() ? resume.track().getAudioId() : null);
    }

    @Test
    void shuffleOrderCoversAllTracksBeforeRepeating() {
        playlist.setShuffle(true, 42L);
        java.util.Set<String> visited = new java.util.HashSet<>();
        Playlist.Advance advance = playlist.next();
        visited.add(advance.track().getAudioId());
        while (visited.size() < 3) {
            advance = playlist.next();
            assertTrue(advance.hasTrack(), "shuffle walk should cover every track before exhausting");
            visited.add(advance.track().getAudioId());
        }
        assertEquals(java.util.Set.of("a", "b", "c"), visited);
    }

    @Test
    void shuffleWalkIsDeterministicForSameSeed() {
        assertEquals(walkThreeTracks(7L, 6), walkThreeTracks(7L, 6));
    }

    private List<String> walkThreeTracks(long seed, int steps) {
        Playlist p = new Playlist();
        p.add("a", "a.mp3"); p.add("b", "b.mp3"); p.add("c", "c.mp3");
        p.setShuffle(true, seed);
        List<String> walked = new java.util.ArrayList<>();
        for (int i = 0; i < steps; i++) {
            Playlist.Advance advance = p.next();
            if (advance.hasTrack()) walked.add(advance.track().getAudioId());
        }
        return walked;
    }

    @Test
    void removingCurrentTrackKeepsListConsistent() {
        playlist.next(); playlist.next();
        assertTrue(playlist.removeByAudioId("b"));
        assertEquals(-1, playlist.getCurrentIndex());
        assertNull(playlist.current());
        Playlist.Advance advance = playlist.next();
        assertEquals("a", advance.track().getAudioId());
    }

    @Test
    void removingEarlierTrackShiftsCurrentIndex() {
        playlist.selectIndex(2);
        playlist.removeByAudioId("a");
        assertEquals(1, playlist.getCurrentIndex());
        assertEquals("c", playlist.current().getAudioId());
    }

    @Test
    void moveUpAndDownKeepSelectionAttached() {
        playlist.selectIndex(1);
        assertTrue(playlist.moveUp(1));
        assertEquals("b", playlist.current().getAudioId());
        assertEquals(0, playlist.getCurrentIndex());
        assertTrue(playlist.moveDown(0));
        assertEquals("b", playlist.current().getAudioId());
        assertEquals(1, playlist.getCurrentIndex());
    }

    @Test
    void setTracksPreservesSelectionWhenStillPresent() {
        playlist.selectIndex(1);
        playlist.setTracks(List.of(
                PlaylistTrack.of("x", "x.mp3"),
                PlaylistTrack.of("b", "b.mp3")));
        assertEquals("b", playlist.current().getAudioId());
        assertEquals(1, playlist.getCurrentIndex());
    }


    @Test
    void queueTakesPrecedenceEvenUnderShuffle() {
        playlist.setShuffle(true, 9L);
        playlist.next();
        playlist.queueNext("a");
        Playlist.Advance advance = playlist.next();
        assertEquals("a", advance.track().getAudioId());
    }

    @Test
    void removeByAudioIdRemovesEveryDuplicate() {
        playlist.add("b", "b-copy.mp3");
        assertTrue(playlist.removeByAudioId("b"));
        assertEquals(2, playlist.size());
        assertFalse(playlist.removeByAudioId("missing"));
    }

    @Test
    void clearResetsQueueSelectionAndTracks() {
        playlist.next();
        playlist.queueNext("c");
        playlist.clear();
        assertEquals(0, playlist.size());
        assertNull(playlist.current());
        assertFalse(playlist.hasQueuedTracks());
        assertEquals(Playlist.AdvanceResult.EXHAUSTED, playlist.next().result());
    }

    @Test
    void singleTrackWithoutRepeatExhaustsAfterFirstPlay() {
        Playlist single = new Playlist();
        single.add("only", "only.mp3");
        single.next();
        Playlist.Advance advance = single.next();
        assertEquals(Playlist.AdvanceResult.EXHAUSTED, advance.result());
        assertFalse(advance.hasTrack());
    }

    @Test
    void trackRepeatRestartsSingleTrack() {
        Playlist single = new Playlist();
        single.add("only", "only.mp3");
        single.setRepeatMode(RepeatMode.TRACK);
        single.next();
        Playlist.Advance advance = single.next();
        assertEquals(Playlist.AdvanceResult.ADVANCED, advance.result());
        assertEquals("only", advance.track().getAudioId());
    }

    @Test
    void singleTrackWithPlaylistRepeatKeepsLooping() {
        Playlist single = new Playlist();
        single.add("only", "only.mp3");
        single.setRepeatMode(RepeatMode.PLAYLIST);
        single.next();
        for (int i = 0; i < 3; i++) {
            Playlist.Advance advance = single.next();
            assertEquals(Playlist.AdvanceResult.WRAPPED, advance.result());
            assertEquals("only", advance.track().getAudioId());
        }
    }

    @Test
    void selectIndexOutOfBoundsReturnsNull() {
        assertNull(playlist.selectIndex(-5));
        assertNull(playlist.selectIndex(99));
        // setCurrentIndex clamps instead of throwing.
        playlist.setCurrentIndex(50);
        assertEquals(2, playlist.getCurrentIndex());
    }

    @Test
    void addIgnoresTracksBeyondTheCap() {
        Playlist full = new Playlist();
        for (int i = 0; i < Playlist.MAX_ENTRIES; i++) {
            full.add("id-" + i, "name-" + i + ".mp3");
        }
        assertEquals(Playlist.MAX_ENTRIES, full.size());
        full.add("overflow", "overflow.mp3");
        assertEquals(Playlist.MAX_ENTRIES, full.size());
    }

    @Test
    void constructorTruncatesToTheCap() {
        java.util.List<PlaylistTrack> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i < Playlist.MAX_ENTRIES + 10; i++) {
            tooMany.add(PlaylistTrack.of("id-" + i, "name-" + i + ".mp3"));
        }
        Playlist playlist = new Playlist(tooMany);
        assertEquals(Playlist.MAX_ENTRIES, playlist.size());
    }

    @Test
    void setTracksTruncatesToTheCap() {
        java.util.List<PlaylistTrack> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i < Playlist.MAX_ENTRIES + 1; i++) {
            tooMany.add(PlaylistTrack.of("id-" + i, "name-" + i + ".mp3"));
        }
        playlist.setTracks(tooMany);
        assertEquals(Playlist.MAX_ENTRIES, playlist.size());
        assertNull(playlist.current());
    }
}
