package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.playlist.RepeatMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientRepeatStateTest {
    @Test void transientDecoderLoopFlagsNeverOverwriteSavedRepeatPreference() {
        ClientSpeakerRegistry.clear();
        var state=new SpeakerState();state.getPlaylist().setRepeatMode(RepeatMode.PLAYLIST);
        ClientSpeakerRegistry.updateState("net_test",state);
        ClientSpeakerRegistry.setLooping("net_test",false);
        assertEquals(RepeatMode.PLAYLIST,ClientSpeakerRegistry.getState("net_test").getPlaylist().getRepeatMode());
        state.getPlaylist().setRepeatMode(RepeatMode.TRACK);
        ClientSpeakerRegistry.updateState("net_test",state);
        assertFalse(ClientSpeakerRegistry.getLooping("net_test",true),"Playlist Track repeats on server; cache must retain decoder flag");
        ClientSpeakerRegistry.clear();
    }
}
