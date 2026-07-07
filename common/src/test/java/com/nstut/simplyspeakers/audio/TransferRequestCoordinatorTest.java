package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferRequestCoordinatorTest {
    @Test
    void concurrentAudioRequestsStartOneServerTransfer() throws InterruptedException {
        TransferRequestCoordinator<String> coordinator =
                new TransferRequestCoordinator<>(Duration.ofSeconds(30));
        AtomicInteger starts = new AtomicInteger();
        int callers = 1_000;
        CountDownLatch fire = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        for (int i = 0; i < callers; i++) {
            pool.execute(() -> {
                try {
                    fire.await();
                    coordinator.tryStart("player:audio", starts::incrementAndGet);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        fire.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(1, starts.get());
    }

    @Test
    void failedAndCompletedTransfersCanRetry() {
        TransferRequestCoordinator<String> coordinator =
                new TransferRequestCoordinator<>(Duration.ofSeconds(30));
        assertThrows(IllegalStateException.class,
                () -> coordinator.tryStart("audio", () -> { throw new IllegalStateException("queue rejected"); }));
        AtomicInteger starts = new AtomicInteger();
        coordinator.tryStart("audio", starts::incrementAndGet);
        coordinator.release("audio");
        coordinator.tryStart("audio", starts::incrementAndGet);
        assertEquals(2, starts.get());
    }
}
