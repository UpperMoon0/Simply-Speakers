package com.nstut.simplyspeakers.client.ui;

import dev.architectury.platform.Platform;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Tiny client-only properties store for UI presentation preferences.
 *
 * <p>Persists the visual theme and personal listening choices. The file is never synced to the
 * server and carries no block-entity state. Unknown future keys are preserved.</p>
 *
 * <p>Tests may override the config directory with the
 * {@code simplyspeakers.ui.config.dir} system property so no Minecraft instance
 * is required to exercise load/save.</p>
 */
public final class SimplySpeakersUiPreferences {
    public static final String THEME_KEY = "ui.theme";
    private static final String FILE_NAME = "simplyspeakers-ui.properties";
    private static final String DEFAULT_VALUE = "dark";

    private SimplySpeakersUiPreferences() {
    }

    public static synchronized void applyListeningPreferences() {
        var p = com.nstut.simplyspeakers.client.ListeningPreferencesStore.load(configFile().getParent());
        apply(p);
    }
    public static synchronized boolean setListeningPreferences(boolean stereo, int fadeIn, int fadeOut, int cacheMiB) {
        var p = new com.nstut.simplyspeakers.client.ListeningPreferencesStore.Preferences(stereo, fadeIn, fadeOut, cacheMiB);
        apply(p);
        return com.nstut.simplyspeakers.client.ListeningPreferencesStore.save(configFile().getParent(), p);
    }
    private static void apply(com.nstut.simplyspeakers.client.ListeningPreferencesStore.Preferences p) {
        com.nstut.simplyspeakers.Config.setPlaybackPreferences(p.stereo(), p.fadeInMillis(), p.fadeOutMillis());
        com.nstut.simplyspeakers.Config.clientCacheLimitBytes = p.cacheMiB() * 1024L * 1024L;
    }

    private static Path configFile() {
        String override = System.getProperty("simplyspeakers.ui.config.dir");
        Path dir = override != null
                ? Path.of(override)
                : Platform.getConfigFolder();
        return dir.resolve(FILE_NAME);
    }

    public static synchronized UiThemeMode getThemeMode() {
        return getThemeMode(configFile());
    }

    public static synchronized void setThemeMode(UiThemeMode mode) {
        setThemeMode(configFile(), mode);
    }

    static UiThemeMode getThemeMode(Path file) {
        Properties props = load(file);
        return UiThemeMode.fromConfigValue(props.getProperty(THEME_KEY, DEFAULT_VALUE));
    }

    static void setThemeMode(Path file, UiThemeMode mode) {
        Properties props = load(file);
        props.setProperty(THEME_KEY, mode.configValue());
        save(file, props);
    }

    private static Properties load(Path file) {
        Properties props = new Properties();
        if (!Files.exists(file)) return props;
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException ignored) {
            // Corrupt/unreadable file is treated as empty; callers fall back to defaults.
        }
        return props;
    }

    private static void save(Path file, Properties props) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Simply Speakers UI preferences");
            }
        } catch (IOException ignored) {
            // Best-effort persistence; ignore if the config directory is not writable.
        }
    }
}
