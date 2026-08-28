package com.nstut.simplyspeakers.speakers;

/**
 * Persistent server-side descriptor of an audio emitter (main speaker or proxy speaker).
 * Snapshots are retained by {@code ServerSpeakerRegistry} independently of the emitter's
 * chunk load state, so playback and listener management continue while the owning block
 * entity is not ticking.
 *
 * @param location  dimension-aware block position of the emitter
 * @param networkKey registry state key of the emitter ("net_&lt;id&gt;" or "internal_&lt;uuid&gt;")
 * @param maxRange  raw configured range of the emitter
 * @param maxVolume configured maximum volume (0.0 to 1.0)
 * @param dropoff   configured dropoff factor (0.0 to 1.0)
 * @param proxy     true when this emitter is a proxy speaker
 * @param active    last known intent to emit audio (powered/playing flags frozen at last update;
 *                  the authoritative per-network {@code SpeakerState} is still checked live)
 */
public record ServerEmitter(
        SpeakerLocation location,
        String networkKey,
        int maxRange,
        float maxVolume,
        float dropoff,
        boolean proxy,
        boolean active
) {

    public ServerEmitter {
        networkKey = networkKey != null ? networkKey : "";
    }

    public ServerEmitter withActive(boolean newActive) {
        return new ServerEmitter(location, networkKey, maxRange, maxVolume, dropoff, proxy, newActive);
    }

    /** Dimension-prefixed registry key used to look up the live {@code SpeakerState}. */
    public String fullStateKey() {
        return location.dimension() + "/" + networkKey;
    }

    /** User-facing speaker ID carried by play packets; empty for standalone speakers. */
    public String speakerIdForPacket() {
        return networkKey.startsWith("net_") ? networkKey.substring(4) : "";
    }
}
