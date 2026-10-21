package com.nstut.simplyspeakers.blocks.entities;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.compat.LegacyWorld;
import com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.*;
import org.mockito.MockedStatic;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class LegacyPersistenceIdentityTest {
    @BeforeClass public static void bootstrap(){Bootstrap.register();}
    @After public void cleanup(){ServerSpeakerRegistry.resetForWorld();}
    @Test public void nativeSplitUuidLoadsWithoutLegacyTemplateMigration() {
        World world=mock(World.class);SpeakerState state=new SpeakerState();
        when(world.getBlockState(any())).thenReturn(Blocks.IRON_BLOCK.getDefaultState());
        UUID identity=UUID.randomUUID();NBTTagCompound tag=new NBTTagCompound();tag.setUniqueId("InternalStateId",identity);
        assertFalse(tag.hasKey("InternalStateId"));assertTrue(tag.hasUniqueId("InternalStateId"));
        SpeakerBlockEntity tile=new SpeakerBlockEntity(null,BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState());tile.setWorld(world);
        try(MockedStatic<ServerSpeakerRegistry> registry=mockStatic(ServerSpeakerRegistry.class)) {
            registry.when(()->ServerSpeakerRegistry.getDimension(world)).thenReturn("minecraft:overworld");
            registry.when(()->ServerSpeakerRegistry.getSpeakerState(eq(world),anyString())).thenReturn(state);
            registry.when(()->ServerSpeakerRegistry.getOrCreateSpeakerState(eq(world),anyString())).thenReturn(state);
            tile.load(tag);
            assertEquals("internal_"+identity,tile.getStateKey());
            registry.verify(()->ServerSpeakerRegistry.applyLegacyStandaloneTemplate(eq(world),anyString()),never());
        }
    }
    @Test public void nativeDimensionsPreserveCanonicalVanillaPersistenceNamespaces() {
        assertEquals("minecraft:overworld",LegacyWorld.dimension(0));
        assertEquals("minecraft:the_nether",LegacyWorld.dimension(-1));
        assertEquals("minecraft:the_end",LegacyWorld.dimension(1));
        assertEquals("minecraft:dimension_42",LegacyWorld.dimension(42));
        assertEquals(LegacyWorld.dimension(0),ServerSpeakerRegistry.getDimension(null));
    }
    @Test public void unprefixedRegistryMigrationProducesReachableOverworldKeys() throws Exception {
        Path folder=Files.createTempDirectory("speaker-registry-native-");Path registry=folder.resolve("speaker_registry.json");
        try {
            ServerSpeakerRegistry.resetForWorld();
            Files.write(registry,"{\"net_test\":{\"maxVolume\":0.3}}".getBytes(StandardCharsets.UTF_8));
            java.lang.reflect.Field path=ServerSpeakerRegistry.class.getDeclaredField("registryFilePath");path.setAccessible(true);path.set(null,registry);
            ServerSpeakerRegistry.loadRegistry();
            assertNotNull(ServerSpeakerRegistry.getSpeakerStateByFullKey("minecraft:overworld/net_test"));
            assertNull(ServerSpeakerRegistry.getSpeakerStateByFullKey("mc:overworld/net_test"));
        } finally {
            ServerSpeakerRegistry.resetForWorld();Files.deleteIfExists(registry);Files.deleteIfExists(folder.resolve("speaker_registry.json.bak"));Files.deleteIfExists(folder);
        }
    }
}
