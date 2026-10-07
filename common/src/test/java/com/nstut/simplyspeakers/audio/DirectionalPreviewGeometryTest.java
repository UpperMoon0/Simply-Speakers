package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DirectionalPreviewGeometryTest {
    @Test void previewMatchesPlaybackGainAndActualFacing() {
        var points=DirectionalPreviewGeometry.samples(16,1,1,90,.9f,5);
        var front=points.get(0); // east, first ring
        var rear=points.get(36); // west, same radius
        assertEquals(4,front.x(),.001);
        assertEquals(SpatialAudioCalculator.calculateDistanceGain(4,16,1,1,1,0,1,0,
            new SpatialAudioCalculator.ConeSettings(1,90,.9f)),front.gain(),.0001);
        assertTrue(front.gain()>rear.gain()*9);
        assertTrue(points.stream().anyMatch(p -> p.boundary() && Math.abs(Math.hypot(p.x(),p.z())-16)<.001));
    }
    @Test void omnidirectionalPreviewHasNoConeAndBoundedCostAtMaximumRange() {
        var points=DirectionalPreviewGeometry.samples(512,0,0,5,1,2);
        assertEquals(288,points.size());
        assertTrue(points.stream().allMatch(p -> p.gain()==1));
        assertEquals(352,DirectionalPreviewGeometry.samples(512,1,1,350,1,2).size());
    }
}
