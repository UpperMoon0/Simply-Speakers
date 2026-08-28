package com.nstut.simplyspeakers.speakers;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe bidirectional player <-> emitter subscription index.
 * Maintains consistent pairing between listening players and active emitters.
 */
public final class PlaybackSubscriptions {

    private final Map<UUID, Set<SpeakerLocation>> playerToEmitters = new ConcurrentHashMap<>();
    private final Map<SpeakerLocation, Set<UUID>> emitterToPlayers = new ConcurrentHashMap<>();

    public void clear() {
        playerToEmitters.clear();
        emitterToPlayers.clear();
    }

    public void subscribe(UUID playerId, SpeakerLocation location) {
        if (playerId == null || location == null) return;
        emitterToPlayers.computeIfAbsent(location, k -> ConcurrentHashMap.newKeySet()).add(playerId);
        playerToEmitters.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(location);
    }

    public void unsubscribe(UUID playerId, SpeakerLocation location) {
        if (playerId == null || location == null) return;
        Set<UUID> players = emitterToPlayers.get(location);
        if (players != null) {
            players.remove(playerId);
            if (players.isEmpty()) emitterToPlayers.remove(location, players);
        }
        Set<SpeakerLocation> locations = playerToEmitters.get(playerId);
        if (locations != null) {
            locations.remove(location);
            if (locations.isEmpty()) playerToEmitters.remove(playerId, locations);
        }
    }

    public Set<SpeakerLocation> removePlayer(UUID playerId) {
        if (playerId == null) return Set.of();
        Set<SpeakerLocation> locations = playerToEmitters.remove(playerId);
        if (locations == null) return Set.of();
        for (SpeakerLocation location : locations) {
            Set<UUID> players = emitterToPlayers.get(location);
            if (players != null) {
                players.remove(playerId);
                if (players.isEmpty()) emitterToPlayers.remove(location, players);
            }
        }
        return locations;
    }

    public Set<UUID> removeEmitter(SpeakerLocation location) {
        if (location == null) return Set.of();
        Set<UUID> players = emitterToPlayers.remove(location);
        if (players == null) return Set.of();
        for (UUID playerId : players) {
            Set<SpeakerLocation> locations = playerToEmitters.get(playerId);
            if (locations != null) {
                locations.remove(location);
                if (locations.isEmpty()) playerToEmitters.remove(playerId, locations);
            }
        }
        return players;
    }

    public Set<SpeakerLocation> getEmitterLocationsForPlayer(UUID playerId) {
        if (playerId == null) return Set.of();
        Set<SpeakerLocation> locations = playerToEmitters.get(playerId);
        return locations != null ? Set.copyOf(locations) : Set.of();
    }

    public Set<UUID> getSubscribers(SpeakerLocation location) {
        if (location == null) return Set.of();
        Set<UUID> players = emitterToPlayers.get(location);
        return players != null ? Set.copyOf(players) : Set.of();
    }

    public boolean isSubscribed(UUID playerId, SpeakerLocation location) {
        if (playerId == null || location == null) return false;
        Set<UUID> players = emitterToPlayers.get(location);
        return players != null && players.contains(playerId);
    }
}
