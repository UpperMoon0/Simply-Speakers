package com.nstut.simplyspeakers.speakers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monotonic per-state playback-session identities used to reject stale client
 * EOF reports. A generation identifies a playback occurrence, not a track id:
 * restarting or advancing to the same URL must still receive a new generation.
 *
 * <p>Generations are intentionally retained after stop/non-URL playback for the
 * lifetime of a world. Reusing generation 1 after a stop would make a very late
 * EOF report from an older session valid again.</p>
 */
public final class PlaybackSessionGenerations {
    private final Map<String, Integer> generations = new ConcurrentHashMap<>();

    /** Begins a new semantic playback occurrence and returns its generation. */
    public synchronized int begin(String stateKey) {
        if (stateKey == null || stateKey.isEmpty()) return 0;
        int current = generations.getOrDefault(stateKey, 0);
        int next = current == Integer.MAX_VALUE ? 1 : current + 1;
        generations.put(stateKey, next);
        return next;
    }

    /** Returns the current generation, creating the first one only as a safe fallback. */
    public synchronized int currentOrBegin(String stateKey) {
        if (stateKey == null || stateKey.isEmpty()) return 0;
        Integer current = generations.get(stateKey);
        return current != null ? current : begin(stateKey);
    }

    /** Returns the current generation, or {@code null} when no session has begun. */
    public Integer current(String stateKey) {
        return stateKey != null ? generations.get(stateKey) : null;
    }

    /** Clears all generations at world shutdown only. */
    public void clear() {
        generations.clear();
    }
}