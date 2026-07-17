package com.nstut.simplyspeakers.client;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Holds client work that arrives before the client world is ready. */
public final class DeferredTaskQueue {
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    public void defer(Runnable task) {
        tasks.add(task);
    }

    public void drain() {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            task.run();
        }
    }

    public void clear() {
        tasks.clear();
    }

    public int size() {
        return tasks.size();
    }
}
