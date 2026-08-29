package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the edge-triggered redstone semantics that the 0.8.2 review required to be
 * correct: PULSE/TOGGLE/NEXT must fire only on a rising edge (so a pulse that falls
 * immediately does not stop playback), POWER must fire on both edges, and ANALOG_* must
 * act on level changes only.
 */
class RedstoneLogicTest {

    @Test
    void powerFiresOnBothEdges() {
        RedstoneLogic.RedstoneResult rising = RedstoneLogic.evaluate(RedstoneMode.POWER, 0, 15, 0);
        assertEquals(RedstoneLogic.Action.PLAY, rising.action());

        RedstoneLogic.RedstoneResult falling = RedstoneLogic.evaluate(RedstoneMode.POWER, 15, 0, 0);
        assertEquals(RedstoneLogic.Action.STOP, falling.action());

        RedstoneLogic.RedstoneResult steady = RedstoneLogic.evaluate(RedstoneMode.POWER, 15, 15, 0);
        assertEquals(RedstoneLogic.Action.NONE, steady.action());
    }

    @Test
    void pulseRestartsOnlyOnRisingEdge() {
        RedstoneLogic.RedstoneResult rising = RedstoneLogic.evaluate(RedstoneMode.PULSE, 0, 15, 0);
        assertEquals(RedstoneLogic.Action.RESTART, rising.action());

        RedstoneLogic.RedstoneResult falling = RedstoneLogic.evaluate(RedstoneMode.PULSE, 15, 0, 0);
        assertEquals(RedstoneLogic.Action.NONE, falling.action(),
                "a falling PULSE edge must NOT stop playback (the speaker emits while the network is playing)");
    }

    @Test
    void toggleFiresOnlyOnRisingEdge() {
        RedstoneLogic.RedstoneResult rising = RedstoneLogic.evaluate(RedstoneMode.TOGGLE, 0, 15, 0);
        assertEquals(RedstoneLogic.Action.TOGGLE_PAUSE, rising.action());

        RedstoneLogic.RedstoneResult falling = RedstoneLogic.evaluate(RedstoneMode.TOGGLE, 15, 0, 0);
        assertEquals(RedstoneLogic.Action.NONE, falling.action());
    }

    @Test
    void nextFiresOnlyOnRisingEdge() {
        RedstoneLogic.RedstoneResult rising = RedstoneLogic.evaluate(RedstoneMode.NEXT, 0, 15, 2);
        assertEquals(RedstoneLogic.Action.NEXT_TRACK, rising.action());

        RedstoneLogic.RedstoneResult falling = RedstoneLogic.evaluate(RedstoneMode.NEXT, 15, 0, 2);
        assertEquals(RedstoneLogic.Action.NONE, falling.action());
    }

    @Test
    void analogVolumeActsOnLevelChange() {
        RedstoneLogic.RedstoneResult change = RedstoneLogic.evaluate(RedstoneMode.ANALOG_VOLUME, 0, 7, 0);
        assertEquals(RedstoneLogic.Action.SET_VOLUME, change.action());
        assertEquals(7, change.payload());

        RedstoneLogic.RedstoneResult steady = RedstoneLogic.evaluate(RedstoneMode.ANALOG_VOLUME, 7, 7, 0);
        assertEquals(RedstoneLogic.Action.NONE, steady.action());
    }

    @Test
    void analogTrackSelectsClampedSlotOnRisingEdge() {
        RedstoneLogic.RedstoneResult rising = RedstoneLogic.evaluate(RedstoneMode.ANALOG_TRACK, 0, 3, 5);
        assertEquals(RedstoneLogic.Action.SELECT_TRACK, rising.action());
        assertEquals(2, rising.payload(), "slot index is 0-based and clamped to track count - 1");

        RedstoneLogic.RedstoneResult noTracks = RedstoneLogic.evaluate(RedstoneMode.ANALOG_TRACK, 0, 3, 0);
        assertEquals(RedstoneLogic.Action.NONE, noTracks.action(), "no playlist => nothing to select");
    }

    @Test
    void edgeModesReportIsEdgeTriggered() {
        assertTrue(RedstoneMode.PULSE.isEdgeTriggered());
        assertTrue(RedstoneMode.TOGGLE.isEdgeTriggered());
        assertTrue(RedstoneMode.NEXT.isEdgeTriggered());
        assertFalse(RedstoneMode.POWER.isEdgeTriggered());
    }
}
