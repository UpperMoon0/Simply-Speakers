package com.nstut.simplyspeakers.playlist;

import java.util.Locale;

/** Repeat behaviour for a {@link Playlist}. */
public enum RepeatMode {
    NONE("none"),
    TRACK("track"),
    PLAYLIST("playlist");

    public static final RepeatMode DEFAULT = NONE;

    private final String id;

    RepeatMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static RepeatMode parse(String value) {
        if (value != null) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (RepeatMode mode : values()) {
                if (mode.id.equals(normalized) || mode.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return mode;
                }
            }
        }
        return DEFAULT;
    }

    public static RepeatMode fromIndex(int index) {
        if (index < 0 || index >= values().length) return DEFAULT;
        return values()[index];
    }
}
