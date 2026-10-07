package com.nstut.simplyspeakers.blocks.entities;

import com.nstut.simplyspeakers.*;
import com.nstut.simplyspeakers.control.ControllerAction;
import com.nstut.simplyspeakers.speakers.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Drives the real configured block entity through signal changes on all three runtimes. */
class RedstoneControllerIntegrationTest {
    private ServerLevel level;
    private ServerPlayer player;
    private SpeakerState state;
    private RedstoneControllerBlockEntity controller;
    private MockedStatic<?> environment;
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach void setup() throws Exception {
        ServerSpeakerRegistry.resetForWorld(); ServerPlaybackManager.resetForWorld();
        var server=mock(MinecraftServer.class); level=mock(ServerLevel.class); player=mock(ServerPlayer.class);
        var players=mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(players); when(server.getAllLevels()).thenReturn(List.of(level));
        when(players.getPlayers()).thenReturn(List.of()); when(level.players()).thenReturn(List.of());
        when(level.dimension()).thenReturn(Level.OVERWORLD); when(level.getServer()).thenReturn(server);
        when(level.hasChunkAt(any())).thenReturn(true); when(level.mayInteract(eq(player),any())).thenReturn(true);
        when(level.getBlockState(any())).thenReturn(Blocks.IRON_BLOCK.defaultBlockState());
        when(player.level()).thenReturn(level); when(player.getUUID()).thenReturn(UUID.randomUUID());
        state=new SpeakerState("https://example.invalid/music.wav","music.wav",false,false,-1);
        ServerSpeakerRegistry.updateSpeakerStateByFullKey("minecraft:overworld/net_test",state);
        state=ServerSpeakerRegistry.getSpeakerStateByFullKey("minecraft:overworld/net_test");
        controller=new RedstoneControllerBlockEntity(validType(),BlockPos.ZERO,Blocks.IRON_BLOCK.defaultBlockState());
        controller.setLevel(level);
        environment=mockStatic(Class.forName("com.nstut.simplyspeakers.speakers.ServerPlaybackEnvironment"));
    }
    @AfterEach void cleanup() { if (environment != null) environment.close(); ServerSpeakerRegistry.resetForWorld(); ServerPlaybackManager.resetForWorld(); }
    private BlockEntityType<?> validType() {
        var type=mock(BlockEntityType.class);
        when(type.isValid(any())).thenReturn(true);
        return type;
    }
    private void configure(ControllerAction action) {
        assertTrue(controller.configure(player,"test",false,BlockPos.ZERO,action,"",false,0.5f));
    }
    private void observe(int value) { controller.observeSignal(value); ControllerCoordinator.flush(level.getServer()); }
    private RedstoneControllerBlockEntity another(int x,ControllerAction mode) {
        var c=new RedstoneControllerBlockEntity(validType(),new BlockPos(x,0,0),Blocks.IRON_BLOCK.defaultBlockState());
        c.setLevel(level); assertTrue(c.configure(player,"test",false,BlockPos.ZERO,mode,"",false,1)); return c;
    }
    @Test void volumeInputsCombineByMaximumRegardlessOfObservationOrderAndRemovalRestoresBaseline() {
        state.setMaxVolume(0.7f); configure(ControllerAction.VOLUME);
        var other=another(1,ControllerAction.VOLUME);
        controller.observeSignal(15);other.observeSignal(12);ControllerCoordinator.flush(level.getServer());
        assertEquals(0.8f,state.getMaxVolume(),0.0001);
        other.observeSignal(3);controller.observeSignal(15);ControllerCoordinator.flush(level.getServer());
        assertEquals(0.5f,state.getMaxVolume(),0.0001);
        controller.setRemoved();ControllerCoordinator.flush(level.getServer());assertEquals(0.2f,state.getMaxVolume(),0.0001);
        other.setRemoved();ControllerCoordinator.flush(level.getServer());assertEquals(0.7f,state.getMaxVolume(),0.0001);
    }
    @Test void playbackInputsCombineWithOrAndRevokedInputsAreExcludedEvenWhenHeld() {
        configure(ControllerAction.ENABLED);var other=another(1,ControllerAction.ENABLED);
        controller.observeSignal(0);other.observeSignal(15);ControllerCoordinator.flush(level.getServer());assertTrue(state.isPlaying());assertFalse(state.isPaused());
        controller.observeSignal(15);other.observeSignal(0);ControllerCoordinator.flush(level.getServer());assertFalse(state.isPaused());
        controller.observeSignal(0);ControllerCoordinator.flush(level.getServer());assertTrue(state.isPaused());
        controller.observeSignal(15);ControllerCoordinator.flush(level.getServer());assertFalse(state.isPaused());
        state.setOwnerUuid(UUID.randomUUID());state.setAccessMode(SpeakerAccess.OWNER_ONLY);
        ControllerCoordinator.flush(level.getServer());assertEquals("denied",controller.getStatus());
    }
    @Test void simultaneousDuplicateTogglePulsesDoNotCancelAndStopWinsInEitherOrder() {
        configure(ControllerAction.TOGGLE);var other=another(1,ControllerAction.TOGGLE);
        controller.observeSignal(15);other.observeSignal(15);ControllerCoordinator.flush(level.getServer());assertTrue(state.isPlaying());assertFalse(state.isPaused());
        controller.observeSignal(0);other.observeSignal(0);var stop=another(2,ControllerAction.STOP);
        stop.observeSignal(15);controller.observeSignal(15);other.observeSignal(15);ControllerCoordinator.flush(level.getServer());assertFalse(state.isPlaying());
        stop.observeSignal(0);controller.observeSignal(0);controller.observeSignal(15);stop.observeSignal(15);ControllerCoordinator.flush(level.getServer());assertFalse(state.isPlaying());
    }
    @Test void reconfigurationUnloadingAndWorldResetDiscardQueuedPulses() {
        configure(ControllerAction.TOGGLE);controller.observeSignal(15);configure(ControllerAction.NEXT);ControllerCoordinator.flush(level.getServer());assertFalse(state.isPlaying());
        configure(ControllerAction.TOGGLE);controller.observeSignal(15);controller.setRemoved();ControllerCoordinator.flush(level.getServer());assertFalse(state.isPlaying());
        var other=another(1,ControllerAction.TOGGLE);other.observeSignal(15);ControllerCoordinator.reset();ControllerCoordinator.flush(level.getServer());assertFalse(state.isPlaying());
    }
    @Test void freshSpeakerCanBeLinkedAndControlledBeforeItsFirstBlockTick() {
        var speaker=new SpeakerBlockEntity(validType(),new BlockPos(4,0,0),Blocks.IRON_BLOCK.defaultBlockState());
        speaker.setLevel(level);
        assertNull(ServerSpeakerRegistry.getSpeakerStateByFullKey(speaker.getFullStateKey()));
        speaker.setSpeakerId("fresh");
        assertNotNull(ServerSpeakerRegistry.getSpeakerStateByFullKey(speaker.getFullStateKey()));
        assertEquals(speaker.getFullStateKey(),ServerSpeakerRegistry.getFullStateKeyAt(level,speaker.getBlockPos()));
        assertTrue(controller.configure(player,"fresh",false,BlockPos.ZERO,ControllerAction.VOLUME,"",false,0.5f));
        observe(15);
        assertEquals(0.5f,ServerSpeakerRegistry.getSpeakerStateByFullKey(speaker.getFullStateKey()).getMaxVolume());
    }
    @Test void nativeSpeakerInputIsInertInEveryLegacyModeAndUnpoweredPlaybackEmits() {
        var speaker=new SpeakerBlockEntity(validType(),new BlockPos(4,0,0),Blocks.IRON_BLOCK.defaultBlockState());speaker.setLevel(level);speaker.setSpeakerId("test");
        state=ServerSpeakerRegistry.getSpeakerState(level,"net_test");
        for(var mode:RedstoneMode.values()) {
            state.setRedstoneMode(mode);state.setPlaying(true);state.setPaused(false);state.setMaxVolume(0.7f);
            speaker.handleRedstoneChange(0);speaker.handleRedstoneChange(15);speaker.handleRedstoneChange(3);speaker.handleRedstoneChange(0);
            assertTrue(state.isPlaying());assertFalse(state.isPaused());assertEquals(0.7f,state.getMaxVolume());
            speaker.updateEmitterSnapshot();assertTrue(ServerSpeakerRegistry.getEmitter(new SpeakerLocation("minecraft:overworld",4,0,0)).active());
        }
    }
    @Test void freshProxyEmitsWithoutLocalPowerAndVolumeOverrideDoesNotChangeSavedVolume() {
        var proxy=new ProxySpeakerBlockEntity(validType(),new BlockPos(5,0,0),Blocks.IRON_BLOCK.defaultBlockState());proxy.setLevel(level);proxy.setSpeakerId("test");
        when(level.getBlockEntity(proxy.getBlockPos())).thenReturn(proxy);proxy.setMaxVolume(0.6f);
        assertTrue(ServerSpeakerRegistry.getEmitter(new SpeakerLocation("minecraft:overworld",5,0,0)).active());
        assertTrue(controller.configure(player,"test",true,proxy.getBlockPos(),ControllerAction.VOLUME,"",false,1));
        observe(12);assertEquals(0.8f,proxy.getEffectiveVolume(),0.0001);assertEquals(0.6f,proxy.getMaxVolume(),0.0001);
        controller.setRemoved();ControllerCoordinator.flush(level.getServer());assertEquals(0.6f,proxy.getEffectiveVolume(),0.0001);
    }
    @Test void proxyReloadReappliesHeldEnabledInputAndLastRemovalEnablesOutput() {
        var pos=new BlockPos(5,0,0);var proxy=new ProxySpeakerBlockEntity(validType(),pos,Blocks.IRON_BLOCK.defaultBlockState());proxy.setLevel(level);proxy.setSpeakerId("test");when(level.getBlockEntity(pos)).thenReturn(proxy);
        assertTrue(controller.configure(player,"test",true,pos,ControllerAction.ENABLED,"",false,1));observe(0);
        var location=new SpeakerLocation("minecraft:overworld",5,0,0);assertFalse(ServerSpeakerRegistry.getEmitter(location).active());
        var reloaded=new ProxySpeakerBlockEntity(validType(),pos,Blocks.IRON_BLOCK.defaultBlockState());reloaded.setLevel(level);reloaded.setSpeakerId("test");when(level.getBlockEntity(pos)).thenReturn(reloaded);
        ControllerCoordinator.flush(level.getServer());assertFalse(ServerSpeakerRegistry.getEmitter(location).active());
        controller.setRemoved();ControllerCoordinator.flush(level.getServer());assertTrue(ServerSpeakerRegistry.getEmitter(location).active());
    }
    @Test void otherNetworksDoNotCompeteAndManualVolumeSurvivesAnOverride() {
        var second=new SpeakerState("https://example.invalid/music.wav","music.wav",false,false,-1);ServerSpeakerRegistry.updateSpeakerStateByFullKey("minecraft:overworld/net_second",second);
        configure(ControllerAction.VOLUME);observe(15);assertEquals(0.5f,state.getMaxVolume());
        state.setMaxVolume(0.25f);assertEquals(0.5f,state.getMaxVolume());assertEquals(0.25f,state.getConfiguredMaxVolume());
        var other=another(1,ControllerAction.VOLUME);assertTrue(other.configure(player,"second",false,BlockPos.ZERO,ControllerAction.VOLUME,"",false,1));other.observeSignal(15);ControllerCoordinator.flush(level.getServer());
        assertEquals(1,ServerSpeakerRegistry.getSpeakerStateByFullKey("minecraft:overworld/net_second").getMaxVolume());assertEquals(0.5f,state.getMaxVolume());
        controller.setRemoved();ControllerCoordinator.flush(level.getServer());assertEquals(0.25f,state.getMaxVolume());
    }
    @Test void revokedHeldVolumeIsReleasedAndNeverChangesSavedSettings() {
        configure(ControllerAction.VOLUME);observe(15);state.setMaxVolume(0.7f);
        state.setOwnerUuid(UUID.randomUUID());state.setAccessMode(SpeakerAccess.OWNER_ONLY);ControllerCoordinator.flush(level.getServer());
        assertEquals(0.7f,state.getMaxVolume(),0.0001);assertEquals("denied",controller.getStatus());
    }
    @Test void networkVolumeOverrideIsNotPersistedByTheRegistrySerializer() {
        configure(ControllerAction.VOLUME);observe(15);state.setMaxVolume(0.7f);
        var gson=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        var saved=gson.fromJson(gson.toJson(state),SpeakerState.class);assertEquals(0.7f,saved.getMaxVolume(),0.0001);
        assertEquals(0.5f,state.copy().getMaxVolume(),0.0001);
    }
    @Test void missingProxyChunksAreNeverLoadedAndAnotherProxyDoesNotShareItsGate() {
        var a=new BlockPos(5,0,0);var b=new BlockPos(6,0,0);
        var first=new ProxySpeakerBlockEntity(validType(),a,Blocks.IRON_BLOCK.defaultBlockState());first.setLevel(level);first.setSpeakerId("test");when(level.getBlockEntity(a)).thenReturn(first);
        var second=new ProxySpeakerBlockEntity(validType(),b,Blocks.IRON_BLOCK.defaultBlockState());second.setLevel(level);second.setSpeakerId("test");when(level.getBlockEntity(b)).thenReturn(second);
        assertTrue(controller.configure(player,"test",true,a,ControllerAction.ENABLED,"",false,1));observe(0);
        assertFalse(ServerSpeakerRegistry.getEmitter(new SpeakerLocation("minecraft:overworld",5,0,0)).active());
        assertTrue(ServerSpeakerRegistry.getEmitter(new SpeakerLocation("minecraft:overworld",6,0,0)).active());
        when(level.hasChunkAt(a)).thenReturn(false);clearInvocations(level);ControllerCoordinator.flush(level.getServer());
        verify(level,never()).getBlockEntity(a);assertEquals("missing_proxy",controller.getStatus());
        assertTrue(ServerSpeakerRegistry.getEmitter(new SpeakerLocation("minecraft:overworld",5,0,0)).active());
    }
    private RedstoneControllerBlockEntity roundTrip(RedstoneControllerBlockEntity source) throws Exception {
        var getter=java.util.Arrays.stream(source.getClass().getDeclaredMethods()).filter(m -> m.getName().equals("getUpdateTag")).findFirst().orElseThrow();
        Object lookup=getter.getParameterCount()==0?null:mock(getter.getParameterTypes()[0]);
        Object tag=getter.getParameterCount()==0?getter.invoke(source):getter.invoke(source,lookup);
        source.setRemoved();ControllerCoordinator.flush(level.getServer());
        var restored=new RedstoneControllerBlockEntity(validType(),source.getBlockPos(),Blocks.IRON_BLOCK.defaultBlockState());
        var loader=java.util.Arrays.stream(source.getClass().getDeclaredMethods()).filter(m -> m.getName().equals("load") || m.getName().equals("loadAdditional")).findFirst().orElseThrow();loader.setAccessible(true);
        if(loader.getParameterTypes()[0].getSimpleName().equals("ValueInput")) {
            var inputType=Class.forName("net.minecraft.world.level.storage.TagValueInput");
            var create=java.util.Arrays.stream(inputType.getMethods()).filter(m -> m.getName().equals("create") && m.getParameterCount()==3 && java.util.Arrays.stream(m.getParameterTypes()).anyMatch(t -> t.getName().contains("HolderLookup")) && java.util.Arrays.stream(m.getParameterTypes()).anyMatch(t -> t.isInstance(tag))).findFirst().orElseThrow();
            Object[] args=new Object[3];
            for(int i=0;i<3;i++) {
                var type=create.getParameterTypes()[i];
                args[i]=type.isInstance(tag)?tag:type.getSimpleName().equals("ProblemReporter")?type.getField("DISCARDING").get(null):lookup;
                assertTrue(type.isInstance(args[i]),create+" argument "+i+" received "+args[i]);
            }
            loader.invoke(restored,create.invoke(null,args));
        } else if(loader.getParameterCount()==1) loader.invoke(restored,tag);
        else loader.invoke(restored,tag,lookup);
        restored.setLevel(level);return restored;
    }
    @Test void everyControllerSettingSurvivesAuthoritativeUpdateTagRoundTrip() throws Exception {
        for(var job:ControllerAction.values()) {
            if(job==ControllerAction.ANNOUNCEMENT) continue; // Live client fixture covers authorized clip metadata too.
            assertTrue(controller.configure(player,"test",false,new BlockPos(3,4,5),job,"",true,.35f));
            controller=roundTrip(controller);
            assertEquals(job,controller.getAction());assertEquals("test",controller.getNetworkId());
            assertEquals(new BlockPos(3,4,5),controller.getProxyPos());
            assertFalse(controller.isProxyTarget());assertTrue(controller.isRestartAnnouncement());
            assertEquals(.35f,controller.getVolumeCeiling(),.0001);
        }
    }
    @Test void savedPoweredPulseDoesNotRetriggerAndAnalogReloadRestoresItsInput() throws Exception {
        configure(ControllerAction.TOGGLE);observe(15);assertTrue(state.isPlaying());
        controller=roundTrip(controller);observe(15);assertFalse(state.isPaused());
        when(level.getBestNeighborSignal(BlockPos.ZERO)).thenReturn(12);configure(ControllerAction.VOLUME);observe(12);
        controller=roundTrip(controller);observe(12);assertEquals(0.4f,state.getMaxVolume(),0.0001);
        assertEquals(ControllerAction.VOLUME,controller.getAction());assertEquals("test",controller.getNetworkId());
    }
    @Test void proxyRelinkClearsOldNetworkOverridesAndNeverEnablesAnUnrelatedOutputOnCleanup() {
        var pos=new BlockPos(5,0,0);var proxy=new ProxySpeakerBlockEntity(validType(),pos,Blocks.IRON_BLOCK.defaultBlockState());proxy.setLevel(level);proxy.setSpeakerId("test");proxy.setMaxVolume(0.6f);when(level.getBlockEntity(pos)).thenReturn(proxy);
        assertTrue(controller.configure(player,"test",true,pos,ControllerAction.VOLUME,"",false,1));observe(0);assertEquals(0,proxy.getEffectiveVolume());
        proxy.setSpeakerId("second");assertEquals(0.6f,proxy.getEffectiveVolume(),0.0001);
        ControllerCoordinator.flush(level.getServer());assertEquals("missing_proxy",controller.getStatus());assertEquals(0.6f,proxy.getEffectiveVolume(),0.0001);
    }
    @Test void unloadedProxyVolumeIsReleasedThroughItsSnapshotWithoutLoadingTheChunk() {
        var pos=new BlockPos(5,0,0);var proxy=new ProxySpeakerBlockEntity(validType(),pos,Blocks.IRON_BLOCK.defaultBlockState());proxy.setLevel(level);proxy.setSpeakerId("test");proxy.setMaxVolume(0.6f);when(level.getBlockEntity(pos)).thenReturn(proxy);
        assertTrue(controller.configure(player,"test",true,pos,ControllerAction.VOLUME,"",false,1));observe(0);
        var location=new SpeakerLocation("minecraft:overworld",5,0,0);assertEquals(0,ServerSpeakerRegistry.getEmitter(location).maxVolume());
        proxy.setMaxVolume(0.25f);ControllerCoordinator.flush(level.getServer());
        when(level.hasChunkAt(pos)).thenReturn(false);clearInvocations(level);controller.setRemoved();ControllerCoordinator.flush(level.getServer());
        verify(level,never()).getBlockEntity(pos);assertEquals(0.25f,ServerSpeakerRegistry.getEmitter(location).maxVolume(),0.0001);
    }
    @Test void matchingSpeakerIdsInDifferentDimensionsNeverShareControllerInputs() {
        configure(ControllerAction.VOLUME);controller.observeSignal(15);
        var otherServer=level.getServer();var otherLevel=mock(ServerLevel.class);when(otherLevel.dimension()).thenReturn(Level.NETHER);when(otherLevel.getServer()).thenReturn(otherServer);
        when(otherLevel.hasChunkAt(any())).thenReturn(true);when(otherLevel.mayInteract(eq(player),any())).thenReturn(true);when(player.level()).thenReturn(otherLevel);
        ServerSpeakerRegistry.updateSpeakerStateByFullKey("minecraft:the_nether/net_test",new SpeakerState());
        var other=new RedstoneControllerBlockEntity(validType(),BlockPos.ZERO,Blocks.IRON_BLOCK.defaultBlockState());other.setLevel(otherLevel);
        assertTrue(other.configure(player,"test",false,BlockPos.ZERO,ControllerAction.VOLUME,"",false,1));other.observeSignal(3);
        ControllerCoordinator.flush(level.getServer());assertEquals(0.5f,state.getMaxVolume(),0.0001);
        assertEquals(0.2f,ServerSpeakerRegistry.getSpeakerStateByFullKey("minecraft:the_nether/net_test").getMaxVolume(),0.0001);
    }
    @Test void duplicateProxyPlaybackControllersUseOrAndRetargetingReleasesOnlyTheirOwnGate() {
        var pos=new BlockPos(5,0,0);var proxy=new ProxySpeakerBlockEntity(validType(),pos,Blocks.IRON_BLOCK.defaultBlockState());proxy.setLevel(level);proxy.setSpeakerId("test");when(level.getBlockEntity(pos)).thenReturn(proxy);
        assertTrue(controller.configure(player,"test",true,pos,ControllerAction.ENABLED,"",false,1));var other=another(1,ControllerAction.ENABLED);
        assertTrue(other.configure(player,"test",true,pos,ControllerAction.ENABLED,"",false,1));
        controller.observeSignal(0);other.observeSignal(15);ControllerCoordinator.flush(level.getServer());
        var location=new SpeakerLocation("minecraft:overworld",5,0,0);assertTrue(ServerSpeakerRegistry.getEmitter(location).active());
        other.setRemoved();ControllerCoordinator.flush(level.getServer());assertFalse(ServerSpeakerRegistry.getEmitter(location).active());
        configure(ControllerAction.TOGGLE);ControllerCoordinator.flush(level.getServer());assertTrue(ServerSpeakerRegistry.getEmitter(location).active());
    }
    @Test void heldPlaybackInputDoesNotUndoAWinningStopPulseOrManualPause() {
        configure(ControllerAction.ENABLED);observe(15);assertTrue(state.isPlaying());
        var stop=another(1,ControllerAction.STOP);stop.observeSignal(15);ControllerCoordinator.flush(level.getServer());assertFalse(state.isPlaying());
        ControllerCoordinator.flush(level.getServer());assertFalse(state.isPlaying());
        observe(0);observe(15);assertTrue(state.isPlaying());
        assertTrue(ServerSpeakerControlService.pause(level.getServer(),level,"minecraft:overworld/net_test"));
        ControllerCoordinator.flush(level.getServer());assertTrue(state.isPaused());
    }
    @Test void loadingDoesNotReplayAPulseThatHappenedWhileUnloaded() throws Exception {
        configure(ControllerAction.TOGGLE);observe(0);controller=roundTrip(controller);observe(15);assertFalse(state.isPlaying());
        observe(0);observe(15);assertTrue(state.isPlaying());
    }
    @Test void trackSelectionPreservesTheRisingStrengthAndBoundsItToTheActivePlaylist() {
        state.getPlaylist().add("one","one.wav");state.getPlaylist().add("two","two.wav");state.getPlaylist().add("three","three.wav");
        configure(ControllerAction.TRACK);controller.observeSignal(2);controller.observeSignal(7);ControllerCoordinator.flush(level.getServer());
        assertEquals(1,state.getPlaylist().getCurrentIndex());assertEquals("two",state.getAudioId());
        observe(0);observe(15);assertEquals(2,state.getPlaylist().getCurrentIndex());
    }
    @Test void conflictingTrackSelectorsUseStableSourcePositionAndNeverDoubleSelect() {
        state.getPlaylist().add("one","one.wav");state.getPlaylist().add("two","two.wav");
        configure(ControllerAction.TRACK);var other=another(1,ControllerAction.TRACK);
        other.observeSignal(1);controller.observeSignal(2);ControllerCoordinator.flush(level.getServer());assertEquals(1,state.getPlaylist().getCurrentIndex());
        controller.observeSignal(0);other.observeSignal(0);controller.observeSignal(1);other.observeSignal(2);ControllerCoordinator.flush(level.getServer());assertEquals(0,state.getPlaylist().getCurrentIndex());
    }
    @Test void heldAndFallingSignalsNeverRepeatPulseTransport() {
        configure(ControllerAction.TOGGLE);
        observe(15); assertTrue(state.isPlaying()); assertFalse(state.isPaused());
        observe(15); observe(7); observe(0);
        assertFalse(state.isPaused());
        observe(8); assertTrue(state.isPaused());
    }
    @Test void analogAndEnabledJobsApplyExistingSignalThenObserveChanges() {
        when(level.getBestNeighborSignal(BlockPos.ZERO)).thenReturn(12);
        configure(ControllerAction.VOLUME); observe(12);
        assertEquals(0.4f,state.getMaxVolume(),0.0001);
        observe(0); assertEquals(0,state.getMaxVolume());
        configure(ControllerAction.ENABLED); observe(12);
        assertTrue(state.isPlaying()); observe(0); assertTrue(state.isPaused());
    }
    @Test void revokedPermissionStopsAnOldLinkFromActing() {
        state.setOwnerUuid(UUID.randomUUID()); state.setAccessMode(SpeakerAccess.PUBLIC);
        configure(ControllerAction.TOGGLE);
        state.setAccessMode(SpeakerAccess.OWNER_ONLY);
        observe(15);
        assertFalse(state.isPlaying()); assertEquals("denied",controller.getStatus());
    }
    @Test void missingAndProtectedTargetsNeverCreateNetworkState() {
        assertFalse(controller.configure(player,"missing",false,BlockPos.ZERO,ControllerAction.TOGGLE,"",false,1));
        assertNull(ServerSpeakerRegistry.getSpeakerState(level,"net_missing"));
        when(level.mayInteract(eq(player),any())).thenReturn(false);
        assertFalse(controller.configure(player,"test",false,BlockPos.ZERO,ControllerAction.TOGGLE,"",false,1));
    }
    @Test void configuringWhilePoweredDoesNotManufactureAPulse() {
        when(level.getBestNeighborSignal(BlockPos.ZERO)).thenReturn(15);
        configure(ControllerAction.TOGGLE); observe(15);
        assertFalse(state.isPlaying());
        observe(0); observe(15); assertTrue(state.isPlaying());
    }
    @Test void aLinkedControllerEnablesAnUnpoweredProxy() {
        BlockPos target=new BlockPos(5,0,0);
        var proxy=new ProxySpeakerBlockEntity(validType(),target,Blocks.IRON_BLOCK.defaultBlockState());
        proxy.setLevel(level); proxy.setSpeakerId("test");
        when(level.getBlockEntity(target)).thenReturn(proxy);
        assertTrue(controller.configure(player,"test",true,target,ControllerAction.ENABLED,"",false,1));
        observe(15);
        var location=new SpeakerLocation("minecraft:overworld",5,0,0);
        assertTrue(ServerSpeakerRegistry.getEmitter(location).active());
        assertFalse(state.isPlaying()); // Local output intent does not start the shared network.
        observe(0); assertFalse(ServerSpeakerRegistry.getEmitter(location).active());
        observe(15); assertTrue(ServerSpeakerRegistry.getEmitter(location).active());
        proxy.setProxyPlaying(true); assertTrue(ServerSpeakerRegistry.getEmitter(location).active());
    }
}
