package com.nstut.simplyspeakers.speakers;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the shared remote-EOF quorum predicate: listener churn
 * (disconnect, range exit, dimension change, emitter removal) must neither stall
 * nor prematurely end a shared URL track.
 */
class RemoteEofQuorumEvaluatorTest {

    private final UUID playerA = UUID.randomUUID();
    private final UUID playerB = UUID.randomUUID();
    private final UUID playerC = UUID.randomUUID();

    @Test
    void bothListenersReportedAdvances() {
        assertTrue(RemoteEofQuorumEvaluator.shouldAdvance(
                Set.of(playerA, playerB), Set.of(playerA, playerB)));
    }

    @Test
    void partialReportingWaits() {
        assertFalse(RemoteEofQuorumEvaluator.shouldAdvance(
                Set.of(playerA), Set.of(playerA, playerB)));
    }

    @Test
    void listenerDisconnectAfterReportAdvances() {
        // A reported, then B disconnected: only A remains, and A already reported.
        assertTrue(RemoteEofQuorumEvaluator.shouldAdvance(
                Set.of(playerA, playerB), Set.of(playerA)));
    }

    @Test
    void listenerRangeExitAfterReportAdvances() {
        // Same semantics as disconnect: churn shrinks the quorum, not the reports.
        assertTrue(RemoteEofQuorumEvaluator.shouldAdvance(
                Set.of(playerA, playerB), Set.of(playerA)));
    }

    @Test
    void dimensionChangeAfterReportAdvances() {
        assertTrue(RemoteEofQuorumEvaluator.shouldAdvance(
                Set.of(playerA, playerB), Set.of(playerA)));
    }

    @Test
    void newListenerJoiningBeforeCompletionWaits() {
        // A reported, C joined afterwards: the union now includes C, so advance waits.
        assertFalse(RemoteEofQuorumEvaluator.shouldAdvance(
                Set.of(playerA), Set.of(playerA, playerC)));
    }

    @Test
    void emptyAudienceNeverAdvancesFromChurnAlone() {
        // Everyone left: with no one to report EOF, a churn-only decision must not
        // advance; playback is torn down through normal stop paths instead.
        assertFalse(RemoteEofQuorumEvaluator.shouldAdvance(
                Set.of(playerA), Set.of()));
        assertFalse(RemoteEofQuorumEvaluator.shouldAdvance(
                Set.of(), Set.of()));
    }

    @Test
    void nullInputsNeverAdvance() {
        assertFalse(RemoteEofQuorumEvaluator.shouldAdvance(null, Set.of(playerA)));
        assertFalse(RemoteEofQuorumEvaluator.shouldAdvance(Set.of(playerA), null));
    }
}
