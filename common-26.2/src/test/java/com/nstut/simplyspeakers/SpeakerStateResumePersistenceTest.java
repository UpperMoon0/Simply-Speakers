package com.nstut.simplyspeakers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nstut.simplyspeakers.playlist.Playlist;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors the ServerSpeakerRegistry save/load cycle (Gson over a SpeakerState copy) to
 * prove the queued-track resume position survives a server restart.
 */
public class SpeakerStateResumePersistenceTest {

    @Test
    public void resumeIndexSurvivesRegistryStyleSaveAndReload() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        SpeakerState state = new SpeakerState();
        Playlist playlist = state.getPlaylist();
        playlist.add("a", "a.mp3");
        playlist.add("b", "b.mp3");
        playlist.add("c", "c.mp3");
        playlist.selectIndex(0);
        playlist.queueNext("c");
        playlist.setResumeIndex(0);

        SpeakerState persisted = state.copy();
        String json = gson.toJson(persisted);
        SpeakerState reloaded = gson.fromJson(json, SpeakerState.class);

        Playlist reloadedPlaylist = reloaded.getPlaylist();
        assertEquals(0, reloadedPlaylist.getResumeIndex());
        assertEquals(0, reloadedPlaylist.getCurrentIndex());
        assertEquals("c", reloadedPlaylist.getQueue().get(0));

        Playlist.Advance queued = reloadedPlaylist.next();
        assertEquals("c", queued.track().getAudioId());
        Playlist.Advance resumed = reloadedPlaylist.next();
        assertEquals("b", resumed.track().getAudioId());
    }
}
