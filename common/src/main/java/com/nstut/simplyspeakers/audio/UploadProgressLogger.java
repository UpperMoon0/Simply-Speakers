package com.nstut.simplyspeakers.audio;

import org.slf4j.Logger;

import java.util.UUID;

/** Shared, low-noise logging policy for chunked uploads on every game version. */
public final class UploadProgressLogger {
    private static final long LOG_INTERVAL_BYTES = 1024L * 1024L;

    private UploadProgressLogger() {
    }

    public static void logStart(Logger logger, UUID transactionId, long totalBytes) {
        if (logger.isDebugEnabled()) {
            logger.debug("Starting audio upload {} ({} bytes)", transactionId, totalBytes);
        }
    }

    public static void logChunk(Logger logger, UUID transactionId, long offset, int length, long totalBytes) {
        if (!logger.isDebugEnabled()) {
            return;
        }

        long uploadedBytes = offset + length;
        if (shouldLogChunk(offset, length, totalBytes)) {
            int percent = totalBytes == 0 ? 100 : (int) Math.min(100L, uploadedBytes * 100L / totalBytes);
            logger.debug("Audio upload {}: {}% ({}/{} bytes)",
                    transactionId, percent, uploadedBytes, totalBytes);
        }
    }

    static boolean shouldLogChunk(long offset, int length, long totalBytes) {
        long uploadedBytes = offset + length;
        return offset == 0
                || uploadedBytes >= totalBytes
                || offset / LOG_INTERVAL_BYTES != uploadedBytes / LOG_INTERVAL_BYTES;
    }
}
