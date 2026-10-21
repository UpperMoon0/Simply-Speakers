package com.nstut.simplyspeakers.blocks.entities;

import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.control.ControllerAction;
import com.nstut.simplyspeakers.speakers.*;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldProvider;
import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.*;
import org.mockito.MockedStatic;
import java.util.Collections;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Actual native controller/coordinator/state; substitute only world and packet I/O. */
public class LegacyControllerUnloadIntegrationTest {
    private WorldServer world;
    private MinecraftServer server;
    private EntityPlayerMP player;
    private SpeakerState state;
    private RedstoneControllerBlockEntity tile;
    private MockedStatic<?> network;

    @BeforeClass public static void bootstrap() { Bootstrap.register(); }
    @Before public void prepare() throws Exception {
        ServerSpeakerRegistry.resetForWorld(); ServerPlaybackManager.resetForWorld(); ControllerCoordinator.reset();
        server=mock(MinecraftServer.class); world=mock(WorldServer.class); player=mock(EntityPlayerMP.class);
        PlayerList players=mock(PlayerList.class); when(server.getPlayerList()).thenReturn(players);
        when(players.getPlayers()).thenReturn(Collections.emptyList());
        when(world.getMinecraftServer()).thenReturn(server);
        WorldProvider provider=mock(WorldProvider.class); when(provider.getDimension()).thenReturn(0);
        // Minecraft's world/provider/player-list fields are final, and supplied by the real constructor in game.
        field(net.minecraft.world.World.class,"provider",world,provider);
        field(net.minecraft.world.World.class,"playerEntities",world,Collections.emptyList());
        when(world.isBlockLoaded(any())).thenReturn(true);
        when(world.isBlockModifiable(eq(player),any())).thenReturn(true);
        when(world.getBlockState(any())).thenReturn(Blocks.IRON_BLOCK.getDefaultState());
        player.world=world; when(player.getUniqueID()).thenReturn(UUID.randomUUID());
        tile=new RedstoneControllerBlockEntity(null,BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState());
        tile.setWorld(world); when(world.getTileEntity(BlockPos.ORIGIN)).thenReturn(tile);
        ServerSpeakerRegistry.updateSpeakerStateByFullKey("minecraft:overworld/net_test",new SpeakerState());
        state=ServerSpeakerRegistry.getSpeakerStateByFullKey("minecraft:overworld/net_test"); state.setMaxVolume(.7f);
        state.setAudioId("00000000-0000-0000-0000-000000000001"); state.setAudioFilename("fixture.wav");
        network=mockStatic(Class.forName("com.nstut.simplyspeakers.speakers.ServerPlaybackEnvironment"));
    }
    private static void field(Class<?> owner,String name,Object target,Object value) throws Exception {
        java.lang.reflect.Field field=owner.getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    @After public void cleanup() {
        if(network!=null)network.close(); ControllerCoordinator.reset();
        ServerSpeakerRegistry.resetForWorld(); ServerPlaybackManager.resetForWorld();
    }
    private void configure(ControllerAction action) {
        assertTrue(tile.configure(player,"test",false,BlockPos.ORIGIN,action,"",false,.5f));
    }
    @Test public void nativeUnloadHookRestoresVolumeWithoutInvalidatingReusableTile() {
        configure(ControllerAction.VOLUME); tile.observeSignal(15); ControllerCoordinator.flush(server);
        assertEquals(.5f,state.getMaxVolume(),.0001f);
        tile.onChunkUnload(); when(world.isBlockLoaded(BlockPos.ORIGIN)).thenReturn(false); clearInvocations(world);
        ControllerCoordinator.flush(server);
        verify(world,never()).getTileEntity(BlockPos.ORIGIN);
        assertEquals(.7f,state.getMaxVolume(),.0001f); assertFalse(tile.isInvalid());
        when(world.isBlockLoaded(BlockPos.ORIGIN)).thenReturn(true); tile.onLoad();
        tile.observeSignal(15); ControllerCoordinator.flush(server);
        assertEquals(.5f,state.getMaxVolume(),.0001f);
    }
    @Test public void missedUnloadHookStillRestoresWithoutLoadingMissingChunk() {
        configure(ControllerAction.VOLUME); tile.observeSignal(15); ControllerCoordinator.flush(server);
        when(world.isBlockLoaded(BlockPos.ORIGIN)).thenReturn(false); clearInvocations(world);
        ControllerCoordinator.flush(server);
        verify(world,never()).getTileEntity(BlockPos.ORIGIN);
        assertEquals(.7f,state.getMaxVolume(),.0001f); assertFalse(tile.isInvalid());
    }
    @Test public void replacementDiscardsPendingPulseFromOldTile() {
        configure(ControllerAction.TOGGLE); tile.observeSignal(15);
        when(world.getTileEntity(BlockPos.ORIGIN)).thenReturn(mock(net.minecraft.tileentity.TileEntity.class));
        ControllerCoordinator.flush(server);
        assertFalse(state.isPlaying()); assertFalse(tile.isInvalid());
    }
    @Test public void unloadDiscardsQueuedPulseAndFirstReloadObservationIsNotAnEdge() {
        configure(ControllerAction.TOGGLE); tile.observeSignal(15); tile.onChunkUnload();
        ControllerCoordinator.flush(server); assertFalse(state.isPlaying());
        tile.onLoad(); tile.observeSignal(15); ControllerCoordinator.flush(server); assertFalse(state.isPlaying());
        tile.observeSignal(0); tile.observeSignal(15); ControllerCoordinator.flush(server);
        assertTrue(state.isPlaying());
    }
    @Test public void missedHookRetirementAlsoResetsTheFirstObservationOnReuse() {
        configure(ControllerAction.TOGGLE);
        when(world.isBlockLoaded(BlockPos.ORIGIN)).thenReturn(false); ControllerCoordinator.flush(server);
        when(world.isBlockLoaded(BlockPos.ORIGIN)).thenReturn(true);
        tile.observeSignal(15); ControllerCoordinator.flush(server); assertFalse(state.isPlaying());
        tile.observeSignal(0); tile.observeSignal(15); ControllerCoordinator.flush(server);
        assertTrue(state.isPlaying());
    }
}
