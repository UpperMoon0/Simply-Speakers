package com.nstut.simplyspeakers.speakers;

import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.api.SpeakerEvents;
import com.nstut.simplyspeakers.network.PlayAudioPacketS2C;
import com.nstut.simplyspeakers.network.PlaylistControlPacketC2S;
import com.nstut.simplyspeakers.network.PlaylistSyncPacketS2C;
import com.nstut.simplyspeakers.network.StopAudioPacketS2C;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Runs the actual versioned manager, control service and persistent registry.
 * Only loader I/O, world/player objects and physics coordinate lookup are substituted.
 * No Minecraft JVM boot or second implementation of playback orchestration is used. */
class ServerPlaybackIntegrationTest {
    private static final String URL = "https://example.invalid/one.wav";
    private static final String KEY = "minecraft:overworld/net_test";
    private final SpeakerLocation location = new SpeakerLocation("minecraft:overworld", 0, 64, 0);
    private final List<ServerPlayer> players = new ArrayList<>();
    private final List<Object> packets = new ArrayList<>();
    private final List<SpeakerEvents.Type> events = new ArrayList<>();
    private final SpeakerEvents.Listener listener = (type, key, name, audio) -> events.add(type);
    private MinecraftServer server;
    private ServerLevel level;
    private MockedStatic<ServerPlaybackEnvironment> environment;
    private long tick;
    private Vec3 emitterPosition;

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach void setup() {
        server = mock(MinecraftServer.class);
        level = mock(ServerLevel.class);
        PlayerList playerList = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(playerList);
        when(server.getAllLevels()).thenReturn(List.of(level));
        when(server.getTickCount()).thenReturn(4);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.getServer()).thenReturn(server);
        when(level.getGameTime()).thenAnswer(inv -> tick);
        when(level.players()).thenReturn(players);
        when(playerList.getPlayers()).thenReturn(players);
        when(playerList.getPlayer(any(UUID.class))).thenAnswer(inv -> players.stream()
                .filter(p -> p.getUUID().equals(inv.getArgument(0))).findFirst().orElse(null));
        emitterPosition = new Vec3(0.5, 64.5, 0.5);
        environment = mockStatic(ServerPlaybackEnvironment.class, inv -> {
            return switch (inv.getMethod().getName()) {
                case "audioFiles" -> null;
                case "emitterPosition" -> {
                    var pos = (net.minecraft.core.BlockPos) inv.getArgument(1);
                    yield emitterPosition == null ? null : emitterPosition.add(pos.getX(), pos.getY() - 64, pos.getZ());
                }
                case "listenerPosition" -> ((ServerPlayer) inv.getArgument(1)).position();
                default -> { packets.add(inv.getArgument(1)); yield null; }
            };
        });
        ServerPlaybackManager.resetForWorld();
        ServerSpeakerRegistry.resetForWorld();
        SpeakerEvents.register(listener);
        SpeakerState state = new SpeakerState(URL, "one.wav", false, false, -1);
        state.setMaxRange(16);
        ServerSpeakerRegistry.updateSpeakerStateByFullKey(KEY, state);
        ServerSpeakerRegistry.upsertEmitter(new ServerEmitter(location, "net_test", 2, 0.2f, 0.3f, false, true));
    }

    @AfterEach void cleanup() {
        SpeakerEvents.unregister(listener);
        ServerPlaybackManager.resetForWorld();
        ServerSpeakerRegistry.resetForWorld();
        environment.close();
    }

    private SpeakerState state() { return ServerSpeakerRegistry.getSpeakerStateByFullKey(KEY); }
    private ServerPlayer player(double distance) {
        ServerPlayer p = mock(ServerPlayer.class);
        when(p.getUUID()).thenReturn(UUID.randomUUID());
        when(p.level()).thenReturn(level);
        try {
            // 26.1.2 obtains the server from level(); older versions expose getServer().
            when((MinecraftServer) ServerPlayer.class.getMethod("getServer").invoke(p)).thenReturn(server);
        } catch (NoSuchMethodException ignored) {
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        move(p, distance);
        players.add(p);
        return p;
    }
    private void move(ServerPlayer p, double distance) {
        when(p.position()).thenReturn(new Vec3(0.5 + distance, 64.5, 0.5));
    }
    private void scan() { ServerPlaybackManager.serverTick(server); }
    private void play() { assertTrue(ServerSpeakerControlService.play(server, level, KEY)); }
    private long packets(Class<?> type) { return packets.stream().filter(type::isInstance).count(); }
    private void report(ServerPlayer p) {
        ServerPlaybackManager.handleRemoteStreamEofReport(p, KEY, state().getPlaybackSessionGeneration(), URL);
    }

    @Test void transferringOwnershipPersistsNewOwnerAndLeavesPlaybackIntact() {
        var first=UUID.randomUUID();var next=UUID.randomUUID();state().setOwnerUuid(first);
        state().setAccessMode(com.nstut.simplyspeakers.SpeakerAccess.TRUSTED);state().trustPlayer(first);
        play();long start=state().getPlaybackStartTick();
        assertTrue(ServerSpeakerControlService.policyControl(server,level,KEY,
            com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.OP_TRANSFER_OWNER,"",0,0,next));
        assertEquals(next,state().getOwnerUuid());assertTrue(state().isPlaying());
        assertEquals(start,state().getPlaybackStartTick());
        assertFalse(com.nstut.simplyspeakers.SpeakerPermissions.canManage(state(),first,false));
        assertTrue(com.nstut.simplyspeakers.SpeakerPermissions.canManage(state(),next,false));
    }

    @Test void repeatAndQueuedRequestsResynchronizeDecoderLooping() {
        player(2);play();scan();
        assertTrue(ServerSpeakerControlService.playlistControl(server,level,KEY,PlaylistControlPacketC2S.OP_SET_REPEAT,1,false,"",""));
        var plays=packets.stream().filter(PlayAudioPacketS2C.class::isInstance).map(PlayAudioPacketS2C.class::cast).toList();
        assertTrue(plays.get(plays.size()-1).isLooping());
        assertTrue(ServerSpeakerControlService.playlistControl(server,level,KEY,PlaylistControlPacketC2S.OP_QUEUE_LAST,0,false,"https://example.invalid/request.wav","request.wav"));
        plays=packets.stream().filter(PlayAudioPacketS2C.class::isInstance).map(PlayAudioPacketS2C.class::cast).toList();
        assertFalse(plays.get(plays.size()-1).isLooping(),"Request must not be starved by decoder loop");
    }

    @Test void listenerEntryExitAndReentryDispatchExactlyOnceWithLiveSettings() {
        ServerPlayer p = player(10);
        state().setMaxVolume(0.75f);
        play(); scan(); scan();
        assertEquals(1, packets(PlayAudioPacketS2C.class));
        PlayAudioPacketS2C packet = (PlayAudioPacketS2C) packets.stream().filter(PlayAudioPacketS2C.class::isInstance).findFirst().orElseThrow();
        assertEquals(16, packet.getMaxRange());
        assertEquals(0.75f, packet.getMaxVolume());
        assertEquals(KEY, packet.getFullStateKey());
        assertEquals(Set.of(p.getUUID()), ServerPlaybackManager.getSubscribers(location));
        move(p, 17); scan(); // Hysteresis retains an existing listener.
        assertEquals(0, packets(StopAudioPacketS2C.class));
        move(p, 30); scan(); scan();
        assertEquals(1, packets(StopAudioPacketS2C.class));
        assertTrue(ServerPlaybackManager.getEmitterLocationsForPlayer(p.getUUID()).isEmpty());
        move(p, 10); scan();
        assertEquals(2, packets(PlayAudioPacketS2C.class));
    }

    @Test void pauseResumeSeekRestartAndStopUseActualControlService() {
        player(1); play(); int generation = state().getPlaybackSessionGeneration();
        tick = 100;
        assertTrue(ServerSpeakerControlService.pause(server, level, KEY));
        assertEquals(5, state().getPauseOffsetSeconds(), 0.001);
        assertTrue(ServerPlaybackManager.getSubscribers(location).isEmpty());
        tick = 200;
        assertTrue(ServerSpeakerControlService.play(server, level, KEY));
        assertEquals(generation, state().getPlaybackSessionGeneration());
        assertEquals(5, state().getPlaybackPositionSeconds(tick), 0.001);
        assertTrue(ServerSpeakerControlService.seek(server, level, KEY, 12));
        assertEquals(12, state().getPlaybackPositionSeconds(tick), 0.001);
        assertTrue(ServerSpeakerControlService.restart(server, level, KEY));
        assertTrue(state().getPlaybackSessionGeneration() > generation);
        assertEquals(0, state().getPlaybackPositionSeconds(tick), 0.001);
        assertTrue(ServerSpeakerControlService.stop(server, level, KEY));
        assertFalse(state().isPlaying());
        assertTrue(ServerPlaybackManager.getSubscribers(location).isEmpty());
        assertTrue(events.containsAll(List.of(SpeakerEvents.Type.STARTED, SpeakerEvents.Type.PAUSED,
                SpeakerEvents.Type.RESUMED, SpeakerEvents.Type.STOPPED)));
    }

    @ParameterizedTest @ValueSource(strings = {"seek", "restart", "track"})
    void linkedResyncStopsEveryMembershipBeforeStartingTheReplacement(String operation) {
        player(5);
        ServerSpeakerRegistry.upsertEmitter(new ServerEmitter(
                new SpeakerLocation("minecraft:overworld", 2, 64, 0), "net_test", 16, 1, 1, false, true));
        play(); scan();
        assertEquals(2, packets(PlayAudioPacketS2C.class));
        packets.clear();
        switch (operation) {
            case "seek" -> assertTrue(ServerSpeakerControlService.seek(server, level, KEY, 4));
            case "restart" -> assertTrue(ServerSpeakerControlService.restart(server, level, KEY));
            case "track" -> assertTrue(ServerSpeakerControlService.selectAudio(server, level, KEY, "https://example.invalid/two.wav", "two.wav"));
        }
        List<Class<?>> transport = packets.stream().filter(p -> p instanceof StopAudioPacketS2C || p instanceof PlayAudioPacketS2C)
                .map(Object::getClass).toList();
        assertEquals(List.of(StopAudioPacketS2C.class, StopAudioPacketS2C.class,
                PlayAudioPacketS2C.class, PlayAudioPacketS2C.class), transport);
    }

    @Test void resyncCannotAdvanceUsingOldEofAgainstAnAudienceBeingRebuilt() {
        ServerPlayer first = player(-10);
        ServerPlayer second = player(30);
        ServerSpeakerRegistry.upsertEmitter(new ServerEmitter(
                new SpeakerLocation("minecraft:overworld", 20, 64, 0), "net_test", 16, 1, 1, false, true));
        play(); scan();
        report(first);
        assertTrue(ServerSpeakerControlService.seek(server, level, KEY, 4));
        assertTrue(state().isPlaying());
        assertEquals(URL, state().getAudioId());
        report(second);
        assertTrue(state().isPlaying(), "resync must invalidate previous stream EOF evidence");
        report(first);
        assertFalse(state().isPlaying());
    }

    @ParameterizedTest @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    void nonFiniteSeeksCannotMutateOrDispatch(float seconds) {
        play(); float position = state().getPlaybackPositionSeconds(tick); int count = packets.size();
        assertFalse(ServerSpeakerControlService.seek(server, level, KEY, seconds));
        assertEquals(position, state().getPlaybackPositionSeconds(tick));
        assertEquals(count, packets.size());
    }

    @Test void stoppedSeekAndUnknownTransportCannotStartPlayback() {
        assertFalse(ServerSpeakerControlService.seek(server, level, KEY, 4));
        assertFalse(ServerSpeakerControlService.applyTransport(server, level, KEY, (byte) 127, 0));
        assertFalse(ServerSpeakerControlService.play(server, level, "missing"));
        assertTrue(packets.isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"quit", "dimension", "range", "emitter"})
    void audienceChurnReevaluatesPendingEofAgainstRemainingListeners(String churn) {
        ServerPlayer a = player(1), b = player(2);
        if (churn.equals("emitter")) {
            move(b, 101);
            ServerSpeakerRegistry.upsertEmitter(new ServerEmitter(
                    new SpeakerLocation("minecraft:overworld", 100, 64, 0), "net_test", 16, 1, 1, true, true));
            // Give each emitter its real position rather than the common test origin.
            environment.when(() -> ServerPlaybackEnvironment.emitterPosition(any(), any())).thenAnswer(inv ->
                    Vec3.atCenterOf(inv.getArgument(1)));
        }
        play(); report(a); assertTrue(state().isPlaying());
        switch (churn) {
            case "quit" -> ServerPlaybackManager.handlePlayerQuit(server, b.getUUID());
            case "dimension" -> ServerPlaybackManager.handlePlayerDimensionChange(server, b.getUUID());
            case "range" -> { move(b, 50); scan(); }
            case "emitter" -> ServerPlaybackManager.unregisterEmitter(server,
                    new SpeakerLocation("minecraft:overworld", 100, 64, 0));
        }
        assertFalse(state().isPlaying());
        assertEquals(1, Collections.frequency(events, SpeakerEvents.Type.FINISHED));
        report(a); // Repeated reports must not finish a second time.
        assertEquals(1, Collections.frequency(events, SpeakerEvents.Type.FINISHED));
    }

    @Test void eofRejectsOutsidersOldGenerationsWrongTracksPauseAndLoop() {
        ServerPlayer a = player(1), b = player(2), outsider = player(100);
        play(); int generation = state().getPlaybackSessionGeneration();
        ServerPlaybackManager.handleRemoteStreamEofReport(outsider, KEY, generation, URL);
        ServerPlaybackManager.handleRemoteStreamEofReport(a, KEY, generation - 1, URL);
        ServerPlaybackManager.handleRemoteStreamEofReport(a, KEY, generation, URL + "?wrong");
        report(b); assertTrue(state().isPlaying());
        assertTrue(ServerSpeakerControlService.restart(server, level, KEY));
        ServerPlaybackManager.handleRemoteStreamEofReport(a, KEY, generation, URL);
        report(b); assertTrue(state().isPlaying());
        state().setPaused(true); report(a); assertTrue(state().isPlaying());
        state().setPaused(false); state().setLooping(true); report(a); assertTrue(state().isPlaying());
        state().setLooping(false); report(a); assertFalse(state().isPlaying());
    }

    @Test void eofAdvancesPlaylistAndDoesNotReuseOldOccurrence() {
        ServerPlayer p = player(1);
        state().getPlaylist().add(URL, "one.wav");
        state().getPlaylist().add("https://example.invalid/two.wav", "two.wav");
        state().getPlaylist().selectIndex(0);
        play(); int generation = state().getPlaybackSessionGeneration();
        report(p);
        assertEquals("https://example.invalid/two.wav", state().getAudioId());
        assertTrue(state().isPlaying());
        assertTrue(state().getPlaybackSessionGeneration() > generation);
        ServerPlaybackManager.handleRemoteStreamEofReport(p, KEY, generation, URL);
        assertEquals(1, state().getPlaylist().getCurrentIndex());
        assertEquals(1, Collections.frequency(events, SpeakerEvents.Type.TRACK_CHANGED));
    }

    @Test void clearingPlaylistBroadcastsAnEmptyAuthoritativeSnapshot() {
        state().getPlaylist().add(URL, "one.wav");
        assertTrue(ServerSpeakerControlService.playlistControl(server, level, KEY,
                PlaylistControlPacketC2S.OP_CLEAR, 0, false, "", ""));
        assertEquals(1, packets(PlaylistSyncPacketS2C.class));
        PlaylistSyncPacketS2C packet = (PlaylistSyncPacketS2C) packets.get(0);
        assertTrue(packet.getAudioIds().isEmpty());
        assertEquals(KEY, packet.getFullStateKey());
    }

    @Test void temporaryLibraryRequestsResumePlaylistAndPlayPlaylistClearsThem() {
        state().getPlaylist().add(URL, "one.wav");
        state().getPlaylist().add("b", "B.wav");
        assertTrue(ServerSpeakerControlService.playlistControl(server, level, KEY,
                PlaylistControlPacketC2S.OP_PLAY_PLAYLIST, 0, true, "", ""));
        ServerSpeakerControlService.playlistControl(server, level, KEY, PlaylistControlPacketC2S.OP_QUEUE_LAST, 0, false, "x", "X.wav");
        ServerSpeakerControlService.playlistControl(server, level, KEY, PlaylistControlPacketC2S.OP_QUEUE_NEXT, 0, false, "y", "Y.wav");
        assertEquals(List.of("y", "x"), state().getPlaylist().getQueue());
        assertTrue(ServerSpeakerControlService.next(server, level, KEY)); assertEquals("y", state().getAudioId());
        assertTrue(ServerSpeakerControlService.next(server, level, KEY)); assertEquals("x", state().getAudioId());
        assertTrue(ServerSpeakerControlService.next(server, level, KEY)); assertEquals("b", state().getAudioId());
        assertEquals(2, state().getPlaylist().size());
        ServerSpeakerControlService.playlistControl(server, level, KEY, PlaylistControlPacketC2S.OP_QUEUE_LAST, 0, false, "x", "X.wav");
        ServerSpeakerControlService.playlistControl(server, level, KEY, PlaylistControlPacketC2S.OP_PLAY_PLAYLIST, 0, true, "", "");
        assertTrue(state().getPlaylist().getQueue().isEmpty());
        assertEquals(URL, state().getAudioId());
        assertTrue(state().isPlaylistSourceActive());
    }

    @Test void playingSingleAudioPreservesSavedPlaylistAndOnlyPlaysTemporaryRequestsAfterIt() {
        state().getPlaylist().add(URL, "one.wav"); state().getPlaylist().add("b", "B.wav");
        ServerSpeakerControlService.playlistControl(server, level, KEY, PlaylistControlPacketC2S.OP_PLAY_AUDIO, 0, true, "x", "X.wav");
        assertEquals(2, state().getPlaylist().size());
        assertFalse(state().isPlaylistSourceActive());
        ServerSpeakerControlService.playlistControl(server, level, KEY, PlaylistControlPacketC2S.OP_QUEUE_NEXT, 0, false, "y", "Y.wav");
        ServerSpeakerControlService.next(server, level, KEY); assertEquals("y", state().getAudioId());
        ServerSpeakerControlService.next(server, level, KEY); assertFalse(state().isPlaying());
        assertEquals(2, state().getPlaylist().size());
    }

    @Test void playStartsPendingRequestsWhenNoTrackWasSelected() {
        state().setAudioId("");
        state().getPlaylist().queueLast("x");
        assertTrue(ServerSpeakerControlService.play(server, level, KEY));
        assertEquals("x", state().getAudioId());
        assertTrue(state().isPlaying());
        assertTrue(ServerSpeakerControlService.next(server, level, KEY));
        assertFalse(state().isPlaying());
    }

    @Test void invalidSourceSelectionsPreservePendingRequests() {
        state().getPlaylist().queueLast("x");
        state().setPlaylistSourceActive(false);
        assertFalse(ServerSpeakerControlService.playlistControl(server, level, KEY,
                PlaylistControlPacketC2S.OP_SELECT_INDEX, 8, true, "", ""));
        assertFalse(ServerSpeakerControlService.playlistControl(server, level, KEY,
                PlaylistControlPacketC2S.OP_PLAY_PLAYLIST, 0, true, "", ""));
        assertFalse(ServerSpeakerControlService.playlistControl(server, level, KEY,
                PlaylistControlPacketC2S.OP_PLAY_AUDIO, 0, true, "", ""));
        assertEquals(List.of("x"), state().getPlaylist().getQueue());
        assertFalse(state().isPlaylistSourceActive());
    }

    @Test void manualNextStopsAfterQueueWithoutASavedPlaylistAndRenewsRepeatedOccurrences() {
        ServerSpeakerControlService.playlistControl(server, level, KEY,
                PlaylistControlPacketC2S.OP_PLAY_AUDIO, 0, true, URL, "one.wav");
        int generation = state().getPlaybackSessionGeneration();
        ServerSpeakerControlService.playlistControl(server, level, KEY,
                PlaylistControlPacketC2S.OP_QUEUE_LAST, 0, false, URL, "one.wav");
        assertTrue(ServerSpeakerControlService.next(server, level, KEY));
        assertTrue(state().getPlaybackSessionGeneration() > generation);
        assertEquals(URL, state().getAudioId());
        assertTrue(ServerSpeakerControlService.previous(server, level, KEY));
        assertEquals(URL, state().getAudioId());
        assertTrue(ServerSpeakerControlService.next(server, level, KEY));
        assertFalse(state().isPlaying());
    }

    @Test void changedPhysicsPositionAndMissingBodyUpdateRealSubscriptions() {
        ServerPlayer p = player(1); play();
        emitterPosition = new Vec3(100, 64, 0); scan();
        assertTrue(ServerPlaybackManager.getSubscribers(location).isEmpty());
        emitterPosition = new Vec3(0.5, 64.5, 0.5); scan();
        assertEquals(Set.of(p.getUUID()), ServerPlaybackManager.getSubscribers(location));
        // The 1.21.1 adapter's nullable coordinate contract is specific to Sable.
        if (ServerPlaybackEnvironment.class.getResource("/com/nstut/simplyspeakers/compat/sable/SpeakerSpatialResolver.class") != null) {
            emitterPosition = null; scan();
            assertTrue(ServerPlaybackManager.getSubscribers(location).isEmpty());
        }
    }

    @Test void registryDiskReloadPreservesPlaylistPolicyAndOccurrenceButClearsAudience(@TempDir Path world) {
        ServerSpeakerRegistry.init(world);
        SpeakerState original = new SpeakerState(URL, "one.wav", false, false, -1);
        original.setNetworkName("Station"); original.setOwnerUuid(UUID.randomUUID());
        original.getPlaylist().add(URL, "one.wav"); original.getPlaylist().add("two", "two.wav");
        original.getPlaylist().queueNext("two"); original.startPlaybackAt(0, 3);
        ServerSpeakerRegistry.updateSpeakerStateByFullKey(KEY, original);
        ServerSpeakerRegistry.flushDirty();
        ServerPlaybackManager.resetForWorld(); ServerSpeakerRegistry.init(world);
        SpeakerState reloaded = state();
        assertNotNull(reloaded);
        assertEquals(original.getOwnerUuid(), reloaded.getOwnerUuid());
        assertEquals("Station", reloaded.getNetworkName());
        assertEquals(original.getPlaybackSessionGeneration(), reloaded.getPlaybackSessionGeneration());
        assertEquals(2, reloaded.getPlaylist().size());
        assertEquals(List.of("two"), reloaded.getPlaylist().getQueue());
        assertTrue(ServerSpeakerRegistry.getEmitters().isEmpty());
        assertTrue(ServerPlaybackManager.getSubscribers(location).isEmpty());
    }

    @Test void announcementStopsOnceDespiteLoopPlaylistAndQueueAndRevalidatesOwnership(@TempDir Path world) throws Exception {
        var files = new com.nstut.simplyspeakers.audio.AudioFileManager(world);
        UUID owner = UUID.randomUUID();
        try {
            var metadata = files.saveFile(new java.io.ByteArrayInputStream(
                    com.nstut.simplyspeakers.testing.WaveFixture.tone(2)), "announcement.wav", owner.toString());
            environment.when(ServerPlaybackEnvironment::audioFiles).thenReturn(files);
            state().setLooping(true);
            state().getPlaylist().add(URL, "music.wav");
            state().getPlaylist().queueNext(URL);
            assertFalse(ServerSpeakerControlService.playAnnouncement(server, level, KEY, metadata.getUuid(), UUID.randomUUID(), false));
            player(1);
            assertTrue(ServerSpeakerControlService.playAnnouncement(server, level, KEY, metadata.getUuid(), owner, false));
            assertTrue(state().isOneShotPlayback());
            var started = (PlayAudioPacketS2C) packets.stream().filter(PlayAudioPacketS2C.class::isInstance).findFirst().orElseThrow();
            assertFalse(started.isLooping());
            int generation = state().getPlaybackSessionGeneration();
            tick = 10;
            assertTrue(ServerSpeakerControlService.playAnnouncement(server, level, KEY, metadata.getUuid(), owner, false));
            assertEquals(generation, state().getPlaybackSessionGeneration());
            assertTrue(ServerSpeakerControlService.playAnnouncement(server, level, KEY, metadata.getUuid(), owner, true));
            assertTrue(state().getPlaybackSessionGeneration() > generation);
            tick = 50; scan(); scan();
            assertFalse(state().isPlaying());
            assertTrue(state().isLooping());
            assertEquals(List.of(URL), state().getPlaylist().getQueue());
            assertEquals(1, Collections.frequency(events, SpeakerEvents.Type.FINISHED));
        } finally { files.shutdown(); }
    }

    @Test void continuousSettingsUpdatesPreserveSubscriptionsTransportAndSession() {
        player(1); play();
        int generation = state().getPlaybackSessionGeneration();
        long start = state().getPlaybackStartTick();
        packets.clear();
        for (int i = 0; i < 20; i++) {
            assertTrue(ServerSpeakerControlService.setVolume(server, level, KEY, i / 20f));
            assertTrue(ServerSpeakerControlService.setControllerVolume(server, level, KEY, i / 20f));
            assertTrue(ServerSpeakerControlService.policyControl(server, level, KEY,
                    com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.OP_DIRECTIONALITY, "", 0, i / 20f, null));
            assertTrue(ServerSpeakerControlService.setRange(server, level, KEY, 20 + i));
            assertEquals(generation, state().getPlaybackSessionGeneration());
            assertEquals(start, state().getPlaybackStartTick());
            assertTrue(state().isPlaying());
            assertEquals(1, ServerPlaybackManager.getSubscribers(location).size());
        }
        assertTrue(packets.stream().noneMatch(StopAudioPacketS2C.class::isInstance));
        var updated = packets.stream().filter(PlayAudioPacketS2C.class::isInstance)
                .map(PlayAudioPacketS2C.class::cast).reduce((a,b) -> b).orElseThrow();
        assertEquals(.95f, updated.getMaxVolume(), .001);
        assertEquals(.95f, updated.getExtras().directionality(), .001);
    }

    @Test void realFileDurationEndsPlaybackAtTickZeroAndLoopOffsetsWrap(@TempDir Path world) throws Exception {
        var files = new com.nstut.simplyspeakers.audio.AudioFileManager(world);
        try {
            var metadata = files.saveFile(new java.io.ByteArrayInputStream(
                    com.nstut.simplyspeakers.testing.WaveFixture.tone(2)), "fixture.wav", UUID.randomUUID().toString());
            assertEquals(2, metadata.getDurationSeconds(), 0.01);
            assertTrue(com.nstut.simplyspeakers.audio.AudioFileManager.validateAudioContent(
                    files.getAudioFilePath(metadata.getUuid()), "fixture.wav"));
            environment.when(ServerPlaybackEnvironment::audioFiles).thenReturn(files);
            state().setAudioId(metadata.getUuid()); state().setAudioFilename("fixture.wav");
            player(1); play(); tick = 40; scan();
            assertFalse(state().isPlaying());
            assertEquals(1, Collections.frequency(events, SpeakerEvents.Type.FINISHED));
            state().setLooping(true); tick = 0; play(); tick = 145;
            packets.clear(); ServerPlaybackManager.resyncState(server, level, KEY);
            PlayAudioPacketS2C packet = (PlayAudioPacketS2C) packets.stream()
                    .filter(PlayAudioPacketS2C.class::isInstance).findFirst().orElseThrow();
            assertEquals(1.25f, packet.getPlaybackPositionSeconds(), 0.01);
            assertTrue(state().isPlaying());
        } finally { files.shutdown(); }
    }

    @Test void editingAnInactivePlaylistDoesNotChangeThePlayingTrackOffsetOrQueue() {
        state().getPlaylist().add(URL,"One");state().getPlaylist().selectIndex(0);state().getPlaylist().queueLast("request");state().startPlaybackAt(tick,12);
        assertTrue(ServerSpeakerControlService.playlistControl(server,level,KEY,PlaylistControlPacketC2S.OP_CREATE_PLAYLIST,-1,false,"","Evening"));
        String id=state().getSavedPlaylists().get(1).getId();
        assertTrue(ServerSpeakerControlService.playlistControl(server,level,KEY,PlaylistControlPacketC2S.OP_ADD,-1,false,"other","Other",id));
        assertTrue(ServerSpeakerControlService.playlistControl(server,level,KEY,PlaylistControlPacketC2S.OP_RENAME_PLAYLIST,-1,false,"","Travel",id));
        assertEquals("default",state().getActivePlaylistId());assertEquals(URL,state().getAudioId());assertEquals(12,state().getPlaybackPositionSeconds(tick));assertEquals(List.of("request"),state().getPlaylist().getQueue());assertTrue(state().isPlaying());
    }
    @Test void explicitPlaylistPlaySwitchesSourceAndClearsTemporaryRequests() {
        state().getPlaylist().add(URL,"One");state().getPlaylist().queueNext("request");String id=state().createSavedPlaylist("Evening");state().findSavedPlaylist(id).getPlaylist().add("new-track","New");
        assertTrue(ServerSpeakerControlService.playlistControl(server,level,KEY,PlaylistControlPacketC2S.OP_PLAY_PLAYLIST,-1,true,"","",id));
        assertEquals(id,state().getActivePlaylistId());assertEquals("new-track",state().getAudioId());assertTrue(state().isPlaying());assertTrue(state().findSavedPlaylist("default").getPlaylist().getQueue().isEmpty());assertEquals(1,state().findSavedPlaylist("default").getPlaylist().size());
    }
    @Test void deletingThePlayingPlaylistStopsAndFallsBackWithoutDeletingOtherTracks() {
        state().getPlaylist().add(URL,"One");String id=state().createSavedPlaylist("Evening");state().findSavedPlaylist(id).getPlaylist().add(URL,"One");
        ServerSpeakerControlService.playlistControl(server,level,KEY,PlaylistControlPacketC2S.OP_PLAY_PLAYLIST,-1,true,"","",id);
        assertTrue(ServerSpeakerControlService.playlistControl(server,level,KEY,PlaylistControlPacketC2S.OP_DELETE_PLAYLIST,-1,false,"","",id));
        assertFalse(state().isPlaying());assertEquals("default",state().getActivePlaylistId());assertEquals(1,state().getPlaylist().size());assertTrue(ServerSpeakerControlService.playlistControl(server,level,KEY,PlaylistControlPacketC2S.OP_DELETE_PLAYLIST,-1,false,"","","default"));assertTrue(state().getSavedPlaylists().isEmpty());
    }
    @Test void unknownTargetsAndOverfullCatalogsCannotMutateTheActivePlaylist() {
        assertFalse(ServerSpeakerControlService.playlistControl(server,level,KEY,PlaylistControlPacketC2S.OP_ADD,-1,false,"x","X","deleted-id"));
        for(int n=0;n<256;n++)state().getPlaylist().add("a"+n,"A");String id=state().createSavedPlaylist("Full");for(int n=0;n<256;n++)state().findSavedPlaylist(id).getPlaylist().add("b"+n,"B");
        String empty=state().createSavedPlaylist("Empty");assertFalse(ServerSpeakerControlService.playlistControl(server,level,KEY,PlaylistControlPacketC2S.OP_ADD,-1,false,"x","X",empty));assertNull(state().duplicateSavedPlaylist(id,"Copy"));assertEquals(512,state().savedTrackCount());
    }
    @Test void namedPlaylistsAndTheirModesPersistThroughAnActualRegistryReload(@TempDir Path world) {
        ServerSpeakerRegistry.init(world);var original=new SpeakerState(URL,"One",false,false,-1);original.getPlaylist().add(URL,"One");String id=original.createSavedPlaylist("Evening");var saved=original.findSavedPlaylist(id).getPlaylist();saved.add("two","Two");saved.setShuffle(true,42);saved.setRepeatMode(com.nstut.simplyspeakers.playlist.RepeatMode.PLAYLIST);original.activateSavedPlaylist(id);
        ServerSpeakerRegistry.updateSpeakerStateByFullKey(KEY,original);ServerSpeakerRegistry.flushDirty();ServerSpeakerRegistry.init(world);
        assertEquals(2,state().getSavedPlaylists().size());assertEquals(id,state().getActivePlaylistId());assertEquals("Evening",state().findSavedPlaylist(id).getName());assertEquals(List.of("two"),state().getPlaylist().getTracks().stream().map(t -> t.getAudioId()).toList());assertEquals(42,state().getPlaylist().getShuffleSeed());assertEquals(com.nstut.simplyspeakers.playlist.RepeatMode.PLAYLIST,state().getPlaylist().getRepeatMode());assertEquals(1,state().findSavedPlaylist("default").getPlaylist().size());
    }

    @Test void oldSinglePlaylistRegistryMigratesWithoutLosingModesCursorOrRequests(@TempDir Path world)throws Exception {
        var original=new SpeakerState(URL,"One",false,false,-1);original.getPlaylist().add(URL,"One");original.getPlaylist().add("two","Two");original.getPlaylist().selectIndex(1);original.getPlaylist().setShuffle(true,99);original.getPlaylist().setRepeatMode(com.nstut.simplyspeakers.playlist.RepeatMode.TRACK);original.getPlaylist().queueLast("request");
        var gson=new com.google.gson.Gson();var data=gson.toJsonTree(Map.of(KEY,original)).getAsJsonObject();data.getAsJsonObject(KEY).remove("savedPlaylists");data.getAsJsonObject(KEY).remove("activePlaylistId");
        java.nio.file.Files.writeString(world.resolve("speaker_registry.json"),gson.toJson(data));ServerSpeakerRegistry.init(world);
        assertEquals("Default",state().getSavedPlaylists().get(0).getName());assertEquals(2,state().getPlaylist().size());assertEquals(1,state().getPlaylist().getCurrentIndex());assertEquals(List.of("request"),state().getPlaylist().getQueue());assertEquals(99,state().getPlaylist().getShuffleSeed());assertEquals(com.nstut.simplyspeakers.playlist.RepeatMode.TRACK,state().getPlaylist().getRepeatMode());
    }

    @Test void personalListsFollowPlayerWhileSpeakerQueuesRemainIndependent() {
        var owner=player(2);var other=player(3);
        var store=com.nstut.simplyspeakers.playlist.PlayerPlaylistStore.library(owner.getUUID());
        assertTrue(PlayerPlaylistControlService.control(owner,KEY,PlaylistControlPacketC2S.OP_CREATE_PLAYLIST,-1,false,"","Clips",""));
        String id=store.getSavedPlaylists().get(0).getId();
        assertTrue(PlayerPlaylistControlService.control(owner,KEY,PlaylistControlPacketC2S.OP_ADD,-1,false,URL,"One",id));
        assertTrue(com.nstut.simplyspeakers.playlist.PlayerPlaylistStore.library(other.getUUID()).getSavedPlaylists().isEmpty());
        assertFalse(PlayerPlaylistControlService.control(other,KEY,PlaylistControlPacketC2S.OP_DELETE_PLAYLIST,-1,false,"","",id));
        String second="minecraft:overworld/second";ServerSpeakerRegistry.updateSpeakerStateByFullKey(second,new SpeakerState());
        state().getPlaylist().queueLast("first-request");ServerSpeakerRegistry.getSpeakerStateByFullKey(second).getPlaylist().queueLast("second-request");
        try(var security=mockStatic(com.nstut.simplyspeakers.network.SpeakerPacketSecurity.class)) {
            security.when(()->com.nstut.simplyspeakers.network.SpeakerPacketSecurity.resolveAuthorizedTrack(owner,URL))
                .thenReturn(new com.nstut.simplyspeakers.network.SpeakerPacketSecurity.AuthorizedTrack(URL,"One"));
            assertTrue(PlayerPlaylistControlService.control(owner,KEY,PlaylistControlPacketC2S.OP_PLAY_PLAYLIST,-1,true,"","",id));
            assertTrue(PlayerPlaylistControlService.control(owner,second,PlaylistControlPacketC2S.OP_PLAY_PLAYLIST,-1,true,"","",id));
        }
        assertEquals(List.of("first-request"),state().getPlaylist().getQueue());
        assertEquals(List.of("second-request"),ServerSpeakerRegistry.getSpeakerStateByFullKey(second).getPlaylist().getQueue());
        assertTrue(store.findSavedPlaylist(id).getPlaylist().getQueue().isEmpty());
        assertTrue(PlayerPlaylistControlService.control(owner,KEY,PlaylistControlPacketC2S.OP_DELETE_PLAYLIST,-1,false,"","",id));
        assertTrue(store.getSavedPlaylists().isEmpty());assertTrue(state().getPlaylist().isEmpty());assertFalse(state().isPlaying());
        assertEquals(List.of("first-request"),state().getPlaylist().getQueue());
        assertFalse(ServerSpeakerRegistry.getSpeakerStateByFullKey(second).isPlaying());
        assertEquals(List.of("second-request"),ServerSpeakerRegistry.getSpeakerStateByFullKey(second).getPlaylist().getQueue());
        assertTrue(PlayerPlaylistControlService.control(owner,KEY,PlaylistControlPacketC2S.OP_CLEAR_QUEUE,-1,false,"","",""));
        assertTrue(state().getPlaylist().getQueue().isEmpty());
    }
    @Test void personalPlaylistAndSpeakerSourcePersistIndependently(@TempDir Path world) {
        ServerSpeakerRegistry.init(world);var owner=player(2);var state=new SpeakerState();ServerSpeakerRegistry.updateSpeakerStateByFullKey(KEY,state);
        var store=com.nstut.simplyspeakers.playlist.PlayerPlaylistStore.library(owner.getUUID());String id=store.createSavedPlaylist("Persist");store.findSavedPlaylist(id).getPlaylist().add(URL,"One");
        state().usePlayerPlaylist(owner.getUUID(),id,store.findSavedPlaylist(id).getPlaylist());state().getPlaylist().queueLast("speaker-request");
        com.nstut.simplyspeakers.playlist.PlayerPlaylistStore.changed();ServerSpeakerRegistry.markDirty();ServerSpeakerRegistry.flushDirty();ServerSpeakerRegistry.init(world);
        assertEquals(owner.getUUID(),state().getPlaylistOwnerUuid());assertEquals(id,state().getActivePlaylistId());
        assertEquals(List.of("speaker-request"),state().getPlaylist().getQueue());
        assertTrue(com.nstut.simplyspeakers.playlist.PlayerPlaylistStore.library(owner.getUUID()).findSavedPlaylist(id).getPlaylist().getQueue().isEmpty());
    }
    @ParameterizedTest @ValueSource(ints={1,2,3})
    void actorlessPublicJavaApiCannotBypassProtectedPolicies(int mode) {
        var pos=new net.minecraft.core.BlockPos(0,64,0);
        ServerSpeakerRegistry.registerSpeaker(level,pos,"net_test");
        state().setOwnerUuid(UUID.randomUUID());state().setAccessMode(com.nstut.simplyspeakers.SpeakerAccess.fromIndex(mode));
        assertFalse(com.nstut.simplyspeakers.api.SpeakerApi.play(level,pos));
        assertFalse(com.nstut.simplyspeakers.api.SpeakerApi.playNetwork(level,KEY));
        assertFalse(com.nstut.simplyspeakers.api.SpeakerApi.setTrack(level,pos,"foreign","forged filename"));
        assertFalse(com.nstut.simplyspeakers.api.SpeakerApi.setVolume(level,pos,0.1f));
        assertFalse(com.nstut.simplyspeakers.api.SpeakerApi.setRange(level,pos,32));
        assertFalse(com.nstut.simplyspeakers.api.SpeakerApi.setAudioDropoff(level,pos,0.1f));
        assertFalse(com.nstut.simplyspeakers.api.SpeakerApi.setLooping(level,pos,true));
        assertFalse(com.nstut.simplyspeakers.api.SpeakerApi.playlistQueueNext(level,pos,"foreign"));
        assertFalse(com.nstut.simplyspeakers.api.SpeakerApi.playlistClear(level,pos));
        assertFalse(com.nstut.simplyspeakers.api.SpeakerApi.setAccessMode(level,pos,com.nstut.simplyspeakers.SpeakerAccess.PUBLIC));
        assertFalse(com.nstut.simplyspeakers.api.SpeakerApi.setNetworkName(level,pos,"renamed"));
        assertTrue(com.nstut.simplyspeakers.api.SpeakerApi.getSavedPlaylists(level,pos).isEmpty());
        assertTrue(com.nstut.simplyspeakers.api.SpeakerApi.getLibrary(level,pos).isEmpty());
        assertEquals(URL,state().getAudioId());assertEquals(16,state().getMaxRange());assertFalse(state().isPlaying());
        assertTrue(events.isEmpty());assertTrue(packets.isEmpty());
    }
    @Test void publicJavaApiStillSeparatesPlaybackFromOwnedManagementAndRejectsNonfiniteValues() {
        var pos=new net.minecraft.core.BlockPos(0,64,0);ServerSpeakerRegistry.registerSpeaker(level,pos,"net_test");
        state().setOwnerUuid(UUID.randomUUID());state().setAccessMode(com.nstut.simplyspeakers.SpeakerAccess.PUBLIC);
        assertTrue(com.nstut.simplyspeakers.api.SpeakerApi.setVolume(level,pos,0.4f));
        assertTrue(com.nstut.simplyspeakers.api.SpeakerApi.setRange(level,pos,Integer.MAX_VALUE));
        assertEquals(com.nstut.simplyspeakers.Config.speakerRange,state().getMaxRange());
        assertTrue(com.nstut.simplyspeakers.api.SpeakerApi.setAudioDropoff(level,pos,0.3f));
        assertFalse(com.nstut.simplyspeakers.api.SpeakerApi.setVolume(level,pos,Float.NaN));
        assertFalse(com.nstut.simplyspeakers.api.SpeakerApi.seek(level,pos,Float.POSITIVE_INFINITY));
        assertFalse(com.nstut.simplyspeakers.api.SpeakerApi.setNetworkName(level,pos,"forbidden"));
        assertFalse(com.nstut.simplyspeakers.api.SpeakerApi.setDirectionality(level,pos,0.7f));
        assertEquals(0.4f,state().getConfiguredMaxVolume(),0.001f);
        state().setOwnerUuid(null);
        assertTrue(com.nstut.simplyspeakers.api.SpeakerApi.setDirectionality(level,pos,0.7f));
        assertTrue(com.nstut.simplyspeakers.api.SpeakerApi.setConeAngle(level,pos,999));
        assertTrue(com.nstut.simplyspeakers.api.SpeakerApi.setRearAttenuation(level,pos,0.2f));
        assertEquals(350,state().getConeAngleDegrees());assertEquals(0.7f,state().getDirectionality(),0.001f);
    }

    @Test void legacyLoopingApiResynchronizesActivePlaybackThroughRepeat() {
        player(2);play();scan();packets.clear();
        assertTrue(ServerSpeakerControlService.setLooping(server,level,KEY,true));
        assertEquals(com.nstut.simplyspeakers.playlist.RepeatMode.TRACK,state().getPlaylist().getRepeatMode());
        var updates=packets.stream().filter(PlayAudioPacketS2C.class::isInstance).map(PlayAudioPacketS2C.class::cast).toList();
        assertFalse(updates.isEmpty());assertTrue(updates.get(updates.size()-1).isLooping());
        assertTrue(ServerSpeakerControlService.setLooping(server,level,KEY,false));
        updates=packets.stream().filter(PlayAudioPacketS2C.class::isInstance).map(PlayAudioPacketS2C.class::cast).toList();
        assertFalse(updates.get(updates.size()-1).isLooping());
    }

    @Test void serviceSendsAuthoritativeMuteAndNameWithoutStoppingPlayback() {
        player(1);play();packets.clear();
        assertTrue(ServerSpeakerControlService.setVolume(server,level,KEY,0));
        assertTrue(ServerSpeakerControlService.policyControl(server,level,KEY,
            com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.OP_NETWORK_NAME,"Hall",0,0,null));
        var updates=packets.stream().filter(com.nstut.simplyspeakers.network.SpeakerStateUpdatePacketS2C.class::isInstance)
            .map(com.nstut.simplyspeakers.network.SpeakerStateUpdatePacketS2C.class::cast).toList();
        assertFalse(updates.isEmpty());var latest=updates.get(updates.size()-1).getSettings();
        assertEquals("Hall",latest.name());assertEquals(0,latest.effectiveVolume());
        assertTrue(state().isPlaying());assertTrue(packets.stream().noneMatch(StopAudioPacketS2C.class::isInstance));
        assertTrue(packets.stream().filter(PlayAudioPacketS2C.class::isInstance).map(PlayAudioPacketS2C.class::cast)
            .anyMatch(p -> p.getMaxVolume()==0));
    }
}
