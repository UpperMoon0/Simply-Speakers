package com.nstut.simplyspeakers.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundedAudioDownloadQueueTest {
    @Test void ninthDistinctUncachedAudioStartsWhenFirstOfEightCompletes() {
        BoundedAudioDownloadQueue waiting = new BoundedAudioDownloadQueue(64);
        java.util.Set<String> active = new java.util.HashSet<>();
        for (int i=0;i<9;i++) {
            assertTrue(waiting.offer("track-"+i,i+".wav"));
            while (active.size()<8 && waiting.size()>0) active.add(waiting.poll().audioId());
        }
        assertEquals(8,active.size());
        assertEquals(1,waiting.size());
        assertTrue(active.remove("track-0"));
        assertEquals("track-8",waiting.poll().audioId());
    }

    @Test void packetweaveBusyRejectionOrTimeoutFreesSlotsAndPreservesFairOrder() {
        BoundedAudioDownloadQueue waiting = new BoundedAudioDownloadQueue(2);
        assertTrue(waiting.offer("ninth","9.wav"));
        assertTrue(waiting.offer("tenth","10.wav"));
        assertTrue(waiting.offer("ninth","duplicate.wav"));
        assertFalse(waiting.offer("eleventh","11.wav"));
        // Server -1 admission rejection and timed-out partial transfers are both
        // removed from active before the same FIFO drain is invoked.
        assertEquals("ninth",waiting.poll().audioId());
        assertEquals("tenth",waiting.poll().audioId());
        assertNull(waiting.poll());
        waiting.offer("old-world","old.wav");
        waiting.clear();
        assertNull(waiting.poll());
    }
}
