package com.nstut.simplyspeakers.blocks.entities;
import com.nstut.simplyspeakers.SpeakerState;
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
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.Collections;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Real native tile/coordinator/state behavior; only world access and packet I/O are substituted. */
class LegacyControllerUnloadIntegrationTest {
    private ServerLevel level;
    private MinecraftServer server;
    private ServerPlayer player;
    private SpeakerState state;
    private RedstoneControllerBlockEntity tile;
    private MockedStatic<?> network;
    @BeforeAll static void bootstrap() {SharedConstants.getCurrentVersion();Bootstrap.bootStrap();}
    @BeforeEach void prepare() throws Exception {
        ServerSpeakerRegistry.resetForWorld();ServerPlaybackManager.resetForWorld();ControllerCoordinator.reset();
        server=mock(MinecraftServer.class);level=mock(ServerLevel.class);player=mock(ServerPlayer.class);
        PlayerList players=mock(PlayerList.class);when(server.getPlayerList()).thenReturn(players);
        when(players.getPlayers()).thenReturn(Collections.emptyList());
        when(level.players()).thenReturn(Collections.emptyList());
        when(level.getServer()).thenReturn(server);when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.hasChunkAt(any())).thenReturn(true);when(level.mayInteract(eq(player),any())).thenReturn(true);
        when(level.getBlockState(any())).thenReturn(Blocks.IRON_BLOCK.defaultBlockState());
        player.level=level;when(player.getUUID()).thenReturn(UUID.randomUUID());
        tile=new RedstoneControllerBlockEntity(null,BlockPos.ZERO,Blocks.IRON_BLOCK.defaultBlockState());
        tile.setLevel(level);when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(tile);
        ServerSpeakerRegistry.updateSpeakerStateByFullKey("minecraft:overworld/net_test",new SpeakerState());
        state=ServerSpeakerRegistry.getSpeakerStateByFullKey("minecraft:overworld/net_test");state.setMaxVolume(.7f);state.setAudioId("fixture-audio");state.setAudioFilename("fixture.wav");
        network=mockStatic(Class.forName("com.nstut.simplyspeakers.speakers.ServerPlaybackEnvironment"));
    }
    @AfterEach void clear() {
        if(network!=null)network.close();ControllerCoordinator.reset();
        ServerSpeakerRegistry.resetForWorld();ServerPlaybackManager.resetForWorld();
    }
    private void configure(ControllerAction action) {
        assertTrue(tile.configure(player,"test",false,BlockPos.ZERO,action,"",false,.5f));
    }
    @Test void realUnloadedContinuousControllerRestoresManualVolumeWithoutLoadingChunk() {
        configure(ControllerAction.VOLUME);tile.observeSignal(15);ControllerCoordinator.flush(server);
        assertEquals(.5f,state.getMaxVolume(),.0001f);
        when(level.hasChunkAt(BlockPos.ZERO)).thenReturn(false);clearInvocations(level);
        ControllerCoordinator.flush(server);
        verify(level,never()).getBlockEntity(BlockPos.ZERO);
        assertEquals(.7f,state.getMaxVolume(),.0001f);assertFalse(tile.isRemoved());
        when(level.hasChunkAt(BlockPos.ZERO)).thenReturn(true);tile.observeSignal(15);ControllerCoordinator.flush(server);
        assertEquals(.5f,state.getMaxVolume(),.0001f);
    }
    @Test void replacementRetiresOldInstanceAndDiscardsQueuedPulse() {
        configure(ControllerAction.TOGGLE);tile.observeSignal(15);
        when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(null);ControllerCoordinator.flush(server);
        assertFalse(state.isPlaying());assertFalse(tile.isRemoved());
    }
    @Test void reusedTileRequiresFreshObservationAndDoesNotFabricateReloadPulse() {
        configure(ControllerAction.TOGGLE);
        when(level.hasChunkAt(BlockPos.ZERO)).thenReturn(false);ControllerCoordinator.flush(server);
        when(level.hasChunkAt(BlockPos.ZERO)).thenReturn(true);
        tile.observeSignal(15);ControllerCoordinator.flush(server);assertFalse(state.isPlaying());
        tile.observeSignal(0);tile.observeSignal(15);ControllerCoordinator.flush(server);
        assertTrue(state.isPlaying());
    }
}
