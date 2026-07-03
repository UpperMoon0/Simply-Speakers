package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioGainTest {
    @Test
    void masterVolumeMutesCustomSpeakerAudio() {
        assertEquals(0.0f, AudioGain.applyGameVolume(0.8f, 0.0f, 1.0f));
    }

    @Test
    void recordsCategoryMutesCustomSpeakerAudio() {
        assertEquals(0.0f, AudioGain.applyGameVolume(0.8f, 1.0f, 0.0f));
    }

    @Test
    void combinesSpeakerMasterAndRecordsGain() {
        assertEquals(0.2f, AudioGain.applyGameVolume(0.8f, 0.5f, 0.5f), 0.0001f);
    }

    @Test
    void clampsInvalidInputsBeforeCombining() {
        assertEquals(1.0f, AudioGain.applyGameVolume(2.0f, 2.0f, 2.0f));
        assertEquals(0.0f, AudioGain.applyGameVolume(-1.0f, 1.0f, 1.0f));
    }
}
