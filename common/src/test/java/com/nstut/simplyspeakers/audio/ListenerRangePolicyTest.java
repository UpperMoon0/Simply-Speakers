package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListenerRangePolicyTest {

    @Test
    void newListenerEntersAtExactEffectiveRange() {
        double range = 16.0;

        // Inside or exact range: true
        assertTrue(ListenerRangePolicy.shouldListen(15.0 * 15.0, range, false));
        assertTrue(ListenerRangePolicy.shouldListen(16.0 * 16.0, range, false));

        // Just beyond range: false
        assertFalse(ListenerRangePolicy.shouldListen(16.1 * 16.1, range, false));
        assertFalse(ListenerRangePolicy.shouldListen(17.5 * 17.5, range, false));
    }

    @Test
    void existingListenerPersistsWithinHysteresisMargin() {
        double range = 16.0;
        double exitLimit = range + SpatialAudioCalculator.LISTENER_EXIT_HYSTERESIS; // 18.0

        // Inside standard range: true
        assertTrue(ListenerRangePolicy.shouldListen(15.0 * 15.0, range, true));
        assertTrue(ListenerRangePolicy.shouldListen(16.0 * 16.0, range, true));

        // In hysteresis margin (16.0 < dist <= 18.0): true
        assertTrue(ListenerRangePolicy.shouldListen(17.0 * 17.0, range, true));
        assertTrue(ListenerRangePolicy.shouldListen(18.0 * 18.0, range, true));

        // Beyond exit margin: false
        assertFalse(ListenerRangePolicy.shouldListen(18.1 * 18.1, range, true));
        assertFalse(ListenerRangePolicy.shouldListen(25.0 * 25.0, range, true));
    }

    @Test
    void zeroOrNegativeRangeAlwaysRejects() {
        assertFalse(ListenerRangePolicy.shouldListen(0.0, 0.0, false));
        assertFalse(ListenerRangePolicy.shouldListen(0.0, 0.0, true));
        assertFalse(ListenerRangePolicy.shouldListen(1.0, -5.0, false));
    }
}
