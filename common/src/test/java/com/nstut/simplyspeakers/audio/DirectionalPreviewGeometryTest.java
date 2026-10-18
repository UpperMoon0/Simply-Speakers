package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DirectionalPreviewGeometryTest {
    @Test void interiorColoursUseActualPlaybackGainAndFacing() {
        for(int facing=2;facing<=5;facing++) {
            double[] direction=DirectionalAudio.facingFromOrdinal(facing);
            var cone=new SpatialAudioCalculator.ConeSettings(1,90,.9f);
            for(var p:DirectionalPreviewGeometry.samples(16,1,1,90,.9f,facing)) {
                if(p.kind()!=DirectionalPreviewGeometry.Kind.LEVEL) continue;
                double distance=Math.sqrt(p.x()*p.x()+p.y()*p.y()+p.z()*p.z());
                assertEquals(SpatialAudioCalculator.calculateDistanceGain(distance,16,1,1,
                    direction[0],direction[1],p.x(),p.z(),cone),p.gain(),.0001);
            }
        }
        var points=DirectionalPreviewGeometry.samples(16,1,1,90,.9f,5);
        assertTrue(points.get(0).gain()>points.get(48).gain()*9);
    }
    @Test void cyanOutlinesShowSphericalRangeIncludingHeight() {
        var points=DirectionalPreviewGeometry.samples(16,1,1,90,.9f,5);
        for(var p:points) if(p.boundary()) {
            assertEquals(16,Math.sqrt(p.x()*p.x()+p.y()*p.y()+p.z()*p.z()),.0001);
            assertEquals(0,p.gain());
            assertEquals(0x5EE3CB,DirectionalPreviewGeometry.color(p));
        }
        assertTrue(points.stream().anyMatch(p -> p.y()>=16));
        assertTrue(points.stream().anyMatch(p -> p.y()<=-16));
        assertTrue(points.stream().filter(p -> p.kind()==DirectionalPreviewGeometry.Kind.DIRECTION)
            .allMatch(p -> !p.boundary() && DirectionalPreviewGeometry.color(p)==0xF4F7FF));
    }
    @Test void costIsBoundedAndOmnidirectionalHasNoDirectionGuides() {
        var small=DirectionalPreviewGeometry.samples(1,0,0,5,1,2);
        var large=DirectionalPreviewGeometry.samples(512,0,0,5,1,2);
        assertEquals(small.size(),large.size());
        assertTrue(large.size()<512);
        assertTrue(large.stream().noneMatch(p -> p.kind()==DirectionalPreviewGeometry.Kind.DIRECTION));
        assertTrue(large.stream().filter(p -> p.kind()==DirectionalPreviewGeometry.Kind.LEVEL).allMatch(p -> p.gain()==1));
        assertTrue(DirectionalPreviewGeometry.samples(512,1,1,350,1,2).size()<512);
    }
}
