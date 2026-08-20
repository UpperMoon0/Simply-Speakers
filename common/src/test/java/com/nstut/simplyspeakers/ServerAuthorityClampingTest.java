package com.nstut.simplyspeakers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServerAuthorityClampingTest {

    @BeforeEach
    void setUp() {
        Config.setLocalConfig(64, false, 5 * 1024 * 1024);
    }

    @AfterEach
    void tearDown() {
        Config.setLocalConfig(64, false, 5 * 1024 * 1024);
    }

    @Test
    void testSpeakerStateClampsToActiveServerConfigRange() {
        SpeakerState state = new SpeakerState();

        // Server config allows up to 64
        state.setMaxRange(100);
        assertEquals(64, state.getMaxRange());

        state.setMaxRange(32);
        assertEquals(32, state.getMaxRange());

        state.setMaxRange(-5);
        assertEquals(1, state.getMaxRange());

        // Server config bumped to 256
        Config.applyServerConfig(256, false, 5 * 1024 * 1024);
        state.setMaxRange(200);
        assertEquals(200, state.getMaxRange());

        state.setMaxRange(500);
        assertEquals(256, state.getMaxRange());
    }

    @Test
    void testSpeakerStateClampsVolumeAndDropoff() {
        SpeakerState state = new SpeakerState();

        state.setMaxVolume(1.5f);
        assertEquals(1.0f, state.getMaxVolume());

        state.setMaxVolume(-0.5f);
        assertEquals(0.0f, state.getMaxVolume());

        state.setAudioDropoff(2.0f);
        assertEquals(1.0f, state.getAudioDropoff());

        state.setAudioDropoff(-1.0f);
        assertEquals(0.0f, state.getAudioDropoff());
    }

    @Test
    void testServerConfigUploadSettings() {
        assertEquals(false, Config.disableUpload);
        assertEquals(5 * 1024 * 1024, Config.maxUploadSize);

        Config.applyServerConfig(128, true, 2 * 1024 * 1024);
        assertEquals(true, Config.disableUpload);
        assertEquals(2 * 1024 * 1024, Config.maxUploadSize);
        assertEquals(128, Config.speakerRange);

        Config.restoreLocalConfig();
        assertEquals(false, Config.disableUpload);
        assertEquals(5 * 1024 * 1024, Config.maxUploadSize);
        assertEquals(64, Config.speakerRange);
    }
}
