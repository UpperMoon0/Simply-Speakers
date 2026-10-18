package com.nstut.simplyspeakers.client;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Personal listening choices. Never read or written by server configuration sync. */
public final class ListeningPreferencesStore {
    public record Preferences(boolean stereo, int fadeInMillis, int fadeOutMillis, int cacheMiB) {
        public Preferences {
            fadeInMillis = Math.max(0, Math.min(5000, fadeInMillis));
            fadeOutMillis = Math.max(0, Math.min(5000, fadeOutMillis));
            cacheMiB = Math.max(64, Math.min(4096, cacheMiB));
        }
    }
    public static final Preferences DEFAULTS = new Preferences(false, 250, 250, 500);
    private static final String FILE = "simplyspeakers-ui.properties";
    private ListeningPreferencesStore() { }

    public static synchronized Preferences load(Path directory) {
        migrateLegacy(directory);
        Properties p = read(directory.resolve(FILE));
        return new Preferences(bool(p.getProperty("listening.stereo"), false),
            integer(p.getProperty("listening.fadeInMillis"), 250),
            integer(p.getProperty("listening.fadeOutMillis"), 250),
            integer(p.getProperty("listening.cacheMiB"), 500));
    }
    public static synchronized boolean save(Path directory, Preferences value) {
        Properties p = read(directory.resolve(FILE));
        put(p, value);
        return write(directory.resolve(FILE), p);
    }
    /** Called before Forge corrects its old spec, so existing personal choices survive removal. */
    public static synchronized void migrateLegacy(Path directory) {
        Properties p = read(directory.resolve(FILE));
        if (p.containsKey("listening.version")) return;
        Properties legacy = new Properties();
        for (String name : new String[]{"simplyspeakers.properties", "simplyspeakers-common.toml"}) {
            Path path = directory.resolve(name);
            if (!Files.isRegularFile(path)) continue;
            try (var lines = Files.newBufferedReader(path, java.nio.charset.StandardCharsets.ISO_8859_1)) {
                String line;
                while ((line = lines.readLine()) != null) {
                    String[] pair = line.split("#", 2)[0].split("=", 2);
                    if (pair.length == 2) legacy.setProperty(pair[0].trim(), pair[1].trim());
                }
            } catch (IOException ignored) { }
        }
        p.putIfAbsent("listening.stereo", Boolean.toString(bool(legacy.getProperty("stereoPlayback"), false)));
        p.putIfAbsent("listening.fadeInMillis", Integer.toString(integer(legacy.getProperty("rangeFadeInMillis"), 250)));
        p.putIfAbsent("listening.fadeOutMillis", Integer.toString(integer(legacy.getProperty("rangeFadeOutMillis"), 250)));
        p.putIfAbsent("listening.cacheMiB", "500");
        p.setProperty("listening.version", "1");
        write(directory.resolve(FILE), p);
    }
    private static void put(Properties p, Preferences v) {
        p.setProperty("listening.version", "1");
        p.setProperty("listening.stereo", Boolean.toString(v.stereo()));
        p.setProperty("listening.fadeInMillis", Integer.toString(v.fadeInMillis()));
        p.setProperty("listening.fadeOutMillis", Integer.toString(v.fadeOutMillis()));
        p.setProperty("listening.cacheMiB", Integer.toString(v.cacheMiB()));
    }
    private static int integer(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (RuntimeException ignored) { return fallback; }
    }
    private static boolean bool(String value, boolean fallback) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) ? Boolean.parseBoolean(value) : fallback;
    }
    private static Properties read(Path file) {
        Properties p = new Properties();
        if (Files.isRegularFile(file)) try (var in = Files.newInputStream(file)) { p.load(in); }
        catch (IOException | IllegalArgumentException ignored) { }
        return p;
    }
    private static boolean write(Path file, Properties p) {
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "simplyspeakers-ui-", ".tmp");
            try (var out = Files.newOutputStream(temporary)) { p.store(out, "Simply Speakers personal preferences"); }
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException error) {
            org.slf4j.LoggerFactory.getLogger("simplyspeakers").warn("Could not save listening preferences", error);
            return false;
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
}
