package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import static org.junit.jupiter.api.Assertions.*;

class ClientCacheBudgetTest {
    @TempDir Path directory;
    @Test void smallPersonalBudgetDoesNotDeleteTheCompletedTrackBeforePlayback() throws Exception {
        Path older=directory.resolve("older.wav"), current=directory.resolve("downloaded.wav");
        Files.write(older,new byte[32]);Files.write(current,new byte[128]);
        Files.setLastModifiedTime(older,FileTime.fromMillis(1000));
        Files.setLastModifiedTime(current,FileTime.fromMillis(2000));
        long previous=Config.clientCacheLimitBytes;
        try {
            Config.clientCacheLimitBytes=64;
            ClientCacheManager.enforceBudget(directory.toFile(),current.toFile());
            assertFalse(Files.exists(older));
            assertTrue(Files.exists(current));assertEquals(128,Files.size(current));
        } finally { Config.clientCacheLimitBytes=previous; }
    }
    @Test void ordinaryCleanupStillEnforcesTheLimit() throws Exception {
        Path older=directory.resolve("older.wav"), newest=directory.resolve("newest.wav");
        Files.write(older,new byte[32]);Files.write(newest,new byte[32]);
        Files.setLastModifiedTime(older,FileTime.fromMillis(1000));
        Files.setLastModifiedTime(newest,FileTime.fromMillis(2000));
        ClientCacheManager.enforceCacheLimit(directory.toFile(),32);
        assertFalse(Files.exists(older));assertTrue(Files.exists(newest));
    }
}
