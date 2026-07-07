package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpiringRequestSetTest {
    @Test
    void suppressesDuplicatesAndAllowsRelease() {
        ExpiringRequestSet<String> requests = new ExpiringRequestSet<>(Duration.ofSeconds(30));

        assertTrue(requests.tryAcquire("audio"));
        assertFalse(requests.tryAcquire("audio"));
        requests.release("audio");
        assertTrue(requests.tryAcquire("audio"));
    }

    @Test
    void permitsRetryAfterTimeout() {
        AtomicLong clock = new AtomicLong();
        ExpiringRequestSet<String> requests = new ExpiringRequestSet<>(Duration.ofSeconds(30), clock::get);

        assertTrue(requests.tryAcquire("audio"));
        clock.set(Duration.ofSeconds(29).toNanos());
        assertFalse(requests.tryAcquire("audio"));
        clock.set(Duration.ofSeconds(30).toNanos());
        assertTrue(requests.tryAcquire("audio"));
    }

    @Test
    void boundsUniqueRequestStorms() {
        ExpiringRequestSet<Integer> requests =
                new ExpiringRequestSet<>(Duration.ofSeconds(30), System::nanoTime, 128);

        long accepted = IntStream.range(0, 10_000).filter(requests::tryAcquire).count();

        assertEquals(128, accepted);
        assertEquals(128, requests.size());
    }
}
