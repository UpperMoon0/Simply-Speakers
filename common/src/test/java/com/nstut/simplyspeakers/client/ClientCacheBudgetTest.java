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
    @Test void parallelPublicationsCannotEvictFilesWaitingForPlayback() throws Exception {
        Path onePart=directory.resolve("one.part"), twoPart=directory.resolve("two.part");
        Path one=directory.resolve("one.wav"), two=directory.resolve("two.wav");
        Files.write(onePart,new byte[70]);Files.write(twoPart,new byte[70]);
        long original=Config.clientCacheLimitBytes;
        try {
            Config.clientCacheLimitBytes=50;
            ClientCacheManager.publishCompleted(onePart.toFile(),one.toFile());
            ClientCacheManager.publishCompleted(twoPart.toFile(),two.toFile());
            var workers=new java.util.ArrayList<Thread>();
            for(int i=0;i<4;i++) {
                Thread worker=new Thread(()-> ClientCacheManager.enforceBudget(directory.toFile()));
                workers.add(worker);worker.start();
            }
            for(Thread worker:workers) worker.join();
            assertTrue(Files.exists(one),"first finished track must survive second eviction");
            assertTrue(Files.exists(two),"second track must survive first eviction");
            ClientCacheManager.retain(one.toFile()); // Decoder lease.
            ClientCacheManager.release(one.toFile()); // Download callback released.
            ClientCacheManager.enforceBudget(directory.toFile());
            assertTrue(Files.exists(one),"decoder must retain the file");
            ClientCacheManager.release(one.toFile());
            ClientCacheManager.enforceBudget(directory.toFile());
            assertFalse(Files.exists(one),"completed and unpinned files are evictable");
            assertTrue(Files.exists(two),"independent playback remains pinned");
        } finally {
            ClientCacheManager.release(one.toFile());
            ClientCacheManager.release(two.toFile());
            Config.clientCacheLimitBytes=original;
        }
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
