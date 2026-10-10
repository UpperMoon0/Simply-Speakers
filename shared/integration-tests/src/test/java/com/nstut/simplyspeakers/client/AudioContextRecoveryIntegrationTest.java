package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.SpeakerSettings;
import com.nstut.simplyspeakers.audio.AudioContextGate;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.network.SpeakerStateUpdatePacketS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.openal.AL10;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real client lifecycle, queued starts, transport cancellation and download completion.
 * Native audio and decoder-thread launch are isolated; these are not live-device tests. */
class AudioContextRecoveryIntegrationTest {
    private static final String SPEAKER_ID = "recovery-test";
    private static final String KEY = "net_" + SPEAKER_ID;
    private final BlockPos first = new BlockPos(12, 64, 30);
    private final BlockPos second = first.offset(1, 0, 0);
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final List<String> downloads = new ArrayList<>();
    private final List<BlockPos> queuedSourcePositions = new ArrayList<>();
    private final List<List<Object>> eofReports = new ArrayList<>();
    private final List<File> createdFiles = new ArrayList<>();
    private MockedStatic<Minecraft> minecraft;
    private MockedStatic<ClientAudioPlayer> audio;
    private MockedStatic<AL10> al;
    private Class<?> intentType;
    private Class<?> resourceType;

    @BeforeEach void setup() throws Exception {
        Minecraft client = mock(Minecraft.class, call -> {
            // 26.1.2 calls execute; earlier adapters call tell.
            if ((call.getMethod().getName().equals("tell") || call.getMethod().getName().equals("execute"))
                    && call.getArguments().length == 1 && call.getArgument(0) instanceof Runnable task) {
                tasks.add(task);
                return null;
            }
            return RETURNS_DEFAULTS.answer(call);
        });
        minecraft = mockStatic(Minecraft.class);
        minecraft.when(Minecraft::getInstance).thenReturn(client);
        intentType = Class.forName(ClientAudioPlayer.class.getName() + "$PlaybackIntent");
        resourceType = Class.forName(ClientAudioPlayer.class.getName() + "$StreamingAudioResource");
        // Keep all lifecycle/transport code real. Intercept the two network boundaries,
        // including their first invocation, rather than executing I/O while stubbing.
        audio = mockStatic(ClientAudioPlayer.class, call -> {
            if (call.getMethod().getName().equals("queueSource")) queuedSourcePositions.add(call.getArgument(1));
            if (call.getMethod().getName().equals("requestFileFromServer")) {
                downloads.add(call.getArgument(0));
                return null;
            }
            if (call.getMethod().getName().equals("sendRemoteStreamEofReport")) {
                eofReports.add(List.of(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
                return null;
            }
            return CALLS_REAL_METHODS.answer(call);
        });
        al = mockStatic(AL10.class);
        ClientAudioPlayer.stopAll();
        ClientSpeakerRegistry.clear();
        ClientAudioPlayer.soundContextReady();
        drainTasks();
    }

    @AfterEach void cleanup() throws Exception {
        try {
            // Test resources never run decoders. Drop them before stopAll so teardown
            // does not launch a cleanup thread outside the thread-local static mocks.
            resources().clear();
            ClientAudioPlayer.stopAll();
            ClientSpeakerRegistry.clear();
            ClientAudioPlayer.soundContextReady();
            drainTasks();
            for (File file : createdFiles) Files.deleteIfExists(file.toPath());
        } finally {
            if (al != null) al.close();
            if (audio != null) audio.close();
            if (minecraft != null) minecraft.close();
        }
    }

    @Test void delayedOldCleanupCannotDeleteIdsReusedAfterActualSuspendAndResume() throws Exception {
        AudioFileMetadata metadata = missingTrack();
        ClientAudioPlayer.play(first, SPEAKER_ID, metadata, 3.0f, false);
        Object intent = intents().get(KEY);
        try (var workers = mockConstruction(Thread.class, (thread, context) -> {
                 AtomicBoolean alive = new AtomicBoolean();
                 when(thread.isAlive()).thenAnswer(call -> alive.get());
                 doAnswer(call -> { alive.set(true); return null; }).when(thread).start();
             });
             var cache = mockStatic(ClientCacheManager.class)) {
            al.when(AL10::alGenSources).thenReturn(17);
            al.when(() -> AL10.alGenBuffers(any(int[].class))).thenAnswer(call -> {
                int[] buffers = call.getArgument(0);
                for (int index = 0; index < buffers.length; index++) buffers[index] = index + 1;
                return null;
            });
            finishDownload(metadata);
            drainTasks();
            Object old = resources().get(KEY);
            assertNotNull(old);
            long oldEpoch = ClientAudioPlayer.contextEpoch();
            // Model decoder exit while its client-thread native cleanup is still queued.
            // Blocked live-worker cleanup is covered separately by StreamingAudioCleanupIntegrationTest.
            Thread oldWorker = (Thread) field(resourceType, "streamingThread").get(old);
            when(oldWorker.isAlive()).thenReturn(false);

            ClientAudioPlayer.soundContextDestroying();
            assertFalse(ClientAudioPlayer.contextAvailable());
            assertTrue(ClientAudioPlayer.contextEpoch() > oldEpoch);
            assertTrue(resources().isEmpty());
            assertSame(intent, intents().get(KEY), "destroying native resources must preserve logical playback");
            assertTrue(((AtomicBoolean) field(resourceType, "stopFlag").get(old)).get());
            Runnable delayedCleanup = takeTask();

            ClientAudioPlayer.soundContextReady();
            assertTrue(ClientAudioPlayer.contextAvailable());
            // The actual restore scan finds the cached track and allocates again.
            // Delay old cleanup until the replacement context has reused all IDs.
            drainTasks();
            Object replacement = resources().get(KEY);
            assertNotNull(replacement);
            assertNotSame(old, replacement);
            assertEquals(17, field(resourceType, "sourceID").getInt(replacement));
            assertArrayEquals(new int[]{1, 2, 3}, (int[]) field(resourceType, "bufferIDs").get(replacement));
            assertEquals(ClientAudioPlayer.contextEpoch(), field(resourceType, "contextEpoch").getLong(replacement));
            assertEquals(2, workers.constructed().size());
            al.clearInvocations();
            delayedCleanup.run();
            al.verifyNoInteractions();
            assertSame(replacement, resources().get(KEY));
            assertSame(intent, intents().get(KEY));

            // The reused IDs are valid in the new context; only their old owner is stale.
            al.when(() -> AL10.alIsSource(17)).thenReturn(true);
            assertEquals(true, invoke(replacement, "isSource", new Class<?>[0]));
            invoke(replacement, "gain", new Class<?>[]{float.class}, 0.5f);
            al.verify(() -> AL10.alSourcef(17, AL10.AL_GAIN, 0.5f));
        }
    }

    @Test void everyOldResourceAudioOperationIsRejectedAfterReload() throws Exception {
        Object intent = trackedIntent(missingTrack(), 3.0f);
        Object old = resource(KEY, 17, new int[]{1, 2, 3});
        ClientAudioPlayer.soundContextDestroying();
        ClientAudioPlayer.soundContextReady();
        // Isolate epoch ownership from stop/map checks: this old object was not in
        // the destruction scan and otherwise still looks active to every wrapper.
        resources().put(KEY, old);
        assertFalse(((AtomicBoolean) field(resourceType, "stopFlag").get(old)).get());
        assertSame(intent, field(resourceType, "intent").get(old));

        assertStale(old, "sourceInt", new Class<?>[]{int.class}, AL10.AL_SOURCE_STATE);
        assertStale(old, "isSource", new Class<?>[0]);
        assertStale(old, "playSource", new Class<?>[0]);
        assertStale(old, "stopSource", new Class<?>[0]);
        assertStale(old, "bufferData", new Class<?>[]{int.class, int.class, ByteBuffer.class, int.class},
                1, AL10.AL_FORMAT_MONO16, ByteBuffer.allocateDirect(4), 8000);
        assertStale(old, "queueBuffer", new Class<?>[]{int.class}, 1);
        assertStale(old, "unqueueBuffer", new Class<?>[0]);
        assertStale(old, "unqueueBuffers", new Class<?>[]{int[].class}, (Object) new int[]{1, 2, 3});
        assertStale(old, "position", new Class<?>[]{float.class, float.class, float.class}, 1f, 2f, 3f);
        assertStale(old, "gain", new Class<?>[]{float.class}, 0.5f);
        assertStale(old, "restartLoopTimeline", new Class<?>[0]);
        assertTrue(offset(intent) >= 3.0f, "stale loop completion must not reset the retained timeline");
        al.verifyNoInteractions();
        tasks.clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void queuedOldEpochStartCannotAllocateDuringOrAfterReload(boolean resumed) throws Exception {
        Object intent = trackedIntent(missingTrack(), 2.0f);
        queueSource(first, intent);
        Runnable oldStart = takeTask();
        ClientAudioPlayer.soundContextDestroying();
        if (resumed) ClientAudioPlayer.soundContextReady();

        oldStart.run();
        al.verifyNoInteractions();
        assertTrue(resources().isEmpty());
        assertSame(intent, intents().get(KEY));
        tasks.clear();
    }

    @Test void pendingDownloadSurvivesReloadAndStartsAtElapsedPlaybackPosition() throws Exception {
        AudioFileMetadata metadata = missingTrack();
        ClientAudioPlayer.play(first, SPEAKER_ID, metadata, 12.0f, false);
        Object intent = intents().get(KEY);
        assertNotNull(intent);
        assertEquals(List.of(metadata.getUuid()), downloads);
        assertEquals(1, pending(metadata).size());
        ClientAudioPlayer.soundContextDestroying();
        ageTimeline(intent, 5_000_000_000L);
        ClientAudioPlayer.soundContextReady();
        drainTasks();
        assertSame(intent, intents().get(KEY));
        assertEquals(List.of(metadata.getUuid(), metadata.getUuid()), downloads);
        assertEquals(1, pending(metadata).size(), "recovery must not duplicate the same pending emitter request");
        assertTrue(offset(intent) >= 17.0f);
        al.verifyNoInteractions();

        // Construction mocking confines the actual production queue/allocation path
        // to this thread: no unmocked decoder thread or network connection can run.
        try (var workers = mockConstruction(Thread.class, (thread, context) -> {
                 AtomicBoolean alive = new AtomicBoolean();
                 when(thread.isAlive()).thenAnswer(call -> alive.get());
                 doAnswer(call -> { alive.set(true); return null; }).when(thread).start();
             });
             var cache = mockStatic(ClientCacheManager.class)) {
            al.when(AL10::alGenSources).thenReturn(71);
            finishDownload(metadata);
            drainTasks();
            Object restored = resources().get(KEY);
            assertNotNull(restored);
            assertSame(intent, field(resourceType, "intent").get(restored));
            assertEquals(ClientAudioPlayer.contextEpoch(), field(resourceType, "contextEpoch").getLong(restored));
            assertTrue(field(resourceType, "startOffsetSeconds").getFloat(restored) >= 17.0f,
                    "the restored decoder must include the time spent downloading and recovering");
            assertEquals(1, workers.constructed().size());
            verify(workers.constructed().get(0)).start();
            al.verify(AL10::alGenSources, times(1));
            assertFalse(pendingPlays().containsKey(metadata.getUuid()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"stop", "stopNetwork", "pause", "stopAll"})
    void transportCancellationDuringRecoveryPreventsBothOldAndNewEpochQueuedStarts(String action) throws Exception {
        AudioFileMetadata metadata = missingTrack();
        ClientAudioPlayer.play(first, SPEAKER_ID, metadata, 9.0f, false);
        Object intent = intents().get(KEY);
        queueSource(first, intent); // A callback queued before context destruction.
        ClientAudioPlayer.soundContextDestroying();
        queueSource(first, intent); // A download callback queued during recovery.
        switch (action) {
            case "stop" -> ClientAudioPlayer.stop(first);
            case "stopNetwork" -> ClientAudioPlayer.stopNetwork(KEY);
            case "pause" -> {
                // Exercise the production pause adapter rather than treating a stop
                // call as proof that the server's pause action is wired correctly.
                var packet = new SpeakerStateUpdatePacketS2C(SPEAKER_ID, "pause", metadata.getUuid(),
                        metadata.getOriginalFilename(), 0, false);
                method(SpeakerStateUpdatePacketS2C.class, "handleSpeakerStateUpdate", SpeakerStateUpdatePacketS2C.class)
                        .invoke(null, packet);
                assertTrue(ClientSpeakerRegistry.getState(KEY).isPaused());
            }
            case "stopAll" -> ClientAudioPlayer.stopAll();
            default -> fail("Unrecognized cancellation action: " + action);
        }
        ClientAudioPlayer.soundContextReady();
        drainTasks();
        assertFalse(intents().containsKey(KEY));
        assertTrue(resources().isEmpty());
        assertTrue(membership().getPositions(KEY).isEmpty());
        assertTrue(pendingPlays().values().stream().allMatch(List::isEmpty));
        al.verifyNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"track-change", "new-generation"})
    void latestPlayReplacesLiveOldResourceAndRejectsItsQueuedStart(String change) throws Exception {
        AudioFileMetadata oldMetadata = missingTrack();
        Object oldIntent = trackedIntent(oldMetadata, 4.0f);
        Object old = resource(KEY, 17, new int[]{1, 2, 3});
        Thread oldDecoder = mock(Thread.class);
        when(oldDecoder.isAlive()).thenReturn(true);
        field(resourceType, "streamingThread").set(old, oldDecoder);
        resources().put(KEY, old);
        queueSource(first, oldIntent);
        Runnable oldStart = takeTask();
        AudioFileMetadata latest = change.equals("track-change") ? missingTrack() : oldMetadata;
        int generation = change.equals("new-generation") ? 8 : 7;

        try (var workers = mockConstruction(Thread.class, (thread, context) -> {
                 AtomicBoolean alive = new AtomicBoolean();
                 when(thread.isAlive()).thenAnswer(call -> alive.get());
                 doAnswer(call -> { alive.set(true); return null; }).when(thread).start();
             });
             var cache = mockStatic(ClientCacheManager.class)) {
            ClientAudioPlayer.play(first, SPEAKER_ID, latest, 30.0f, false, 16, 1.0f, 1.0f,
                    null, "minecraft:overworld/" + KEY, generation);
            Object latestIntent = intents().get(KEY);
            assertNotNull(latestIntent);
            assertNotSame(oldIntent, latestIntent, "a live decoder must not suppress a new playback occurrence");
            assertSame(latest, field(intentType, "metadata").get(latestIntent));
            assertTrue(resources().isEmpty(), "replacement must remove the old source before awaiting a download");
            assertTrue(((AtomicBoolean) field(resourceType, "stopFlag").get(old)).get());
            verify(oldDecoder).interrupt();
            assertEquals(1, pending(latest).size());
            assertSame(latestIntent, field(pending(latest).get(0).getClass(), "intent").get(pending(latest).get(0)));
            assertEquals(1, workers.constructed().size(), "the blocked old decoder needs a deferred cleanup worker");
            verify(workers.constructed().get(0)).start();

            oldStart.run();
            al.verifyNoInteractions();
            assertTrue(resources().isEmpty());
            assertSame(latestIntent, intents().get(KEY));

            al.when(AL10::alGenSources).thenReturn(73);
            finishDownload(latest);
            drainTasks();
            Object replacement = resources().get(KEY);
            assertNotNull(replacement);
            assertSame(latestIntent, field(resourceType, "intent").get(replacement));
            assertTrue(field(resourceType, "startOffsetSeconds").getFloat(replacement) >= 30.0f);
            assertEquals(2, workers.constructed().size());
            verify(workers.constructed().get(1)).start();
            al.verify(AL10::alGenSources, times(1));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"context-reload", "intent-replaced"})
    void queuedUrlEofCannotReportOrRemoveReplacementPlayback(String invalidation) throws Exception {
        AudioFileMetadata metadata = new AudioFileMetadata("https://example.invalid/track.ogg", "track.ogg");
        Object oldIntent = trackedIntent(metadata, 4.0f);
        Object old = resource(KEY, 17, new int[]{1, 2, 3});
        resources().put(KEY, old);
        finishResource(old, true);
        Runnable oldEof = takeTask();
        Runnable oldCleanup = takeTask();
        assertTrue(resources().isEmpty());
        assertSame(oldIntent, intents().get(KEY));

        Object replacementIntent = oldIntent;
        if (invalidation.equals("context-reload")) {
            ClientAudioPlayer.soundContextDestroying();
            ClientAudioPlayer.soundContextReady();
        } else {
            replacementIntent = newIntent(metadata, 20.0f, 8);
            intents().put(KEY, replacementIntent);
        }
        Object replacement = resource(KEY, 18, new int[]{4, 5, 6});
        resources().put(KEY, replacement);
        oldEof.run();
        oldCleanup.run();
        assertTrue(eofReports.isEmpty(), "stale EOF must never advance the server playlist");
        assertSame(replacementIntent, intents().get(KEY));
        assertSame(replacement, resources().get(KEY));
        tasks.clear();
    }

    @Test void currentUrlEofReportsExactlyOnceAndRetiresItsIntent() throws Exception {
        AudioFileMetadata metadata = new AudioFileMetadata("https://example.invalid/current.ogg", "current.ogg");
        trackedIntent(metadata, 4.0f);
        Object current = resource(KEY, 17, new int[]{1, 2, 3});
        resources().put(KEY, current);
        finishResource(current, true);
        finishResource(current, true);
        drainTasks();
        assertEquals(List.of(List.of("minecraft:overworld/" + KEY, 7, metadata.getUuid())), eofReports);
        assertFalse(intents().containsKey(KEY));
        assertTrue(resources().isEmpty());
    }

    @Test void linkedPendingDownloadKeepsSecondEmitterAfterFirstDetaches() throws Exception {
        AudioFileMetadata metadata = missingTrack();
        ClientAudioPlayer.play(first, SPEAKER_ID, metadata, 6.0f, false);
        Object intent = intents().get(KEY);
        ClientAudioPlayer.play(second, SPEAKER_ID, metadata, 6.0f, false);
        assertEquals(2, pending(metadata).size(), "linked emitters each need a pending position until completion");
        ClientAudioPlayer.soundContextDestroying();
        ClientAudioPlayer.stop(first);
        assertEquals(1, pending(metadata).size());
        Object survivingRequest = pending(metadata).get(0);
        assertEquals(second, field(survivingRequest.getClass(), "pos").get(survivingRequest));
        assertSame(intent, intents().get(KEY));
        ClientAudioPlayer.soundContextReady();
        drainTasks();
        assertEquals(1, pending(metadata).size());

        try (var workers = mockConstruction(Thread.class, (thread, context) -> {
                 AtomicBoolean alive = new AtomicBoolean();
                 when(thread.isAlive()).thenAnswer(call -> alive.get());
                 doAnswer(call -> { alive.set(true); return null; }).when(thread).start();
             });
             var cache = mockStatic(ClientCacheManager.class)) {
            al.when(AL10::alGenSources).thenReturn(72);
            finishDownload(metadata);
            drainTasks();
            assertNotNull(resources().get(KEY), "the surviving linked emitter must start when its download completes");
            assertEquals(java.util.Set.of(second), membership().getPositions(KEY));
            assertSame(intent, field(resourceType, "intent").get(resources().get(KEY)));
            al.verify(() -> AL10.alSource3f(72, AL10.AL_POSITION,
                    second.getX() + 0.5f, second.getY() + 0.5f, second.getZ() + 0.5f));
            assertEquals(1, workers.constructed().size());
            verify(workers.constructed().get(0)).start();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void detachBetweenRestoreScanAndAllocationUsesSurvivorButNeverRevivesEmptyNetwork(boolean detachLast) throws Exception {
        AudioFileMetadata metadata = missingTrack();
        Object intent = trackedIntent(metadata, 5f);
        membership().track(second, KEY, new SpeakerSettings(1.0f, 16, 1.0f));
        File cacheDir = (File) field(ClientAudioPlayer.class, "CACHE_DIR").get(null);
        Files.createDirectories(cacheDir.toPath());
        File cached = new File(cacheDir, metadata.getUuid() + ".wav");
        Files.write(cached.toPath(), new byte[]{1, 2, 3, 4});
        createdFiles.add(cached);

        try (var workers = mockConstruction(Thread.class, (thread, context) -> {
                 AtomicBoolean alive = new AtomicBoolean();
                 when(thread.isAlive()).thenAnswer(call -> alive.get());
                 doAnswer(call -> { alive.set(true); return null; }).when(thread).start();
             });
             var cache = mockStatic(ClientCacheManager.class)) {
            ClientAudioPlayer.soundContextDestroying();
            ClientAudioPlayer.soundContextReady();
            takeTask().run(); // Real restore scan chooses and queues one representative.
            assertEquals(1, queuedSourcePositions.size());
            BlockPos selected = queuedSourcePositions.get(0);
            BlockPos survivor = selected.equals(first) ? second : first;
            Runnable allocation = takeTask();
            ClientAudioPlayer.stop(selected);
            if (detachLast) ClientAudioPlayer.stop(survivor);
            al.when(AL10::alGenSources).thenReturn(74);
            allocation.run();

            if (detachLast) {
                assertTrue(resources().isEmpty());
                assertFalse(intents().containsKey(KEY));
                assertTrue(membership().getPositions(KEY).isEmpty());
                assertTrue(workers.constructed().isEmpty());
                al.verifyNoInteractions();
            } else {
                Object restored = resources().get(KEY);
                assertNotNull(restored, "a detached representative must not strand its still-active network intent");
                assertSame(intent, field(resourceType, "intent").get(restored));
                assertEquals(java.util.Set.of(survivor), membership().getPositions(KEY));
                assertEquals(1, workers.constructed().size());
                al.verify(AL10::alGenSources, times(1));
                al.verify(() -> AL10.alSource3f(74, AL10.AL_POSITION,
                        survivor.getX() + 0.5f, survivor.getY() + 0.5f, survivor.getZ() + 0.5f));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(floats = {2.0f, 11.0f})
    void unknownLengthLoopingSeekLearnsDurationAndReopensAtWrappedOffset(float requested) throws Exception {
        Object intent = trackedIntent(missingTrack(), requested);
        field(intentType, "looping").setBoolean(intent, true);
        Object resource = resource(KEY, 17, new int[]{1, 2, 3});
        ((AtomicBoolean) field(resourceType, "isLooping").get(resource)).set(true);
        resources().put(KEY, resource);
        int bytes = 8000 * 2 * 2; // Two seconds of mono PCM; decoder reports unknown length.
        try (var stream = unknownLengthPcm(bytes)) {
            boolean retry = (Boolean) invoke(resource, "seek",
                    new Class<?>[]{javax.sound.sampled.AudioInputStream.class, float.class}, stream, requested);
            if (!retry) {
                assertEquals(-1, stream.read(new byte[2])); // Exact seek-to-EOF boundary.
                retry = (Boolean) invoke(resource, "recoverSeekAtEof", new Class<?>[0]);
            }
            assertTrue(retry);
        }
        assertEquals(16000L, field(intentType, "frameLengthHint").getLong(intent));
        try (var stream = unknownLengthPcm(bytes)) {
            assertEquals(false, invoke(resource, "seek",
                    new Class<?>[]{javax.sound.sampled.AudioInputStream.class, float.class}, stream, requested));
            assertEquals(requested == 2.0f ? bytes : bytes / 2, stream.readAllBytes().length,
                    "recovery must wrap the requested timeline, not blindly restart at zero");
        }
        al.verifyNoInteractions(); // Seeking and duration discovery never require native audio access.
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void emptyOrFiniteNonLoopingInputCannotRequestEndlessSeekRetries(boolean emptyLoop) throws Exception {
        Object intent = trackedIntent(missingTrack(), 11f);
        field(intentType, "looping").setBoolean(intent, emptyLoop);
        Object resource = resource(KEY, 17, new int[]{1, 2, 3});
        ((AtomicBoolean) field(resourceType, "isLooping").get(resource)).set(emptyLoop);
        resources().put(KEY, resource);
        try (var stream = unknownLengthPcm(emptyLoop ? 0 : 32000)) {
            assertEquals(false, invoke(resource, "seek",
                    new Class<?>[]{javax.sound.sampled.AudioInputStream.class, float.class}, stream, 11f));
            assertEquals(false, invoke(resource, "recoverSeekAtEof", new Class<?>[0]));
            assertEquals(-1, stream.read(new byte[2]));
        }
        assertEquals(-1L, field(intentType, "frameLengthHint").getLong(intent));
        al.verifyNoInteractions();
    }

    private javax.sound.sampled.AudioInputStream unknownLengthPcm(int bytes) {
        return new javax.sound.sampled.AudioInputStream(new java.io.ByteArrayInputStream(new byte[bytes]),
                new javax.sound.sampled.AudioFormat(8000, 16, 1, true, false), javax.sound.sampled.AudioSystem.NOT_SPECIFIED);
    }

    private AudioFileMetadata missingTrack() {
        return new AudioFileMetadata("context-recovery-" + UUID.randomUUID(), "test.wav");
    }

    private Object trackedIntent(AudioFileMetadata metadata, float offset) throws Exception {
        Object intent = newIntent(metadata, offset, 7);
        intents().put(KEY, intent);
        membership().track(first, KEY, new SpeakerSettings(1.0f, 16, 1.0f));
        return intent;
    }

    private Object newIntent(AudioFileMetadata metadata, float offset, int generation) throws Exception {
        var constructor = intentType.getDeclaredConstructor(AudioFileMetadata.class, float.class, boolean.class, String.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(metadata, offset, false, "minecraft:overworld/" + KEY, generation);
    }

    private Object resource(String key, int source, int[] buffers) throws Exception {
        var constructor = resourceType.getDeclaredConstructor(String.class, int.class, int[].class, Thread.class,
                boolean.class, String.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(key, source, buffers, null, false, "minecraft:overworld/" + key, 7);
    }

    private void queueSource(BlockPos pos, Object intent) throws Exception {
        method(ClientAudioPlayer.class, "queueSource", String.class, BlockPos.class, intentType, boolean.class, String.class)
                .invoke(null, KEY, pos, intent, false, "unused-decoder-input.wav");
    }

    private void finishResource(Object resource, boolean reportEof) throws Exception {
        method(ClientAudioPlayer.class, "finishResource", resourceType, boolean.class).invoke(null, resource, reportEof);
    }

    private void finishDownload(AudioFileMetadata metadata) throws Exception {
        File cache = (File) field(ClientAudioPlayer.class, "CACHE_DIR").get(null);
        createdFiles.add(new File(cache, metadata.getUuid() + ".part"));
        createdFiles.add(new File(cache, metadata.getUuid() + ".wav"));
        Class<?> downloadType = Class.forName(ClientAudioPlayer.class.getName() + "$DownloadProcess");
        var constructor = downloadType.getDeclaredConstructor(String.class, String.class);
        constructor.setAccessible(true);
        Object download = constructor.newInstance(metadata.getUuid(), metadata.getOriginalFilename());
        map("activeDownloads").put(metadata.getUuid(), download);
        ClientAudioPlayer.handleAudioFileChunk(metadata.getUuid(), new byte[]{1, 2, 3, 4}, true);
    }

    private void ageTimeline(Object intent, long elapsedNanos) throws Exception {
        Object timeline = field(intentType, "timeline").get(intent);
        Field anchor = field(timeline.getClass(), "anchorNanos");
        anchor.setLong(timeline, anchor.getLong(timeline) - elapsedNanos);
    }

    private float offset(Object intent) throws Exception {
        return (Float) method(intentType, "offsetSeconds").invoke(intent);
    }

    private void assertStale(Object resource, String name, Class<?>[] parameters, Object... arguments) throws Exception {
        Method operation = method(resourceType, name, parameters);
        InvocationTargetException exception = assertThrows(InvocationTargetException.class, () -> operation.invoke(resource, arguments), name);
        assertInstanceOf(AudioContextGate.StaleContextException.class, exception.getCause(), name);
    }

    private static Object invoke(Object target, String name, Class<?>[] parameters, Object... arguments) throws Exception {
        return method(target.getClass(), name, parameters).invoke(target, arguments);
    }

    private Runnable takeTask() {
        Runnable task = tasks.poll();
        assertNotNull(task, "production code must schedule the expected client callback");
        return task;
    }

    private void drainTasks() {
        int count = 0;
        for (Runnable task; (task = tasks.poll()) != null;) {
            assertTrue(++count <= 30, "client callbacks must settle without unbounded rescheduling");
            task.run();
        }
    }

    private Map<String, Object> resources() throws Exception { return map("networkResources"); }
    private Map<String, Object> intents() throws Exception { return map("playbackIntents"); }
    @SuppressWarnings("unchecked")
    private Map<String, Object> map(String name) throws Exception {
        return (Map<String, Object>) field(ClientAudioPlayer.class, name).get(null);
    }
    @SuppressWarnings("unchecked")
    private Map<String, List<Object>> pendingPlays() throws Exception {
        return (Map<String, List<Object>>) field(ClientAudioPlayer.class, "pendingPlays").get(null);
    }
    private List<Object> pending(AudioFileMetadata metadata) throws Exception {
        return pendingPlays().getOrDefault(metadata.getUuid(), List.of());
    }
    @SuppressWarnings("unchecked")
    private PlaybackMembership<BlockPos> membership() throws Exception {
        return (PlaybackMembership<BlockPos>) field(ClientAudioPlayer.class, "membership").get(null);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }
}
