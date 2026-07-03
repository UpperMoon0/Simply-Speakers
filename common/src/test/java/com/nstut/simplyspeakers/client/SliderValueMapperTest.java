package com.nstut.simplyspeakers.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SliderValueMapperTest {
    @Test
    void mapsTheSliderTrackToNormalizedValues() {
        assertEquals(0.0, SliderValueMapper.normalizedFromMouse(14, 10, 100), 0.0001);
        assertEquals(0.5, SliderValueMapper.normalizedFromMouse(60, 10, 100), 0.0001);
        assertEquals(1.0, SliderValueMapper.normalizedFromMouse(106, 10, 100), 0.0001);
    }

    @Test
    void clampsMousePositionsOutsideTheTrack() {
        assertEquals(0.0, SliderValueMapper.normalizedFromMouse(-100, 10, 100), 0.0001);
        assertEquals(1.0, SliderValueMapper.normalizedFromMouse(500, 10, 100), 0.0001);
    }
}
