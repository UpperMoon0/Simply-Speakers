package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class AudioContextGateTest {
    @Test
    void startsAvailableAndRunsBothOperationTypes() {
        AudioContextGate gate = new AudioContextGate();
        AtomicInteger calls = new AtomicInteger();

        assertEquals(0, gate.epoch());
        assertTrue(gate.isAvailable());
        assertTrue(gate.isCurrent(0));
        assertEquals(1, gate.call(0, calls::incrementAndGet));
        gate.run(0, calls::incrementAndGet);
        assertEquals(2, calls.get());
        assertNull(gate.call(0, () -> null));
    }

    @Test
    void suspensionRejectsOldAndCurrentEpochWithoutInvokingCallbacks() {
        AudioContextGate gate = new AudioContextGate();
        long oldEpoch = gate.epoch();
        long suspendedEpoch = gate.suspend();
        AtomicInteger calls = new AtomicInteger();

        assertEquals(oldEpoch + 1, suspendedEpoch);
        assertFalse(gate.isAvailable());
        assertFalse(gate.isCurrent(oldEpoch));
        assertFalse(gate.isCurrent(suspendedEpoch));
        for (long epoch : new long[] {oldEpoch, suspendedEpoch}) {
            assertThrows(AudioContextGate.StaleContextException.class,
                    () -> gate.call(epoch, calls::incrementAndGet));
            assertThrows(AudioContextGate.StaleContextException.class,
                    () -> gate.run(epoch, calls::incrementAndGet));
        }
        assertEquals(0, calls.get());
    }

    @Test
    void resumePreservesCurrentEpochAndNeverRevivesOldEpochs() {
        AudioContextGate gate = new AudioContextGate();
        long originalEpoch = gate.epoch();
        long currentEpoch = gate.suspend();

        gate.resume();
        gate.resume();

        assertEquals(currentEpoch, gate.epoch());
        assertTrue(gate.isAvailable());
        assertTrue(gate.isCurrent(currentEpoch));
        assertFalse(gate.isCurrent(originalEpoch));
        assertEquals("ready", gate.call(currentEpoch, () -> "ready"));
        assertThrows(AudioContextGate.StaleContextException.class,
                () -> gate.run(originalEpoch, () -> { throw new AssertionError("stale callback"); }));
    }

    @Test
    void rejectsArbitraryMismatchedEpochsWhileAvailable() {
        AudioContextGate gate = new AudioContextGate();
        AtomicInteger calls = new AtomicInteger();

        for (long epoch : new long[] {-1, 1, Long.MAX_VALUE}) {
            assertFalse(gate.isCurrent(epoch));
            assertThrows(AudioContextGate.StaleContextException.class,
                    () -> gate.call(epoch, calls::incrementAndGet));
            assertThrows(AudioContextGate.StaleContextException.class,
                    () -> gate.run(epoch, calls::incrementAndGet));
        }
        assertEquals(0, calls.get());
    }

    @Test
    void staleCleanupCannotDeleteAReusedNativeSourceId() {
        AudioContextGate gate = new AudioContextGate();
        Map<Integer, String> nativeSources = new HashMap<>();
        int reusedSourceId = 7;
        long oldEpoch = gate.epoch();
        gate.run(oldEpoch, () -> nativeSources.put(reusedSourceId, "old playback"));

        gate.suspend();
        nativeSources.clear(); // Native context destruction invalidates all its IDs.
        gate.resume();
        long newEpoch = gate.epoch();
        gate.run(newEpoch, () -> nativeSources.put(reusedSourceId, "new playback"));

        assertThrows(AudioContextGate.StaleContextException.class,
                () -> gate.run(oldEpoch, () -> nativeSources.remove(reusedSourceId)));
        assertEquals("new playback", nativeSources.get(reusedSourceId));
        gate.run(newEpoch, () -> nativeSources.remove(reusedSourceId));
        assertTrue(nativeSources.isEmpty());
    }

    @Test
    void callbackFailuresPropagateWithoutChangingGateState() {
        AudioContextGate gate = new AudioContextGate();
        RuntimeException failure = new IllegalArgumentException("native operation failed");

        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> gate.call(0, () -> { throw failure; })));
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> gate.run(0, () -> { throw failure; })));
        assertTrue(gate.isCurrent(0));
        assertEquals(1, gate.suspend());
        gate.resume();
        assertEquals("ready", gate.call(1, () -> "ready"));
    }

    @Test
    void suspendWaitsForAnAlreadyRunningCall() throws Exception {
        assertSuspendWaitsForOperation(true);
    }

    @Test
    void suspendWaitsForAnAlreadyRunningRun() throws Exception {
        assertSuspendWaitsForOperation(false);
    }

    private static void assertSuspendWaitsForOperation(boolean returnsValue) throws Exception {
        AudioContextGate gate = new AudioContextGate();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();
        Runnable operation = () -> {
            entered.countDown();
            await(finish);
            completed.incrementAndGet();
        };
        FutureTask<Void> running = new FutureTask<>(() -> {
            if (returnsValue) {
                gate.call(0, () -> { operation.run(); return null; });
            } else {
                gate.run(0, operation);
            }
            return null;
        });
        FutureTask<Long> suspension = new FutureTask<>(gate::suspend);
        Thread operationThread = new Thread(running, "audio-gate-operation");
        Thread suspensionThread = new Thread(suspension, "audio-gate-suspend");
        operationThread.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            suspensionThread.start();
            awaitBlocked(suspensionThread);
            assertFalse(suspension.isDone());
            assertEquals(0, completed.get());
        } finally {
            finish.countDown();
            operationThread.join(5_000);
            suspensionThread.join(5_000);
        }

        running.get(5, TimeUnit.SECONDS);
        assertEquals(1L, suspension.get(5, TimeUnit.SECONDS));
        assertEquals(1, completed.get());
        assertFalse(gate.isAvailable());
        assertThrows(AudioContextGate.StaleContextException.class,
                () -> gate.run(0, completed::incrementAndGet));
        assertEquals(1, completed.get());
    }

    @Test
    void queuedCallbacksCannotEnterAfterSuspensionWinsTheMonitor() throws Exception {
        AudioContextGate gate = new AudioContextGate();
        AtomicInteger calls = new AtomicInteger();
        FutureTask<Void> queued = new FutureTask<>(() -> {
            assertThrows(AudioContextGate.StaleContextException.class,
                    () -> gate.run(0, calls::incrementAndGet));
            return null;
        });
        Thread thread = new Thread(queued, "audio-gate-queued");

        synchronized (gate) {
            thread.start();
            awaitBlocked(thread);
            gate.suspend();
        }

        queued.get(5, TimeUnit.SECONDS);
        thread.join(5_000);
        assertEquals(0, calls.get());
    }

    @Test
    void everyOverlappingSuspensionGetsADistinctIncreasingEpoch() throws Exception {
        AudioContextGate gate = new AudioContextGate();
        int suspensions = 64;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Long>> results = new ArrayList<>();
        try {
            for (int i = 0; i < suspensions; i++) {
                results.add(pool.submit(() -> { await(start); return gate.suspend(); }));
            }
            start.countDown();
            Set<Long> epochs = new HashSet<>();
            for (Future<Long> result : results) {
                assertTrue(epochs.add(result.get(5, TimeUnit.SECONDS)));
            }
            assertEquals(suspensions, epochs.size());
            for (long epoch = 1; epoch <= suspensions; epoch++) {
                assertTrue(epochs.contains(epoch));
            }
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }

        assertEquals(suspensions, gate.epoch());
        assertFalse(gate.isAvailable());
        gate.resume();
        assertTrue(gate.isCurrent(suspensions));
        for (long epoch = 0; epoch < suspensions; epoch++) {
            assertFalse(gate.isCurrent(epoch));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for test coordination");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while coordinating test", exception);
        }
    }

    private static void awaitBlocked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            assertTrue(thread.isAlive(), "Thread exited before waiting on the gate");
            Thread.sleep(1);
        }
        assertEquals(Thread.State.BLOCKED, thread.getState());
    }
}
