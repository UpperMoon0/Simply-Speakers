package com.nstut.simplyspeakers.audio;

import java.time.Duration;

/** Starts at most one transfer per key during the timeout window. */
public final class TransferRequestCoordinator<K> {
    private final ExpiringRequestSet<K> requests;

    public TransferRequestCoordinator(Duration timeout) {
        this.requests = new ExpiringRequestSet<>(timeout);
    }

    TransferRequestCoordinator(ExpiringRequestSet<K> requests) {
        this.requests = requests;
    }

    public boolean tryStart(K key, Runnable starter) {
        if (!requests.tryAcquire(key)) {
            return false;
        }
        try {
            starter.run();
            return true;
        } catch (RuntimeException | Error exception) {
            requests.release(key);
            throw exception;
        }
    }

    public void release(K key) {
        requests.release(key);
    }
}
