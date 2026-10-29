package com.nstut.simplyspeakers.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Manages the client-side audio cache directory with LRU (Least Recently Used) eviction.
 */
public final class ClientCacheManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("simplyspeakers");
    private static final long DEFAULT_MAX_CACHE_BYTES = 500L * 1024L * 1024L; // 500 MB
    // All completed files awaiting playback and all active audio readers remain
    // protected from eviction. Publication and eviction use this class monitor.
    private static final Map<Path, Integer> protectedFiles = new HashMap<>();

    private static Path key(File file) { return file.toPath().toAbsolutePath().normalize(); }

    public static synchronized void retain(File file) {
        if (file != null) protectedFiles.merge(key(file), 1, Integer::sum);
    }

    public static synchronized void release(File file) {
        if (file == null) return;
        Path path = key(file);
        Integer count = protectedFiles.get(path);
        if (count == null) return;
        if (count == 1) protectedFiles.remove(path);
        else protectedFiles.put(path, count - 1);
    }

    /** Install and reserve before another concurrent worker can evict the file. */
    public static synchronized void publishCompleted(File part, File completed) throws IOException {
        try {
            Files.move(part.toPath(), completed.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(part.toPath(), completed.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        retain(completed);
        recordAccess(completed);
    }

    private ClientCacheManager() {
    }

    /**
     * Marks a cached audio file as recently used by updating its last-modified timestamp.
     */
    public static void touch(File file) {
        if (file != null && file.exists()) {
            try {
                Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(System.currentTimeMillis()));
            } catch (IOException ignored) {
            }
        }
    }

    public static void recordAccess(File file) {
        touch(file);
    }

    public static synchronized void enforceBudget(File cacheDir) {
        enforceBudget(cacheDir, null);
    }

    /** Keep a completed download available until its queued playback opens it. */
    public static synchronized void enforceBudget(File cacheDir, File completedDownload) {
        long limit = com.nstut.simplyspeakers.Config.clientCacheLimitBytes;
        enforceCacheLimit(cacheDir, limit > 0 ? limit : DEFAULT_MAX_CACHE_BYTES, completedDownload);
    }

    /**
     * Enforces the cache size limit using LRU eviction.
     *
     * @param cacheDir The cache directory
     * @param maxBytes Maximum allowed total size in bytes (defaults to 500 MB if <= 0)
     */
    public static synchronized void enforceCacheLimit(File cacheDir, long maxBytes) {
        enforceCacheLimit(cacheDir, maxBytes, null);
    }

    private static synchronized void enforceCacheLimit(File cacheDir, long maxBytes, File keep) {
        if (cacheDir == null || !cacheDir.exists() || !cacheDir.isDirectory()) {
            return;
        }

        long budget = maxBytes > 0 ? maxBytes : DEFAULT_MAX_CACHE_BYTES;
        File[] files = cacheDir.listFiles(f -> f.isFile() && !f.getName().endsWith(".part") && !f.getName().endsWith(".tmp"));
        if (files == null || files.length == 0) {
            return;
        }

        long totalSize = 0;
        for (File f : files) {
            totalSize += f.length();
        }

        if (totalSize <= budget) {
            return;
        }

        LOGGER.info("Client audio cache exceeds budget ({} / {} bytes). Running LRU eviction.", totalSize, budget);

        // Sort oldest modified first
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));

        for (File f : files) {
            if (totalSize <= budget) {
                break;
            }
            if ((keep != null && key(f).equals(key(keep))) || protectedFiles.containsKey(key(f))) continue;
            long len = f.length();
            if (f.delete()) {
                totalSize -= len;
                LOGGER.debug("Evicted cached audio: {}", f.getName());
            }
        }
    }

    /**
     * Cleans up all orphaned .part and .tmp files in the cache directory.
     */
    public static void cleanTemporaryFiles(File cacheDir) {
        if (cacheDir == null || !cacheDir.exists() || !cacheDir.isDirectory()) {
            return;
        }
        File[] tempFiles = cacheDir.listFiles(f -> f.isFile() && (f.getName().endsWith(".part") || f.getName().endsWith(".tmp")));
        if (tempFiles != null) {
            for (File f : tempFiles) {
                f.delete();
            }
        }
    }
}
