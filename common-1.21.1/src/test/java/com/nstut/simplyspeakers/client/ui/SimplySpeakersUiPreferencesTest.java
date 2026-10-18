package com.nstut.simplyspeakers.client.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplySpeakersUiPreferencesTest {

    private static Path configFile(Path dir) {
        return dir.resolve("simplyspeakers-ui.properties");
    }

    @Test
    void missingFileDefaultsToDark(@TempDir Path dir) {
        assertEquals(UiThemeMode.DARK, SimplySpeakersUiPreferences.getThemeMode(configFile(dir)));
    }

    @Test
    void darkLoadSaveRoundTrip(@TempDir Path dir) {
        Path file = configFile(dir);
        SimplySpeakersUiPreferences.setThemeMode(file, UiThemeMode.DARK);
        assertEquals(UiThemeMode.DARK, SimplySpeakersUiPreferences.getThemeMode(file));
        assertTrue(Files.exists(file));
    }

    @Test
    void lightLoadSaveRoundTrip(@TempDir Path dir) {
        Path file = configFile(dir);
        SimplySpeakersUiPreferences.setThemeMode(file, UiThemeMode.LIGHT);
        assertEquals(UiThemeMode.LIGHT, SimplySpeakersUiPreferences.getThemeMode(file));
    }

    @Test
    void invalidThemeFallsBackToDark(@TempDir Path dir) throws Exception {
        Path file = configFile(dir);
        Files.writeString(file, "ui.theme=banana\n");
        assertEquals(UiThemeMode.DARK, SimplySpeakersUiPreferences.getThemeMode(file));
    }

    @Test
    void settingThemePreservesOtherProperties(@TempDir Path dir) throws Exception {
        Path file = configFile(dir);
        Files.writeString(file, "other.setting=keepme\nui.theme=dark\n");
        SimplySpeakersUiPreferences.setThemeMode(file, UiThemeMode.LIGHT);
        String content = Files.readString(file);
        assertTrue(content.contains("other.setting=keepme"), "unknown properties must be preserved");
        assertTrue(content.contains("ui.theme=light"));
        assertFalse(content.contains("ui.theme=dark"));
    }
    @Test
    void listeningSettingsSurviveThemeSaveAndReload(@TempDir Path dir) {
        String oldDirectory=System.getProperty("simplyspeakers.ui.config.dir");
        boolean stereo=com.nstut.simplyspeakers.Config.stereoPlayback;
        int fadeIn=com.nstut.simplyspeakers.Config.rangeFadeInMillis, fadeOut=com.nstut.simplyspeakers.Config.rangeFadeOutMillis;
        long cache=com.nstut.simplyspeakers.Config.clientCacheLimitBytes;
        try {
            System.setProperty("simplyspeakers.ui.config.dir",dir.toString());
            assertTrue(SimplySpeakersUiPreferences.setListeningPreferences(true,0,1800,1024));
            SimplySpeakersUiPreferences.setThemeMode(UiThemeMode.LIGHT);
            com.nstut.simplyspeakers.Config.setPlaybackPreferences(false,250,250);
            SimplySpeakersUiPreferences.applyListeningPreferences();
            assertTrue(com.nstut.simplyspeakers.Config.stereoPlayback);
            assertEquals(0,com.nstut.simplyspeakers.Config.rangeFadeInMillis);
            assertEquals(1800,com.nstut.simplyspeakers.Config.rangeFadeOutMillis);
            assertEquals(1024L*1024*1024,com.nstut.simplyspeakers.Config.clientCacheLimitBytes);
            assertEquals(UiThemeMode.LIGHT,SimplySpeakersUiPreferences.getThemeMode());
        } finally {
            com.nstut.simplyspeakers.Config.setPlaybackPreferences(stereo,fadeIn,fadeOut);
            com.nstut.simplyspeakers.Config.clientCacheLimitBytes=cache;
            if(oldDirectory==null)System.clearProperty("simplyspeakers.ui.config.dir");else System.setProperty("simplyspeakers.ui.config.dir",oldDirectory);
        }
    }
}
