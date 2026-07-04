package com.nstut.simplyspeakers.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadProgressLoggerTest {
    @Test
    void logsFirstFinalAndOneMiBBoundariesOnly() {
        int totalBytes = 3 * 1024 * 1024;

        assertTrue(UploadProgressLogger.shouldLogChunk(0, 32_000, totalBytes));
        assertFalse(UploadProgressLogger.shouldLogChunk(32_000, 32_000, totalBytes));
        assertTrue(UploadProgressLogger.shouldLogChunk(1_024_000, 32_000, totalBytes));
        assertFalse(UploadProgressLogger.shouldLogChunk(1_056_000, 32_000, totalBytes));
        assertTrue(UploadProgressLogger.shouldLogChunk(totalBytes - 1_000, 1_000, totalBytes));
    }
}
