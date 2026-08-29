package com.nstut.simplyspeakers;

import com.nstut.simplyspeakers.playlist.Playlist;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpeakerStateResumeIndexTest {

    private SpeakerState stateWithQueuedPlayback() {
        SpeakerState state = new SpeakerState();
        Playlist playlist = state.getPlaylist();
        playlist.add("a", "a.mp3");
        playlist.add("b", "b.mp3");
        playlist.add("c", "c.mp3");
        playlist.selectIndex(0);
        playlist.queueNext("c");
        playlist.setResumeIndex(0);
        return state;
    }

    @Test
    void copyPreservesResumeIndexAndQueue() {
        SpeakerState copy = stateWithQueuedPlayback().copy();
        Playlist playlist = copy.getPlaylist();
        assertEquals(0, playlist.getResumeIndex());
        assertEquals(0, playlist.getCurrentIndex());
        assertEquals(1, playlist.getQueue().size());
        assertEquals("c", playlist.getQueue().get(0));
    }

    @Test
    void copyResumeIndexDrainsLikeOriginal() {
        SpeakerState state = stateWithQueuedPlayback();
        SpeakerState copy = state.copy();
        Playlist.Advance queued = copy.getPlaylist().next();
        assertEquals("c", queued.track().getAudioId());
        Playlist.Advance resumed = copy.getPlaylist().next();
        assertEquals("b", resumed.track().getAudioId());
        assertEquals(-1, copy.getPlaylist().getResumeIndex());
    }

    @Test
    void copyWithoutPlaylistStillInitialisesEmptyPlaylist() {
        SpeakerState state = new SpeakerState();
        assertEquals(-1, state.copy().getPlaylist().getResumeIndex());
    }
}
