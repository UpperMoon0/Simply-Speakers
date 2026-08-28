package com.nstut.simplyspeakers.speakers;

import com.nstut.simplyspeakers.audio.ListenerRangePolicy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pure decision core for the centralized server playback scan. Given an emitter snapshot's
 * observed state and per-listener observations, it produces the exact subscription
 * transitions to execute, honoring {@link ListenerRangePolicy} entry/exit hysteresis.
 * Keeping this logic free of Minecraft types makes listener lifecycle behavior unit-testable.
 */
public final class ServerPlaybackPlanner {

    private ServerPlaybackPlanner() {
    }

    /**
     * @param playerId      unique id of the observed player
     * @param sameDimension false when the player is currently in another dimension
     * @param online        false when the player is no longer connected
     * @param distanceSq    squared distance to the emitter; use {@link Double#MAX_VALUE} when unresolvable
     */
    public record ListenerObservation(UUID playerId, boolean sameDimension, boolean online, double distanceSq) {
    }

    /**
     * @param active         emitter intends to emit (frozen snapshot flag)
     * @param playing        authoritative network state says audio is playing
     * @param effectiveRange range including any configured scaling
     * @param subscribed     players currently subscribed to this emitter
     */
    public record EmitterObservation(boolean active, boolean playing, double effectiveRange, Set<UUID> subscribed) {
    }

    /**
     * @param startListeners players that must receive a play packet and become subscribed
     * @param stopListeners  players that must receive a stop packet and be unsubscribed
     * @param stopEmitter    true when every subscription must be dropped (emitter stopped);
     *                       {@code stopListeners} then enumerates all subscribers to notify
     */
    public record ScanPlan(Set<UUID> startListeners, Set<UUID> stopListeners, boolean stopEmitter) {
    }

    /**
     * Plans subscription transitions for one emitter over one scan pass.
     */
    public static ScanPlan plan(EmitterObservation emitter, List<ListenerObservation> players) {
        Set<UUID> subscribed = emitter.subscribed() != null ? emitter.subscribed() : Set.of();
        if (!emitter.active() || !emitter.playing()) {
            return new ScanPlan(Set.of(), Set.copyOf(subscribed), !subscribed.isEmpty());
        }

        Set<UUID> inRange = new HashSet<>();
        Set<UUID> start = new HashSet<>();
        Set<UUID> stop = new HashSet<>();

        for (ListenerObservation player : players) {
            if (player.playerId() == null) continue;
            boolean isSubscribed = subscribed.contains(player.playerId());
            if (!player.online() || !player.sameDimension()) {
                if (isSubscribed) stop.add(player.playerId());
                continue;
            }
            if (!ListenerRangePolicy.shouldListen(player.distanceSq(), emitter.effectiveRange(), isSubscribed)) {
                continue;
            }
            inRange.add(player.playerId());
            if (!isSubscribed) start.add(player.playerId());
        }

        for (UUID listenerId : subscribed) {
            if (!inRange.contains(listenerId)) stop.add(listenerId);
        }

        return new ScanPlan(Set.copyOf(start), Set.copyOf(stop), false);
    }
}
