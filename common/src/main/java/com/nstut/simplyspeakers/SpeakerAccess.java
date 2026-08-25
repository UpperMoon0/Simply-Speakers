package com.nstut.simplyspeakers;

import java.util.Locale;

/** Access policy for a speaker network. */
public enum SpeakerAccess {
    PUBLIC("public"),
    TRUSTED("trusted"),
    OWNER_ONLY("owner_only"),
    OPERATORS("operators");

    public static final SpeakerAccess DEFAULT = PUBLIC;

    private final String id;

    SpeakerAccess(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static SpeakerAccess byId(String value) {
        if (value != null) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (SpeakerAccess access : values()) {
                if (access.id.equals(normalized) || access.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return access;
                }
            }
        }
        return DEFAULT;
    }

    public static SpeakerAccess fromIndex(int index) {
        if (index < 0 || index >= values().length) return DEFAULT;
        return values()[index];
    }
}
