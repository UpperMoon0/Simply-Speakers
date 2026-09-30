package com.nstut.simplyspeakers;

import java.util.Locale;

/**
 * Optional redstone behaviours for main speakers. The default {@link #POWER}
 * preserves the classic behaviour: powered = play, unpowered = stop.
 */
public enum RedstoneMode {
    POWER("power"),
    PULSE("pulse"),
    TOGGLE("toggle"),
    NEXT("next"),
    ANALOG_VOLUME("analog_volume"),
    ANALOG_TRACK("analog_track");

    public static final RedstoneMode DEFAULT = POWER;
    public static final int MAX_ANALOG_SLOTS = 15;

    private final String id;

    RedstoneMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static RedstoneMode byId(String value) {
        if (value != null) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (RedstoneMode mode : values()) {
                if (mode.id.equals(normalized) || mode.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return mode;
                }
            }
        }
        return DEFAULT;
    }

    public static RedstoneMode fromIndex(int index) {
        if (index < 0 || index >= values().length) return DEFAULT;
        return values()[index];
    }

    public boolean isEdgeTriggered() {
        return this == PULSE || this == TOGGLE || this == NEXT
                || this == ANALOG_VOLUME || this == ANALOG_TRACK;
    }
}
