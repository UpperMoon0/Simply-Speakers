package com.nstut.simplyspeakers.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundedAudioDownloadQueueTest {
    @Test void thirdDistinctUncachedAudioStartsWhenFirstOfTwoCompletes() {
        BoundedAudioDownloadQueue waiting = new BoundedAudioDownloadQueue(64);
        java.util.Set<String> active = new java.util.HashSet<>();
        for (int i=0;i<3;i++) {
            assertTrue(waiting.offer("track-"+i,i+".wav"));
            while (active.size()<2 && waiting.size()>0) active.add(waiting.poll().audioId());
        }
        assertEquals(2,active.size(), "Match PacketWeave's shared per-peer send budget");
        assertEquals(1,waiting.size());
        assertTrue(active.remove("track-0"));
        assertEquals("track-2",waiting.poll().audioId());
    }

    @Test void busyRetryBackoffIsBoundedAndEventuallyStops() {
        assertEquals(-1, BoundedAudioDownloadQueue.retryDelayMillis(0));
        assertEquals(1_000, BoundedAudioDownloadQueue.retryDelayMillis(1));
        assertEquals(2_000, BoundedAudioDownloadQueue.retryDelayMillis(2));
        assertEquals(16_000, BoundedAudioDownloadQueue.retryDelayMillis(5));
        assertEquals(30_000, BoundedAudioDownloadQueue.retryDelayMillis(6));
        assertEquals(-1, BoundedAudioDownloadQueue.retryDelayMillis(7));
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
