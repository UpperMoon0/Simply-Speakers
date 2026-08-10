package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpeakerStateRelinkerTest {
    @Test
    void sourceStateReplacesProxyCreatedPlaceholder() {
        SpeakerState source = new SpeakerState("audio", "song.mp3", false, true, -1, 0.7f, 32, 0.4f);
        SpeakerState placeholder = new SpeakerState();

        SpeakerState result = SpeakerStateRelinker.stateForNewId(source, placeholder, false);

        assertEquals("audio", result.getAudioId());
        assertEquals("song.mp3", result.getAudioFilename());
        assertEquals(32, result.getMaxRange());
        assertNotSame(source, result);
    }

    @Test
    void existingMainSpeakerKeepsDestinationNetworkAuthoritative() {
        SpeakerState source = new SpeakerState("old", "old.mp3", false, false, -1);
        SpeakerState destination = new SpeakerState("network", "network.mp3", true, true, 40);

        SpeakerState result = SpeakerStateRelinker.stateForNewId(source, destination, true);

        assertEquals("network", result.getAudioId());
        assertEquals("network.mp3", result.getAudioFilename());
        assertFalse(result == destination);
    }

    @Test
    void missingStatesRemainMissing() {
        assertNull(SpeakerStateRelinker.stateForNewId(null, null, false));
    }
}
