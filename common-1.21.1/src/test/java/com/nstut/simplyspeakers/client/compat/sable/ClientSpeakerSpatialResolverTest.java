package com.nstut.simplyspeakers.client.compat.sable;

import dev.ryanhcode.sable.companion.*;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientSpeakerSpatialResolverTest {
    @Test void audioUsesInterpolatedRenderPoseAndCenteredBlockCoordinates() {
        var sable = mock(SableCompanion.class); var body = mock(ClientSubLevelAccess.class);
        var render = new Pose3d(); render.position().set(10, 20, 30); render.orientation().rotateY(Math.PI / 2);
        var logical = new Pose3d(); logical.position().set(1000, 2000, 3000);
        when(body.renderPose()).thenReturn(render); when(body.logicalPose()).thenReturn(logical);
        var pos = new BlockPos(1, 2, 3); when(sable.getContaining(null, pos)).thenReturn(body);
        var result = ClientSpeakerSpatialResolver.resolveRender(sable, null, pos);
        assertEquals(13.5, result.x, 1e-6); assertEquals(22.5, result.y, 1e-6); assertEquals(28.5, result.z, 1e-6);
        assertArrayEquals(new double[] {-1, 0}, ClientSpeakerSpatialResolver.resolveRenderFacing(sable, null, pos, 2), 1e-6);
        verify(body, never()).logicalPose();
    }
    @Test void unresolvedPlotAndNonClientBodyCannotLeakPlotSpaceAudio() {
        var sable = mock(SableCompanion.class); var pos = new BlockPos(1, 2, 3);
        when(sable.isInPlotGrid(null, pos)).thenReturn(true);
        assertNull(ClientSpeakerSpatialResolver.resolveRender(sable, null, pos));
        when(sable.getContaining(null, pos)).thenReturn(mock(SubLevelAccess.class));
        assertNull(ClientSpeakerSpatialResolver.resolveRender(sable, null, pos));
        assertNull(ClientSpeakerSpatialResolver.resolveRenderFacing(sable, null, pos, 2));
    }
}
