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
}
