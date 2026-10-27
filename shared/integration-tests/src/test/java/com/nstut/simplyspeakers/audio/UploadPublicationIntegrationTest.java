package com.nstut.simplyspeakers.audio;

import com.nstut.packetweave.api.BoundedFileReceiver;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Real versioned publication and PacketWeave worker; no duplicate state machine. */
class UploadPublicationIntegrationTest {
    @TempDir Path world;

    @Test void disconnectAndShutdownBeforeCommitPreventPublicationAndDeleteFile() throws Exception {
        for (boolean shutdown : new boolean[]{false, true}) {
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            AudioFileManager manager = new AudioFileManager(world.resolve("cancel-" + shutdown)) {
                @Override void beforeUploadCommit() throws IOException { block(entered, release); }
            };
            try {
                UUID owner = UUID.randomUUID();
                Upload upload = start(manager, owner, wav());
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                runNonblocking(() -> { if (shutdown) manager.shutdown(); else manager.cancelUploads(owner); });
                release.countDown();
                awaitFinished(upload.receiver);
                assertEquals(1, upload.failures.get());
                assertEquals(0, upload.successes.get());
                assertTrue(manager.getManifest().isEmpty());
                assertFalse(Files.exists(upload.session.tempFilePath));
                assertNull(manager.getAudioFilePath(upload.session.audioId));
            } finally { release.countDown(); manager.shutdown(); }
        }
    }

    @Test void claimedCommitDrainsAcrossDisconnectAndShutdownWithoutBlockingCaller() throws Exception {
        for (boolean shutdown : new boolean[]{false, true}) {
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            Path root = world.resolve("claimed-" + shutdown);
            AudioFileManager manager = new AudioFileManager(root) {
                @Override void persistManifest(Map<String, AudioFileMetadata> snapshot) throws IOException {
                    block(entered, release);
                    super.persistManifest(snapshot);
                }
            };
            try {
                UUID owner = UUID.randomUUID();
                Upload upload = start(manager, owner, wav());
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertTrue(manager.getManifest().isEmpty(), "unpersisted entry must not be discoverable");
                runNonblocking(() -> { if (shutdown) manager.shutdown(); else manager.cancelUploads(owner); });
                release.countDown();
                awaitFinished(upload.receiver);
                assertEquals(0, upload.failures.get());
                assertEquals(1, upload.successes.get());
                assertEquals(1, manager.getManifest().size());
                assertEquals(owner.toString(), manager.getManifest().get(upload.session.audioId).getOwnerUUID());
                assertEquals(1, manager.getAudioListForPlayer(owner.toString()).size());
                assertTrue(manager.getAudioListForPlayer(UUID.randomUUID().toString()).isEmpty());
                assertTrue(Files.exists(manager.getAudioFilePath(upload.session.audioId)));
                AudioFileManager reloaded = new AudioFileManager(root);
                try { assertEquals(1, reloaded.getManifest().size()); }
                finally { reloaded.shutdown(); }
            } finally { release.countDown(); manager.shutdown(); }
        }
    }

    @Test void invalidAudioAndManifestFailureRemainUnpublishedAndReceiverCleansThem() throws Exception {
        for (boolean invalid : new boolean[]{false, true}) {
            AudioFileManager manager = new AudioFileManager(world.resolve("failed-" + invalid)) {
                @Override void persistManifest(Map<String, AudioFileMetadata> snapshot) throws IOException {
                    throw new IOException("simulated locked manifest");
                }
            };
            try {
                Upload upload = start(manager, UUID.randomUUID(), invalid ? new byte[]{1,2,3,4} : wav());
                awaitFinished(upload.receiver);
                assertEquals(1, upload.failures.get());
                assertEquals(0, upload.successes.get());
                assertTrue(manager.getManifest().isEmpty());
                assertFalse(Files.exists(upload.session.tempFilePath));
            } finally { manager.shutdown(); }
        }
    }

