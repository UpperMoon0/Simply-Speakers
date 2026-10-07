package com.nstut.simplyspeakers.playlist;

import com.nstut.simplyspeakers.SpeakerState;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerQueueTest {
    private Playlist source() {
        Playlist source = new Playlist();
        source.add("a", "A"); source.add("b", "B"); source.add("c", "C");
        source.playFromStart();
        return source;
    }

    @Test void playNextPrecedesAppendedRequestsAndResumesTheSavedSource() {
        Playlist p = source();
        p.queueLast("x"); p.queueLast("y"); p.queueNext("z");
        assertEquals(List.of("z", "x", "y"), p.getQueue());
        assertEquals("z", p.next().track().getAudioId());
        assertEquals(List.of(1, 2), p.upcomingIndices());
        assertEquals("x", p.next().track().getAudioId());
        assertEquals("y", p.next().track().getAudioId());
        assertEquals("b", p.next().track().getAudioId());
        assertEquals(3, p.size());
    }

    @Test void clearPendingRequestsWhileAQueuedTrackIsPlayingPreservesResume() {
        Playlist p = source(); p.queueLast("x"); p.queueLast("y");
        p.next(); p.clearQueue();
        assertEquals("b", p.next().track().getAudioId());
    }

    @Test void startingPlaylistClearsRequestsAndHonorsSeededShuffle() {
        Playlist p = source(); p.queueLast("x"); p.next();
        p.setShuffle(true, 42);
        Playlist comparison = source(); comparison.setShuffle(true, 42);
        String first = comparison.playFromStart().getAudioId();
        assertEquals(first, p.playFromStart().getAudioId());
        assertFalse(p.hasQueuedTracks()); assertFalse(p.isQueuedTrackActive());
        assertEquals(-1, p.getResumeIndex());
    }

    @Test void queueWorksWithNoSavedPlaylistAndThenExhausts() {
        Playlist p = new Playlist(); p.queueLast("x"); p.queueLast("x");
        p.removeQueued(0);
        assertEquals("x", p.next().track().getAudioId());
        assertEquals(0, p.size()); assertFalse(p.next().hasTrack());
    }

    @Test void movingSavedTracksDuringAnInterruptionPreservesTheResumeTrack() {
        Playlist p = source(); p.queueNext("x"); p.next();
        p.moveDown(0);
        assertEquals(List.of(2), p.upcomingIndices());
        assertEquals("c", p.next().track().getAudioId());
    }

    @Test void removingOneDuplicateEntryDoesNotRemoveAllOccurrences() {
        Playlist p = source(); p.add("a", "A again");
        assertTrue(p.removeAt(3)); assertEquals(3, p.size()); assertEquals("a", p.current().getAudioId());
    }

    @Test void queueIsBoundedAndIndexedEditsKeepDuplicatesDistinct() {
        Playlist p = source(); p.queueLast("x"); p.queueLast("x"); p.queueLast("y");
        assertTrue(p.moveQueued(2, -1)); assertEquals(List.of("x", "y", "x"), p.getQueue());
        assertTrue(p.removeQueued(2)); assertEquals(List.of("x", "y"), p.getQueue());
        assertFalse(p.removeQueued(9)); assertFalse(p.moveQueued(0, -1));
        for (int i = 0; i < 300; i++) p.queueLast("x");
        assertEquals(Playlist.MAX_ENTRIES, p.getQueue().size());
    }

    @Test void playerSnapshotRoundTripsQueueAndPausedPosition() {
        SpeakerState state = new SpeakerState();
        state.setPlaylist(source()); state.setAudioId("a"); state.setAudioFilename("A");
        state.startPlaybackAt(100, 10); state.pauseAt(140); state.getPlaylist().queueNext("x");
        PlayerViewSnapshot snapshot = PlayerViewSnapshot.capture(state, 200, 120);
        assertEquals(snapshot, PlayerViewSnapshot.decode(snapshot.encode()));
        assertEquals(12, snapshot.positionSeconds()); assertEquals(List.of("x"), snapshot.queue());
        assertEquals(List.of(1, 2), snapshot.upcoming());
    }

    @Test void corruptSnapshotsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> PlayerViewSnapshot.decode(new byte[]{127,127,127,127}));
        byte[] encoded = PlayerViewSnapshot.EMPTY.encode();
        assertThrows(IllegalArgumentException.class, () -> PlayerViewSnapshot.decode(java.util.Arrays.copyOf(encoded, encoded.length-1)));
        assertThrows(IllegalArgumentException.class, () -> PlayerViewSnapshot.decode(java.util.Arrays.copyOf(encoded, encoded.length+1)));
    }
}
