package com.nstut.simplyspeakers.client;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure, dependency-free state machine tracking client speaker emitter lifetime and settings.
 * <p>
 * Under the server-authoritative ownership model:
 * <ul>
 *   <li>{@code PlayAudioPacket} tracks/attaches an emitter.</li>
 *   <li>{@code StopAudioPacket} detaches an emitter.</li>
 *   <li>BlockEntity presence refreshes live volume, range, and dropoff settings.</li>
 *   <li>BlockEntity absence/chunk unload preserves cached settings and NEVER removes playback membership.</li>
 * </ul>
 *
 * @param <P> Position identifier type (e.g. BlockPos or SpeakerLocation)
 */
public class ClientPlaybackTracker<P> {

    public static final class EmitterSettings {
        private volatile int maxRange;
        private volatile float maxVolume;
        private volatile float audioDropoff;

        public EmitterSettings(int maxRange, float maxVolume, float audioDropoff) {
            this.maxRange = maxRange;
            this.maxVolume = maxVolume;
            this.audioDropoff = audioDropoff;
        }

        public int getMaxRange() {
            return maxRange;
        }

        public float getMaxVolume() {
            return maxVolume;
        }

        public float getAudioDropoff() {
            return audioDropoff;
        }

        public void update(int maxRange, float maxVolume, float audioDropoff) {
            this.maxRange = maxRange;
            this.maxVolume = maxVolume;
            this.audioDropoff = audioDropoff;
        }
    }

    private final Map<P, String> posToNetworkKey = new ConcurrentHashMap<>();
    private final Map<String, Set<P>> networkToPositions = new ConcurrentHashMap<>();
    private final Map<P, EmitterSettings> cachedEmitters = new ConcurrentHashMap<>();

    /**
     * Tracks an emitter at {@code pos} on the given {@code networkKey} with initial authoritative settings.
     *
     * @return The previous networkKey if the position was previously attached elsewhere, or null.
     */
    public String track(P pos, String networkKey, int maxRange, float maxVolume, float audioDropoff) {
        Objects.requireNonNull(pos, "pos must not be null");
        Objects.requireNonNull(networkKey, "networkKey must not be null");

        cachedEmitters.put(pos, new EmitterSettings(maxRange, maxVolume, audioDropoff));

        String oldKey = posToNetworkKey.put(pos, networkKey);
        if (oldKey != null && !oldKey.equals(networkKey)) {
            Set<P> oldPositions = networkToPositions.get(oldKey);
            if (oldPositions != null) {
                oldPositions.remove(pos);
                if (oldPositions.isEmpty()) {
                    networkToPositions.remove(oldKey);
                }
            }
        }

        networkToPositions.computeIfAbsent(networkKey, k -> ConcurrentHashMap.newKeySet()).add(pos);
        return oldKey;
    }

    /**
     * Refreshes cached settings when a client block entity is present in a loaded chunk.
     */
    public boolean updateFromBlockEntity(P pos, int maxRange, float maxVolume, float audioDropoff) {
        EmitterSettings settings = cachedEmitters.get(pos);
        if (settings != null) {
            settings.update(maxRange, maxVolume, audioDropoff);
            return true;
        }
        return false;
    }

    /**
     * Called when a block entity is missing or chunk is unloaded.
     * Under server-authoritative lifetime, this explicitly preserves the cached emitter and does NOT cull.
     */
    public void onBlockEntityMissing(P pos) {
        // Intentionally no-op: retains cached emitter settings and network membership.
    }

    /**
     * Detaches an emitter position upon receiving an authoritative server stop command.
     *
     * @param pos Position to detach
     * @return Result containing the affected networkKey and whether that network is now empty
     */
    public DetachResult detachEmitter(P pos) {
        cachedEmitters.remove(pos);
        String networkKey = posToNetworkKey.remove(pos);
        if (networkKey == null) {
            return new DetachResult(null, false);
        }

        Set<P> positions = networkToPositions.get(networkKey);
        boolean networkEmpty = false;
        if (positions != null) {
            positions.remove(pos);
            if (positions.isEmpty()) {
                networkToPositions.remove(networkKey);
                networkEmpty = true;
            }
        }
        return new DetachResult(networkKey, networkEmpty);
    }

    /**
     * Stops an entire network key, detaching all associated emitters.
     *
     * @return Set of positions that were detached
     */
    public Set<P> stopNetwork(String networkKey) {
        Set<P> positions = networkToPositions.remove(networkKey);
        if (positions == null || positions.isEmpty()) {
            return Collections.emptySet();
        }
        for (P pos : positions) {
            posToNetworkKey.remove(pos, networkKey);
            cachedEmitters.remove(pos);
        }
        return new HashSet<>(positions);
    }

    public boolean isNetworkActive(String networkKey) {
        Set<P> positions = networkToPositions.get(networkKey);
        return positions != null && !positions.isEmpty();
    }

    public boolean isTracked(P pos) {
        return posToNetworkKey.containsKey(pos);
    }

    public String getNetworkKey(P pos) {
        return posToNetworkKey.get(pos);
    }

    public EmitterSettings getEmitter(P pos) {
        return cachedEmitters.get(pos);
    }

    public Set<P> getPositions(String networkKey) {
        Set<P> positions = networkToPositions.get(networkKey);
        return positions != null ? Collections.unmodifiableSet(positions) : Collections.emptySet();
    }

    public Set<String> getActiveNetworks() {
        return Collections.unmodifiableSet(networkToPositions.keySet());
    }

    public void clear() {
        posToNetworkKey.clear();
        networkToPositions.clear();
        cachedEmitters.clear();
    }

    public record DetachResult(String networkKey, boolean networkEmpty) {}
}
