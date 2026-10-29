package com.nstut.simplyspeakers.client;

import java.util.LinkedHashMap;
import java.util.Map;

/** FIFO pending requests coalesced by file ID, bounded independently from active slots. */
public final class BoundedAudioDownloadQueue {
    public record Request(String audioId, String filename) {}
    private final int limit;
    private final LinkedHashMap<String, String> entries = new LinkedHashMap<>();

    public BoundedAudioDownloadQueue(int limit) {
        if (limit < 1) throw new IllegalArgumentException("Queue limit must be positive");
        this.limit = limit;
    }

    public synchronized boolean offer(String audioId, String filename) {
        if (entries.containsKey(audioId)) return true;
        if (entries.size() >= limit) return false;
        entries.put(audioId, filename);
        return true;
    }
    public synchronized boolean contains(String audioId) { return entries.containsKey(audioId); }
    public synchronized Request poll() {
        if (entries.isEmpty()) return null;
        Map.Entry<String, String> first = entries.entrySet().iterator().next();
        Request item = new Request(first.getKey(), first.getValue());
        entries.remove(first.getKey());
        return item;
    }
    public synchronized void clear() { entries.clear(); }
    public synchronized int size() { return entries.size(); }

    /** Bounded backoff when another integration occupies the peer's send budget. */
    public static long retryDelayMillis(int failures) {
        if (failures < 1 || failures > 6) return -1;
        return Math.min(30_000L, 1_000L << (failures - 1));
    }
}
