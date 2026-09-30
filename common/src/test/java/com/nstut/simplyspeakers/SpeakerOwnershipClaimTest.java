package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for first-come ownership claiming: an unowned speaker must become
 * owned by the first manager, and the claim must stick against later claim attempts so
 * OWNER_ONLY/TRUSTED boundaries become authoritative.
 */
class SpeakerOwnershipClaimTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void aliceClaimingUnownedSpeakerBecomesOwner() {
        SpeakerState state = new SpeakerState();
        assertTrue(state.getOwnerUuid() == null);
        assertTrue(state.claimOwnershipIfAbsent(ALICE));
        assertEquals(ALICE, state.getOwnerUuid());
    }

    @Test
    void bobCannotOverwriteExistingOwner() {
        SpeakerState state = new SpeakerState();
        assertTrue(state.claimOwnershipIfAbsent(ALICE));
        assertFalse(state.claimOwnershipIfAbsent(BOB));
        assertEquals(ALICE, state.getOwnerUuid());
    }

    @Test
    void nullPlayerNeverClaims() {
        SpeakerState state = new SpeakerState();
        assertFalse(state.claimOwnershipIfAbsent(null));
        assertTrue(state.getOwnerUuid() == null);
    }

    @Test
    void ownerOnlyManagementIsDeniedToOthersAfterClaimButAllowedToOwnerAndOperators() {
        SpeakerState state = new SpeakerState();
        assertTrue(SpeakerPermissions.canManage(state, ALICE, false));
        assertTrue(state.claimOwnershipIfAbsent(ALICE));

        // Before claiming, the state was open; after claiming, the boundary holds.
        assertFalse(SpeakerPermissions.canManage(state, BOB, false));
        assertTrue(SpeakerPermissions.canManage(state, ALICE, false));
        assertTrue(SpeakerPermissions.canManage(state, BOB, true));
    }

    @Test
    void trustedAccessBlocksUntrustedControlAfterClaim() {
        SpeakerState state = new SpeakerState();
        state.claimOwnershipIfAbsent(ALICE);
        state.setAccessMode(SpeakerAccess.TRUSTED);
        assertFalse(SpeakerPermissions.canControl(state, BOB, false));
        assertTrue(SpeakerPermissions.canControl(state, ALICE, false));
        state.trustPlayer(BOB);
        assertTrue(SpeakerPermissions.canControl(state, BOB, false));
    }
}
