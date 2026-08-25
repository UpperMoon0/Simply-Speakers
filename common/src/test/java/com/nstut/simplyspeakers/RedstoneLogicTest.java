package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedstoneLogicTest {

    @Test
    void powerModePlaysOnRisingEdgeAndStopsOnFallingEdge() {
        assertEquals(RedstoneLogic.Action.PLAY,
                RedstoneLogic.evaluate(RedstoneMode.POWER, 0, 15, 0).action());
        assertEquals(RedstoneLogic.Action.STOP,
                RedstoneLogic.evaluate(RedstoneMode.POWER, 15, 0, 0).action());
        assertEquals(RedstoneLogic.Action.NONE,
                RedstoneLogic.evaluate(RedstoneMode.POWER, 15, 15, 0).action());
    }

    @Test
    void pulseModeRestartsOnlyOnRisingEdge() {
        assertEquals(RedstoneLogic.Action.RESTART,
                RedstoneLogic.evaluate(RedstoneMode.PULSE, 0, 1, 0).action());
        assertEquals(RedstoneLogic.Action.NONE,
                RedstoneLogic.evaluate(RedstoneMode.PULSE, 1, 15, 0).action());
        assertEquals(RedstoneLogic.Action.NONE,
                RedstoneLogic.evaluate(RedstoneMode.PULSE, 15, 0, 0).action());
    }

    @Test
    void toggleModeTogglesPauseOnRisingEdge() {
        assertEquals(RedstoneLogic.Action.TOGGLE_PAUSE,
                RedstoneLogic.evaluate(RedstoneMode.TOGGLE, 0, 15, 0).action());
        assertEquals(RedstoneLogic.Action.NONE,
                RedstoneLogic.evaluate(RedstoneMode.TOGGLE, 15, 0, 0).action());
    }

    @Test
    void nextModeSkipsOnRisingEdge() {
        assertEquals(RedstoneLogic.Action.NEXT_TRACK,
                RedstoneLogic.evaluate(RedstoneMode.NEXT, 0, 5, 3).action());
        assertEquals(RedstoneLogic.Action.NONE,
                RedstoneLogic.evaluate(RedstoneMode.NEXT, 5, 0, 3).action());
    }

    @Test
    void analogVolumeTracksSignalStrengthChanges() {
        RedstoneLogic.RedstoneResult result =
                RedstoneLogic.evaluate(RedstoneMode.ANALOG_VOLUME, 0, 8, 0);
        assertEquals(RedstoneLogic.Action.SET_VOLUME, result.action());
        assertEquals(8, result.payload());
        assertEquals(RedstoneLogic.Action.NONE,
                RedstoneLogic.evaluate(RedstoneMode.ANALOG_VOLUME, 8, 8, 0).action());
        RedstoneLogic.RedstoneResult zero = RedstoneLogic.evaluate(RedstoneMode.ANALOG_VOLUME, 15, 0, 0);
        assertEquals(RedstoneLogic.Action.SET_VOLUME, zero.action());
        assertEquals(0, zero.payload());
    }

    @Test
    void analogTrackSelectsSlotOnRisingEdge() {
        RedstoneLogic.RedstoneResult result = RedstoneLogic.evaluate(RedstoneMode.ANALOG_TRACK, 0, 3, 10);
        assertEquals(RedstoneLogic.Action.SELECT_TRACK, result.action());
        assertEquals(2, result.payload());
        assertEquals(RedstoneLogic.Action.NONE,
                RedstoneLogic.evaluate(RedstoneMode.ANALOG_TRACK, 3, 4, 10).action());
        RedstoneLogic.RedstoneResult clamped = RedstoneLogic.evaluate(RedstoneMode.ANALOG_TRACK, 0, 15, 2);
        assertEquals(1, clamped.payload());
        assertEquals(RedstoneLogic.Action.NONE,
                RedstoneLogic.evaluate(RedstoneMode.ANALOG_TRACK, 0, 5, 0).action());
    }

    @Test
    void comparatorLevelExposesProgressBands() {
        assertEquals(0, RedstoneLogic.comparatorLevel(false, 0.0f, 100.0f));
        assertEquals(1, RedstoneLogic.comparatorLevel(true, 0.0f, 100.0f));
        assertEquals(7, RedstoneLogic.comparatorLevel(true, 50.0f, 100.0f));
        assertEquals(14, RedstoneLogic.comparatorLevel(true, 99.9f, 100.0f));
        assertEquals(15, RedstoneLogic.comparatorLevel(true, 100.0f, 100.0f));
        assertEquals(15, RedstoneLogic.comparatorLevel(true, 120.0f, 100.0f));
    }

    @Test
    void comparatorLevelWithUnknownDurationIndicatesPlaying() {
        assertEquals(7, RedstoneLogic.comparatorLevel(true, 12.0f, 0.0f));
        assertEquals(0, RedstoneLogic.comparatorLevel(false, 12.0f, 0.0f));
    }

    @Test
    void nullAndUnknownModesFallBackToNoAction() {
        assertEquals(RedstoneLogic.Action.NONE,
                RedstoneLogic.evaluate(null, 0, 15, 0).action());
    }

    @Test
    void modeLookupIsLenient() {
        assertEquals(RedstoneMode.POWER, RedstoneMode.byId("power"));
        assertEquals(RedstoneMode.ANALOG_VOLUME, RedstoneMode.byId("ANALOG_VOLUME"));
        assertEquals(RedstoneMode.DEFAULT, RedstoneMode.byId("bogus"));
        assertEquals(RedstoneMode.DEFAULT, RedstoneMode.fromIndex(-1));
        assertEquals(RedstoneMode.NEXT, RedstoneMode.fromIndex(3));
    }
}