    @Test void reopeningWorldWhileOldCommitDrainsSharesPublishedCatalogWithoutWaiting() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AudioFileManager old = new AudioFileManager(world) {
            @Override void persistManifest(Map<String, AudioFileMetadata> snapshot) throws IOException {
                block(entered, release);
                super.persistManifest(snapshot);
            }
        };
        AudioFileManager replacement = null;
        ExecutorService constructor = Executors.newSingleThreadExecutor();
        try {
            Upload upload = start(old, UUID.randomUUID(), wav());
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            old.shutdown();
            replacement = constructor.submit(() -> new AudioFileManager(world)).get(1, TimeUnit.SECONDS);
            assertTrue(replacement.getManifest().isEmpty());
            release.countDown();
            awaitFinished(upload.receiver);
            assertEquals(1, replacement.getManifest().size());
            var second = replacement.saveFile(new java.io.ByteArrayInputStream(wav()), "new.wav", UUID.randomUUID().toString());
            assertEquals(2, replacement.getManifest().size());
            assertNotNull(replacement.getAudioFilePath(upload.session.audioId));
            assertNotNull(replacement.getAudioFilePath(second.getUuid()));
            // Read actual persisted bytes; a shared in-memory lookup cannot prove this.
            String persisted = Files.readString(world.resolve("simply_speakers_audios/audio_manifest.json"));
            assertTrue(persisted.contains(upload.session.audioId));
            assertTrue(persisted.contains(second.getUuid()));
            assertEquals(0, upload.failures.get());
        } finally {
            release.countDown(); old.shutdown();
            if (replacement != null) replacement.shutdown();
            constructor.shutdownNow();
        }
    }

    private static Upload start(AudioFileManager manager, UUID owner, byte[] bytes) throws Exception {
        var session = manager.createUploadSession(UUID.randomUUID(), "test.wav", bytes.length, BlockPos.ZERO, owner);
        assertNotNull(session);
        var field = AudioFileManager.class.getDeclaredField("uploadReceiver");
        field.setAccessible(true);
        var receiver = (BoundedFileReceiver) field.get(manager);
        var upload = new Upload(session, receiver, new AtomicInteger(), new AtomicInteger());
        var committed = new java.util.concurrent.atomic.AtomicLong();
        assertTrue(receiver.begin(session.key, session.tempFilePath, bytes.length, new BoundedFileReceiver.Listener() {
            public void committed(long count, boolean complete) throws Exception {
                committed.set(count);
                if (complete) { manager.publishUpload(session); upload.successes.incrementAndGet(); }
            }
            public void failed(Exception error) { upload.failures.incrementAndGet(); }
        }));
        // Follow the same committed-byte receipt boundary as the production sender.
        int chunkBytes = com.nstut.packetweave.api.OutboundTransfers.CHUNK_BYTES;
        for (int offset=0, index=0; offset<bytes.length; index++) {
            int end=Math.min(bytes.length,offset+chunkBytes);
            if (index>0) {
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                while (committed.get()<offset && System.nanoTime()<deadline) Thread.sleep(1);
                assertEquals(offset,committed.get());
            }
            assertTrue(receiver.accept(session.key,index,java.util.Arrays.copyOfRange(bytes,offset,end)));
            offset=end;
        }
        return upload;
    }

    private record Upload(AudioFileManager.UploadSession session, BoundedFileReceiver receiver,
                          AtomicInteger successes, AtomicInteger failures) { }
    private static void block(CountDownLatch entered, CountDownLatch release) throws IOException {
        entered.countDown();
        try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("test latch timed out"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
    }
    private static void runNonblocking(Runnable action) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try { executor.submit(action).get(1, TimeUnit.SECONDS); }
        finally { executor.shutdownNow(); }
    }
    private static void awaitFinished(BoundedFileReceiver receiver) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (receiver.activeSessions() != 0 && System.nanoTime() < until) Thread.sleep(5);
        assertEquals(0, receiver.activeSessions());
    }
    private static byte[] wav() {
        int data = 16000;
        ByteBuffer out = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(36 + data);
        out.put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1);
        out.putInt(8000).putInt(16000).putShort((short)2).putShort((short)16);
        out.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(data);
        return out.array();
    }
}
