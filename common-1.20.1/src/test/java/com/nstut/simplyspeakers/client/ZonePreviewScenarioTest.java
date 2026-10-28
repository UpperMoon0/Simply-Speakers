package com.nstut.simplyspeakers.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZonePreviewScenarioTest {
    @Test void portableMeasurementsHaveIdenticalPosesAtDifferentBackendThroughputs() {
        var start=ZonePreviewScenario.listener(4,0);
        var settled=ZonePreviewScenario.listener(4,100);
        assertNotEquals(start,settled);
        assertEquals(settled,ZonePreviewScenario.listener(4,20));
        // Slow particle and fast shader renders can cover different game-tick spans.
        for(int sample=0;sample<1200;sample++) {
            assertEquals(settled,ZonePreviewScenario.listener(4,100+sample/4));
            assertEquals(settled,ZonePreviewScenario.listener(4,100+sample/7));
        }
    }

    @Test void everyNonPortablePhaseResetsListenerAndYaw() {
        var reset=new ZonePreviewScenario.Pose(0,-60,0,0);
        for(int phase:new int[]{0,1,2,3,5,6})
            for(int tick:new int[]{0,100,600})
                assertEquals(reset,ZonePreviewScenario.listener(phase,tick));
    }
}
