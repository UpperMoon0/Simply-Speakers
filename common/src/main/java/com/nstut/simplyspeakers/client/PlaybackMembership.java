package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.SpeakerSettings;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Version-independent thread-safe tracking of client playback emitter positions,
 * network associations, and cached speaker settings.
 *
 * @param <P> position type (e.g. net.minecraft.core.BlockPos)
 */
public class PlaybackMembership<P> {

    public record DetachResult(String networkKey, boolean networkEmpty, boolean wasTracked) {
    }

    private final Map<P, String> posToNetworkKey = new ConcurrentHashMap<>();
    private final Map<String, Set<P>> networkToPositions = new ConcurrentHashMap<>();
    private final Map<P, SpeakerSettings> cachedSettings = new ConcurrentHashMap<>();

    /**
     * Associates a position with a network key and caches its initial settings.
     * Reassigns networks cleanly if the position was previously on a different network.
     */
    public synchronized void track(P pos, String networkKey, SpeakerSettings settings) {
        if (pos == null || networkKey == null) {
            return;
        }

        String existingKey = posToNetworkKey.get(pos);
        if (existingKey != null && !existingKey.equals(networkKey)) {
            Set<P> oldPositions = networkToPositions.get(existingKey);
            if (oldPositions != null) {
                oldPositions.remove(pos);
                if (oldPositions.isEmpty()) {
                    networkToPositions.remove(existingKey);
                }
            }
        }

        posToNetworkKey.put(pos, networkKey);
        networkToPositions.computeIfAbsent(networkKey, k -> ConcurrentHashMap.newKeySet()).add(pos);
        if (settings != null) {
            cachedSettings.put(pos, settings);
        }
    }

    /**
     * Updates cached settings for an active position (e.g. from live block entity tick).
     */
    public void updateSettings(P pos, SpeakerSettings settings) {
        if (pos != null && settings != null && posToNetworkKey.containsKey(pos)) {
            cachedSettings.put(pos, settings);
        }
    }

    /**
     * Detaches a single position from its network and returns whether the network has become empty.
     */
    public synchronized DetachResult detach(P pos) {
        if (pos == null) {
            return new DetachResult(null, false, false);
        }

        cachedSettings.remove(pos);
        String networkKey = posToNetworkKey.remove(pos);
        if (networkKey == null) {
            return new DetachResult(null, false, false);
        }

        Set<P> positions = networkToPositions.get(networkKey);
        boolean networkEmpty = true;
        if (positions != null) {
            positions.remove(pos);
            if (!positions.isEmpty()) {
                networkEmpty = false;
            } else {
                networkToPositions.remove(networkKey);
            }
        }
        return new DetachResult(networkKey, networkEmpty, true);
    }

    /**
     * Detaches all positions associated with the given network key.
     */
    public synchronized Set<P> detachNetwork(String networkKey) {
        if (networkKey == null) {
            return Collections.emptySet();
        }

        Set<P> positions = networkToPositions.remove(networkKey);
        if (positions == null || positions.isEmpty()) {
            return Collections.emptySet();
        }

        Set<P> detached = new HashSet<>(positions);
        for (P pos : detached) {
            posToNetworkKey.remove(pos);
            cachedSettings.remove(pos);
        }
        return detached;
    }

    /**
     * Clears all tracked positions, networks, and cached settings.
     */
    public synchronized void clear() {
        posToNetworkKey.clear();
        networkToPositions.clear();
        cachedSettings.clear();
    }

    public String getNetworkKey(P pos) {
        return pos != null ? posToNetworkKey.get(pos) : null;
    }

    public Set<P> getPositions(String networkKey) {
        if (networkKey == null) {
            return Collections.emptySet();
        }
        Set<P> positions = networkToPositions.get(networkKey);
        return positions != null ? Collections.unmodifiableSet(new HashSet<>(positions)) : Collections.emptySet();
    }

    public SpeakerSettings getSettings(P pos) {
        return pos != null ? cachedSettings.get(pos) : null;
    }

    public Set<P> getAllPositions() {
        return Collections.unmodifiableSet(new HashSet<>(posToNetworkKey.keySet()));
    }

    public boolean isTracking(P pos) {
        return pos != null && posToNetworkKey.containsKey(pos);
    }

    public int size() {
        return posToNetworkKey.size();
    }
}
