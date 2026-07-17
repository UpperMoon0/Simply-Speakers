package com.nstut.simplyspeakers.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeferredTaskQueueTest {
    @Test
    void deferredJoinPacketsRunOnceWhenTheWorldBecomesReady() {
        DeferredTaskQueue queue = new DeferredTaskQueue();
        AtomicInteger calls = new AtomicInteger();

        queue.defer(calls::incrementAndGet);
        assertEquals(1, queue.size());
        assertEquals(0, calls.get());

        queue.drain();
        queue.drain();
        assertEquals(0, queue.size());
        assertEquals(1, calls.get());
    }

    @Test
    void disconnectDiscardsPacketsFromThePreviousWorld() {
        DeferredTaskQueue queue = new DeferredTaskQueue();
        AtomicInteger calls = new AtomicInteger();

        queue.defer(calls::incrementAndGet);
        queue.clear();
        queue.drain();

        assertEquals(0, calls.get());
    }
}
