package com.nstut.simplyspeakers;

import com.nstut.simplyspeakers.playlist.RepeatMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RepeatConsolidationTest {
    @Test void legacyFlagMigratesOnceAndCannotOverrideLaterRepeatChanges() {
        var state=new SpeakerState("clip","clip.wav",true,true,0);
        assertTrue(state.isLooping());
        assertEquals(RepeatMode.TRACK,state.getPlaylist().getRepeatMode());
        state.getPlaylist().setRepeatMode(RepeatMode.NONE);
        assertFalse(state.isLooping());
        assertFalse(state.copy().isLooping());
    }
    @Test void compatibilityApiAndTaskbarShareOnePreference() {
        var state=new SpeakerState();state.setLooping(true);
        assertEquals(RepeatMode.TRACK,state.getPlaylist().getRepeatMode());
        state.getPlaylist().setRepeatMode(RepeatMode.PLAYLIST);
        assertFalse(state.isLooping());
        assertEquals(RepeatMode.PLAYLIST,state.copy().getPlaylist().getRepeatMode());
    }
    @Test void explicitNextSkipsRepeatTrackWithoutChangingThePreference() {
        var list=new com.nstut.simplyspeakers.playlist.Playlist();
        list.add("first","first.wav");list.add("second","second.wav");list.selectIndex(0);
        list.setRepeatMode(RepeatMode.TRACK);
        assertEquals("first",list.next().track().getAudioId());
        assertEquals("second",list.nextRequested().track().getAudioId());
        assertEquals(RepeatMode.TRACK,list.getRepeatMode());
    }
    @Test void requestsInterruptTrackRepeatAndPlayOnlyOnce() {
        var state=new SpeakerState();state.setLooping(true);
        var list=state.getPlaylist();list.add("source","source.wav");list.selectIndex(0);
        list.queueNext("request");
        assertFalse(state.isPlaybackLooping());
        assertEquals("request",list.next().track().getAudioId());
        assertFalse(state.isPlaybackLooping());
        assertEquals("source",list.next().track().getAudioId());
        assertEquals("source",list.next().track().getAudioId());
    }
}
