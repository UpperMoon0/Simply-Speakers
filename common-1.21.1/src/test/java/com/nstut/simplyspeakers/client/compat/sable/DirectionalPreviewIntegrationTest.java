package com.nstut.simplyspeakers.client.compat.sable;

import com.nstut.simplyspeakers.audio.DirectionalAudio;
import com.nstut.simplyspeakers.blocks.SpeakerBlock;
import com.nstut.simplyspeakers.client.DirectionalPreview;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real preview lifecycle and render-pose math, with only the world/body lookup substituted. */
class DirectionalPreviewIntegrationTest {
    private static final BlockPos POSITION = new BlockPos(1, 2, 3);
    private Minecraft client;
    private ClientLevel level;
    private BlockState state;
    private SableCompanion sable;
    private ClientSubLevelAccess body;
    private Pose3d render;
    private MockedStatic<Minecraft> minecraft;
    private MockedStatic<ClientSpeakerSpatialResolver> spatial;

    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @BeforeEach void setup() {
        client = mock(Minecraft.class);
        level = mock(ClientLevel.class);
        state = mock(BlockState.class);
        client.level = level;
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.getBlockState(POSITION)).thenReturn(state);
        when(state.hasProperty(SpeakerBlock.FACING)).thenReturn(true);
        when(state.getValue(SpeakerBlock.FACING)).thenReturn(Direction.NORTH);
        minecraft = mockStatic(Minecraft.class);
        minecraft.when(Minecraft::getInstance).thenReturn(client);

        sable = mock(SableCompanion.class);
        body = mock(ClientSubLevelAccess.class);
        render = new Pose3d();
        render.position().set(10, 20, 30);
        render.orientation().rotateY(Math.PI / 2);
        when(body.renderPose()).thenReturn(render);
        when(sable.getContaining(level, POSITION)).thenReturn(body);
        when(sable.isInPlotGrid(level, POSITION)).thenReturn(true);
        spatial = mockStatic(ClientSpeakerSpatialResolver.class, call -> {
            if (call.getMethod().getName().equals("resolveRender") && call.getArguments().length == 2)
                return ClientSpeakerSpatialResolver.resolveRender(sable, level, (BlockPos) call.getArgument(1));
            if (call.getMethod().getName().equals("resolveRenderFacing") && call.getArguments().length == 3)
                return ClientSpeakerSpatialResolver.resolveRenderFacing(sable, level, (BlockPos) call.getArgument(1), call.getArgument(2));
            return CALLS_REAL_METHODS.answer(call);
        });
        DirectionalPreview.clear();
    }

    @AfterEach void cleanup() {
        DirectionalPreview.clear();
        spatial.close();
        minecraft.close();
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "WEST", "EAST"})
    void fixedSpeakerPreviewUsesPlaybackRenderPositionAndFacing(Direction facing) throws Exception {
        when(state.getValue(SpeakerBlock.FACING)).thenReturn(facing);
        DirectionalPreview.show(POSITION, 16, 1, 1, 90, 1);

        assertTrue(remaining() > 0);
        assertPosition(13.5, 22.5, 28.5);
        assertFacingMatchesPlayback(facing.ordinal());
        verify(body, never()).logicalPose();

        // A body can move and rotate while the same preview is still visible.
        render.position().set(50, 60, 70);
        render.orientation().identity().rotateY(-Math.PI / 2);
        DirectionalPreview.tick(client);
        assertPosition(46.5, 62.5, 71.5);
        assertFacingMatchesPlayback(facing.ordinal());
    }

    @Test void unresolvedBodyDismissesRatherThanShowingPlotCoordinates() throws Exception {
        DirectionalPreview.show(POSITION, 16, 1, 1, 90, 1);
        assertTrue(remaining() > 0);
        when(sable.getContaining(level, POSITION)).thenReturn(null);
        DirectionalPreview.tick(client);
        assertEquals(0, remaining());
        assertNull(renderOrigin());

        // The first HUD frame must be safe too, before a client tick has run.
        DirectionalPreview.show(POSITION, 16, 1, 1, 90, 1);
        assertEquals(0, remaining());
        assertNull(renderOrigin());
    }

    @Test void unresolvedFacingDismissesEvenWhenPositionResolves() throws Exception {
        DirectionalPreview.show(POSITION, 16, 1, 1, 90, 1);
        spatial.when(() -> ClientSpeakerSpatialResolver.resolveRenderFacing(level, POSITION, Direction.NORTH.ordinal()))
                .thenReturn(null);
        DirectionalPreview.tick(client);
        assertEquals(0, remaining());
        assertNull(renderOrigin());
    }

    @Test void ordinaryBlockRetainsItsCenteredPositionAndVanillaFacing() throws Exception {
        when(sable.getContaining(level, POSITION)).thenReturn(null);
        when(sable.isInPlotGrid(level, POSITION)).thenReturn(false);
        DirectionalPreview.show(POSITION, 16, 1, 1, 90, 1);
        assertEquals(Vec3.atCenterOf(POSITION), renderOrigin());
        assertEquals(0, (double) field("rotation"), 1e-9);
    }

    private void assertFacingMatchesPlayback(int ordinal) throws Exception {
        double rotation = (double) field("rotation");
        double[] local = DirectionalAudio.facingFromOrdinal(ordinal);
        double[] displayed = {local[0] * Math.cos(rotation) - local[1] * Math.sin(rotation),
                local[0] * Math.sin(rotation) + local[1] * Math.cos(rotation)};
        assertArrayEquals(ClientSpeakerSpatialResolver.resolveRenderFacing(level, POSITION, ordinal), displayed, 1e-9);
    }

    private static int remaining() throws Exception { return (int) field("remaining"); }
    private static Vec3 renderOrigin() throws Exception { return (Vec3) field("renderOrigin"); }
    private static void assertPosition(double x, double y, double z) throws Exception {
        Vec3 origin = renderOrigin();
        assertNotNull(origin);
        assertEquals(x, origin.x, 1e-9);
        assertEquals(y, origin.y, 1e-9);
        assertEquals(z, origin.z, 1e-9);
    }
    private static Object field(String name) throws Exception {
        var field = DirectionalPreview.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
}
