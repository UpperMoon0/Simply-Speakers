package com.nstut.simplyspeakers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigSyncTest {

    @BeforeEach
    void setUp() {
        Config.restoreLocalConfig();
        Config.setLocalConfig(64, false, 5 * 1024 * 1024);
    }

    @AfterEach
    void tearDown() {
        Config.restoreLocalConfig();
        Config.setLocalConfig(64, false, 5 * 1024 * 1024);
    }

    @Test
    void setLocalConfigUpdatesActiveAndLocalValues() {
        Config.setLocalConfig(128, true, 10 * 1024 * 1024);

        assertEquals(128, Config.speakerRange);
        assertTrue(Config.disableUpload);
        assertEquals(10 * 1024 * 1024, Config.maxUploadSize);

        assertEquals(128, Config.getLocalSpeakerRange());
        assertTrue(Config.isLocalDisableUpload());
        assertEquals(10 * 1024 * 1024, Config.getLocalMaxUploadSize());
    }

    @Test
    void applyServerConfigOverridesActiveValuesWithoutModifyingLocalCache() {
        Config.setLocalConfig(64, false, 5 * 1024 * 1024);

        Config.applyServerConfig(512, true, 20 * 1024 * 1024);

        assertEquals(512, Config.speakerRange);
        assertTrue(Config.disableUpload);
        assertEquals(20 * 1024 * 1024, Config.maxUploadSize);

        // Local cache remains intact
        assertEquals(64, Config.getLocalSpeakerRange());
        assertFalse(Config.isLocalDisableUpload());
        assertEquals(5 * 1024 * 1024, Config.getLocalMaxUploadSize());
    }

    @Test
    void restoreLocalConfigResetsActiveValuesToLocal() {
        Config.setLocalConfig(48, false, 4 * 1024 * 1024);

        Config.applyServerConfig(512, true, 50 * 1024 * 1024);
        assertEquals(512, Config.speakerRange);
        assertTrue(Config.disableUpload);

        Config.restoreLocalConfig();

        assertEquals(48, Config.speakerRange);
        assertFalse(Config.disableUpload);
        assertEquals(4 * 1024 * 1024, Config.maxUploadSize);
    }

    @Test
    void clampsServerConfigValuesToLegalRanges() {
        Config.applyServerConfig(9999, false, 999999999);
        assertEquals(Config.MAX_RANGE, Config.speakerRange);
        assertEquals(Config.MAX_UPLOAD_SIZE, Config.maxUploadSize);

        Config.applyServerConfig(-10, false, 10);
        assertEquals(Config.MIN_RANGE, Config.speakerRange);
        assertEquals(Config.MIN_UPLOAD_SIZE, Config.maxUploadSize);
    }
}
