package com.nstut.simplyspeakers.audio;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/** Thread-safe duplicate suppression with automatic retry after a timeout. */
public final class ExpiringRequestSet<K> {
    private final ConcurrentHashMap<K, Long> requests = new ConcurrentHashMap<>();
    private final long timeoutNanos;
    private final LongSupplier nanoTime;
    private final int maxEntries;
    private final AtomicInteger cleanupCounter = new AtomicInteger();

    public ExpiringRequestSet(Duration timeout) {
        this(timeout, System::nanoTime, 1024);
    }

    ExpiringRequestSet(Duration timeout, LongSupplier nanoTime) {
        this(timeout, nanoTime, 1024);
    }

    ExpiringRequestSet(Duration timeout, LongSupplier nanoTime, int maxEntries) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.timeoutNanos = timeout.toNanos();
        this.nanoTime = nanoTime;
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
    }

    public synchronized boolean tryAcquire(K key) {
        long now = nanoTime.getAsLong();
        if ((cleanupCounter.incrementAndGet() & 63) == 0) {
            requests.entrySet().removeIf(entry -> now - entry.getValue() >= timeoutNanos);
        }
        if (!requests.containsKey(key) && requests.size() >= maxEntries) {
            return false;
        }
        AtomicBoolean acquired = new AtomicBoolean();
        requests.compute(key, (ignored, startedAt) -> {
            if (startedAt == null || now - startedAt >= timeoutNanos) {
                acquired.set(true);
                return now;
            }
            return startedAt;
        });
        return acquired.get();
    }

    public synchronized void release(K key) {
        requests.remove(key);
    }

    synchronized int size() {
        return requests.size();
    }
}
