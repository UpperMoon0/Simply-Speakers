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
}
