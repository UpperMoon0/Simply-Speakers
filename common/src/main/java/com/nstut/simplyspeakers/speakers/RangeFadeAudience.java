package com.nstut.simplyspeakers.speakers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Recently departed listeners still need immediate lifecycle stops, but never count toward EOF. */
public final class RangeFadeAudience {
    // Covers the maximum five-second client envelope plus packet delivery grace.
    public static final long RETENTION_NANOS = 7_000_000_000L;
    private final Map<SpeakerLocation, Map<UUID, Long>> departed = new HashMap<>();

    public synchronized void leave(SpeakerLocation location, UUID player, long now) {
        departed.computeIfAbsent(location, ignored -> new HashMap<>()).put(player, now + RETENTION_NANOS);
    }

    public synchronized void reenter(SpeakerLocation location, UUID player) {
        Map<UUID, Long> players = departed.get(location);
        if (players == null) return;
        players.remove(player);
        if (players.isEmpty()) departed.remove(location);
    }

    public synchronized Set<UUID> removeEmitter(SpeakerLocation location, long now) {
        Map<UUID, Long> players = departed.remove(location);
        Set<UUID> result = new HashSet<>();
        if (players != null) players.forEach((player, expires) -> {
            if (now - expires < 0) result.add(player);
        });
        return result;
    }

    public synchronized void removePlayer(UUID player) {
        departed.values().forEach(players -> players.remove(player));
        departed.values().removeIf(Map::isEmpty);
    }

    public synchronized void prune(long now) {
        departed.values().forEach(players -> players.values().removeIf(expires -> now - expires >= 0));
        departed.values().removeIf(Map::isEmpty);
    }

    public synchronized void clear() { departed.clear(); }
}
