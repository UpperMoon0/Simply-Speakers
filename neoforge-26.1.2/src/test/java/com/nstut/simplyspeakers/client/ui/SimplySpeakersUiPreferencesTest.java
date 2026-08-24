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
}
