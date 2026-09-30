package com.nstut.simplyspeakers.speakers;

import java.util.Set;
import java.util.UUID;

/**
 * Pure quorum predicate for remote URL track EOF reports. A state's playback
 * advances (or stops) only when its current subscriber union is non-empty and
 * every remaining subscriber has already reported EOF. Reports from players
 * who are no longer subscribed (quit, range exit, dimension change) neither
 * block nor trigger advancement on their own, so listener churn can never
 * permanently stall or prematurely end a shared URL track.
 */
public final class RemoteEofQuorumEvaluator {

    private RemoteEofQuorumEvaluator() {
    }

    /**
     * @param reported           players that reported EOF for the pending session
     * @param currentSubscribers current state-wide subscriber union; empty means
     *                           nobody is listening and no advance may be decided
     *                           from churn alone
     */
    public static boolean shouldAdvance(Set<UUID> reported, Set<UUID> currentSubscribers) {
        return reported != null
                && currentSubscribers != null
                && !currentSubscribers.isEmpty()
                && reported.containsAll(currentSubscribers);
    }
}
