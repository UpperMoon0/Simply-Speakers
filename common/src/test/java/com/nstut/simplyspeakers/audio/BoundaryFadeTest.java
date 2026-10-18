package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundaryFadeTest {
    private static long ms(long time) { return time * 1_000_000; }

    @Test void entryExitAndReversalUseOneContinuousEnvelope() {
        var fade = new BoundaryFade();
        assertEquals(0f, fade.gain(true, .8f, ms(0), 200, 400));
        assertEquals(.4f, fade.gain(true, .8f, ms(100), 200, 400), .0001);
        assertEquals(.8f, fade.gain(true, .8f, ms(200), 200, 400), .0001);
        assertEquals(.8f, fade.gain(false, 0f, ms(200), 200, 400), .0001);
        assertEquals(.6f, fade.gain(false, 0f, ms(300), 200, 400), .0001);
        assertEquals(.4f, fade.gain(true, .8f, ms(400), 200, 400), .0001);
        assertEquals(.8f, fade.gain(true, .8f, ms(500), 200, 400), .0001);
        fade.gain(false, 0f, ms(500), 200, 400);
        assertEquals(0f, fade.gain(false, 0f, ms(900), 200, 400));
        assertTrue(fade.silent());
    }

    @Test void distanceGainRemainsIndependentAndZeroDisablesEitherFade() {
        var fade = new BoundaryFade();
        assertEquals(.9f, fade.gain(true, .9f, ms(0), 0, 250));
        assertEquals(.1f, fade.gain(true, .1f, ms(10), 0, 250));
        assertEquals(0f, fade.gain(false, 0f, ms(20), 0, 0));
        assertEquals(.7f, fade.gain(true, .7f, ms(30), 0, 0));
    }

    @Test void neverAudibleSourceCannotProduceAnExitTail() {
        var fade = new BoundaryFade();
        assertEquals(0f, fade.gain(false, 1f, ms(0), 250, 250));
        assertTrue(fade.silent());
        assertEquals(0f, fade.gain(true, .5f, ms(10), 250, 250));
    }
}
