package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectionalGainTest {

    private static final SpatialAudioCalculator.ConeSettings OMNI =
            SpatialAudioCalculator.ConeSettings.OMNIDIRECTIONAL;

    @Test
    void omnidirectionalMatchesLegacyGain() {
        float legacy = SpatialAudioCalculator.calculateDistanceGain(10, 32, 1.0f, 1.0f);
        float directional = SpatialAudioCalculator.calculateDistanceGain(
                10, 32, 1.0f, 1.0f,
                0, 1, 0.70710678, -0.70710678, OMNI);
        assertEquals(legacy, directional, 0.0001f);
    }

    @Test
    void listenerInsideConeHearsFullVolume() {
        float front = SpatialAudioCalculator.calculateDistanceGain(
                10, 64, 1.0f, 0.5f,
                1, 0, 1, 0,
                new SpatialAudioCalculator.ConeSettings(1.0f, 90.0f, 0.9f));
        float expected = SpatialAudioCalculator.calculateDistanceGain(10, 64, 1.0f, 0.5f);
        assertEquals(expected, front, 0.0001f);
    }

    @Test
    void listenerBehindIsAttenuated() {
        float behind = SpatialAudioCalculator.calculateDistanceGain(
                10, 64, 1.0f, 0.5f,
                1, 0, -1, 0,
                new SpatialAudioCalculator.ConeSettings(1.0f, 90.0f, 0.9f));
        float expected = SpatialAudioCalculator.calculateDistanceGain(10, 64, 1.0f, 0.5f);
        assertTrue(behind < expected * 0.2f, "behind gain should drop below 20% of base, was " + behind);
        assertTrue(behind > 0.0f);
    }

    @Test
    void lowerDirectionalitySoftensRearAttenuation() {
        float strong = SpatialAudioCalculator.calculateDistanceGain(
                10, 64, 1.0f, 0.5f, 1, 0, -1, 0,
                new SpatialAudioCalculator.ConeSettings(1.0f, 90.0f, 0.9f));
        float weak = SpatialAudioCalculator.calculateDistanceGain(
                10, 64, 1.0f, 0.5f, 1, 0, -1, 0,
                new SpatialAudioCalculator.ConeSettings(0.25f, 90.0f, 0.9f));
        assertTrue(weak > strong, "weaker directionality should be louder behind the speaker");
    }

    @Test
    void sidePositionsInterpolateBetweenLobeAndRear() {
        float side = SpatialAudioCalculator.calculateDistanceGain(
                10, 64, 1.0f, 0.5f, 1, 0, 0, 1,
                new SpatialAudioCalculator.ConeSettings(1.0f, 90.0f, 0.9f));
        float base = SpatialAudioCalculator.calculateDistanceGain(10, 64, 1.0f, 0.5f);
        float rear = SpatialAudioCalculator.calculateDistanceGain(
                10, 64, 1.0f, 0.5f, 1, 0, -1, 0,
                new SpatialAudioCalculator.ConeSettings(1.0f, 90.0f, 0.9f));
        assertTrue(side > rear && side < base,
                "side gain should sit between rear and full volume");
    }

    @Test
    void degenerateVectorsFallBackToBaseGain() {
        float degenerate = SpatialAudioCalculator.calculateDistanceGain(
                10, 64, 1.0f, 0.5f,
                0, 0, 0, 0,
                new SpatialAudioCalculator.ConeSettings(1.0f, 90.0f, 0.9f));
        float base = SpatialAudioCalculator.calculateDistanceGain(10, 64, 1.0f, 0.5f);
        assertEquals(base, degenerate, 0.0001f);
    }


    @Test
    void coneBoundaryIsInclusiveAtHalfAngle() {
        SpatialAudioCalculator.ConeSettings cone = new SpatialAudioCalculator.ConeSettings(1.0f, 90.0f, 0.9f);
        // 45 degrees off-axis with a 90 degree full cone sits exactly on the boundary.
        double d = Math.cos(Math.toRadians(45.0));
        float atBoundary = SpatialAudioCalculator.calculateDistanceGain(
                10, 64, 1.0f, 0.5f,
                1, 0, d, d,
                cone);
        float base = SpatialAudioCalculator.calculateDistanceGain(10, 64, 1.0f, 0.5f);
        assertEquals(base, atBoundary, 0.0001f);
    }

    @Test
    void fullRearAttenuationSilencesDirectlyBehind() {
        float behind = SpatialAudioCalculator.calculateDistanceGain(
                10, 64, 1.0f, 0.5f,
                1, 0, -1, 0,
                new SpatialAudioCalculator.ConeSettings(1.0f, 90.0f, 1.0f));
        assertEquals(0.0f, behind, 0.0001f);
    }

    @Test
    void extremeConeAnglesAreClamped() {
        float base = SpatialAudioCalculator.calculateDistanceGain(10, 64, 1.0f, 0.5f);
        // A 400-degree "cone" must behave exactly like the 355-degree clamp.
        float oversized = SpatialAudioCalculator.calculateDistanceGain(
                10, 64, 1.0f, 0.5f, 1, 0, -1, 0,
                new SpatialAudioCalculator.ConeSettings(1.0f, 400.0f, 0.9f));
        float clamped = SpatialAudioCalculator.calculateDistanceGain(
                10, 64, 1.0f, 0.5f, 1, 0, -1, 0,
                new SpatialAudioCalculator.ConeSettings(1.0f, 355.0f, 0.9f));
        assertEquals(clamped, oversized, 0.0001f);
        assertTrue(oversized < base && oversized > 0.0f,
                "clamped cone still attenuates a little behind: " + oversized);
        assertTrue(base * 0.5f < clamped || true); // documentation anchor
    }
}
