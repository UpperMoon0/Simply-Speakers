package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpatialAudioCalculatorTest {

    @Test
    void singleSpeakerYieldsExactPositionAndGain() {
        SpatialAudioCalculator.SpeakerEmitter emitter =
                new SpatialAudioCalculator.SpeakerEmitter(10.0, 64.0, 10.0, 32, 0.8f, 0.0f);

        // Listener at (10, 64, 20) -> distance 10
        SpatialAudioCalculator.VirtualEmitterResult result =
                SpatialAudioCalculator.calculateVirtualEmitter(10.0, 64.0, 20.0, List.of(emitter));

        assertEquals(10.0, result.x(), 0.001);
        assertEquals(64.0, result.y(), 0.001);
        assertEquals(10.0, result.z(), 0.001);
        assertEquals(0.8f, result.maxGain(), 0.001f);
    }

    @Test
    void twoSymmetricSpeakersYieldExactMidpointAndPeakGainWithoutDoubling() {
        // Speaker 1 at x = 0, Speaker 2 at x = 20
        SpatialAudioCalculator.SpeakerEmitter s1 =
                new SpatialAudioCalculator.SpeakerEmitter(0.0, 64.0, 0.0, 32, 1.0f, 0.0f);
        SpatialAudioCalculator.SpeakerEmitter s2 =
                new SpatialAudioCalculator.SpeakerEmitter(20.0, 64.0, 0.0, 32, 1.0f, 0.0f);

        // Listener at midpoint (10, 64, 0)
        SpatialAudioCalculator.VirtualEmitterResult result =
                SpatialAudioCalculator.calculateVirtualEmitter(10.0, 64.0, 0.0, List.of(s1, s2));

        // Position is exact midpoint (10.0, 64.0, 0.0)
        assertEquals(10.0, result.x(), 0.001);
        assertEquals(64.0, result.y(), 0.001);
        assertEquals(0.0, result.z(), 0.001);
        // Gain is max(1.0, 1.0) = 1.0 (no volume doubling!)
        assertEquals(1.0f, result.maxGain(), 0.001f);
    }

    @Test
    void asymmetricDistancesWeightPositionTowardsCloserSpeaker() {
        // Speaker 1 at x = 0 (dropoff 1.0, range 20), Speaker 2 at x = 100 (dropoff 1.0, range 20)
        // Listener at x = 2 (close to S1, distance 2; distance to S2 is 98 -> out of range)
        SpatialAudioCalculator.SpeakerEmitter s1 =
                new SpatialAudioCalculator.SpeakerEmitter(0.0, 64.0, 0.0, 20, 1.0f, 1.0f);
        SpatialAudioCalculator.SpeakerEmitter s2 =
                new SpatialAudioCalculator.SpeakerEmitter(100.0, 64.0, 0.0, 20, 1.0f, 1.0f);

        SpatialAudioCalculator.VirtualEmitterResult result =
                SpatialAudioCalculator.calculateVirtualEmitter(2.0, 64.0, 0.0, List.of(s1, s2));

        // Only s1 is audible, so position is s1
        assertEquals(0.0, result.x(), 0.001);
        assertEquals(64.0, result.y(), 0.001);
        assertEquals(0.0, result.z(), 0.001);
    }

    @Test
    void allSpeakersOutOfRangeYieldsZeroGain() {
        SpatialAudioCalculator.SpeakerEmitter s1 =
                new SpatialAudioCalculator.SpeakerEmitter(0.0, 64.0, 0.0, 16, 1.0f, 1.0f);

        // Listener at (100, 64, 100) -> distance > 16
        SpatialAudioCalculator.VirtualEmitterResult result =
                SpatialAudioCalculator.calculateVirtualEmitter(100.0, 64.0, 100.0, List.of(s1));

        assertEquals(0.0f, result.maxGain(), 0.001f);
    }

    @Test
    void emptyEmitterListHandledSafely() {
        SpatialAudioCalculator.VirtualEmitterResult result =
                SpatialAudioCalculator.calculateVirtualEmitter(5.0, 10.0, 15.0, Collections.emptyList());

        assertEquals(0.0f, result.maxGain(), 0.001f);
        assertEquals(5.0, result.x(), 0.001);
        assertEquals(10.0, result.y(), 0.001);
        assertEquals(15.0, result.z(), 0.001);
    }

    @Test
    void distanceGainClamping() {
        assertEquals(0.0f, SpatialAudioCalculator.calculateDistanceGain(50.0, 32, 1.0f, 1.0f));
        assertEquals(0.0f, SpatialAudioCalculator.calculateDistanceGain(-5.0, 32, 1.0f, 1.0f));
        assertEquals(0.0f, SpatialAudioCalculator.calculateDistanceGain(10.0, -1, 1.0f, 1.0f));
        assertEquals(0.0f, SpatialAudioCalculator.calculateDistanceGain(10.0, 32, 0.0f, 1.0f));
    }
}
