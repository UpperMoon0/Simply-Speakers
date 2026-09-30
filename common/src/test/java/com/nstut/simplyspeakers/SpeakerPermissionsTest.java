package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeakerPermissionsTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FRIEND = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private SpeakerState ownedState(SpeakerAccess access) {
        SpeakerState state = new SpeakerState();
        state.setOwnerUuid(OWNER);
        state.setAccessMode(access);
        return state;
    }

    @Test
    void publicSpeakersAllowAnyoneIncludingAnonymousAutomation() {
        SpeakerState state = ownedState(SpeakerAccess.PUBLIC);
        assertTrue(SpeakerPermissions.canControl(state, STRANGER, false));
        assertTrue(SpeakerPermissions.canControl(state, null, false));
    }

    @Test
    void trustedSpeakersAllowOwnerAndTrustedOnly() {
        SpeakerState state = ownedState(SpeakerAccess.TRUSTED);
        state.trustPlayer(FRIEND);
        assertTrue(SpeakerPermissions.canControl(state, OWNER, false));
        assertTrue(SpeakerPermissions.canControl(state, FRIEND, false));
        assertFalse(SpeakerPermissions.canControl(state, STRANGER, false));
        assertFalse(SpeakerPermissions.canControl(state, null, false));
        assertTrue(SpeakerPermissions.canControl(state, STRANGER, true));
    }

    @Test
    void ownerOnlyBlocksTrustedPlayers() {
        SpeakerState state = ownedState(SpeakerAccess.OWNER_ONLY);
        state.trustPlayer(FRIEND);
        assertTrue(SpeakerPermissions.canControl(state, OWNER, false));
        assertFalse(SpeakerPermissions.canControl(state, FRIEND, false));
        assertTrue(SpeakerPermissions.canControl(state, FRIEND, true));
    }

    @Test
    void operatorsModeRestrictsToOperators() {
        SpeakerState state = ownedState(SpeakerAccess.OPERATORS);
        assertFalse(SpeakerPermissions.canControl(state, OWNER, false));
        assertTrue(SpeakerPermissions.canControl(state, STRANGER, true));
    }

    @Test
    void unownedStatesStayOpenUntilClaimed() {
        SpeakerState state = new SpeakerState();
        assertTrue(SpeakerPermissions.canControl(state, STRANGER, false));
        assertTrue(SpeakerPermissions.canManage(state, STRANGER, false));
    }

    @Test
    void managementRequiresOwnerOrOperator() {
        SpeakerState state = ownedState(SpeakerAccess.PUBLIC);
        assertTrue(SpeakerPermissions.canManage(state, OWNER, false));
        assertFalse(SpeakerPermissions.canManage(state, FRIEND, false));
        assertTrue(SpeakerPermissions.canManage(state, FRIEND, true));

        state.trustPlayer(FRIEND);
        assertTrue(SpeakerPermissions.canControl(state, FRIEND, false));
        assertFalse(SpeakerPermissions.canManage(state, FRIEND, false));
    }

    @Test
    void missingStateIsAlwaysDenied() {
        assertFalse(SpeakerPermissions.canControl(null, OWNER, false));
        assertFalse(SpeakerPermissions.canManage(null, OWNER, true));
    }
}
