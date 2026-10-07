package com.nstut.simplyspeakers.compat.sable;

import dev.ryanhcode.sable.companion.*;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpeakerSpatialResolverTest {
    @Test void actualPoseTransformsTranslationRotationAndDirectionalFacing() {
        var sable = mock(SableCompanion.class); var body = mock(SubLevelAccess.class);
        var pose = new Pose3d(); pose.position().set(10, 20, 30); pose.orientation().rotateY(Math.PI / 2);
        when(body.logicalPose()).thenReturn(pose);
        var local = new Vec3(1, 2, 3); var pos = new BlockPos(1, 2, 3);
        when(sable.getContaining(null, local)).thenReturn(body);
        when(sable.getContaining(null, pos)).thenReturn(body);
        var world = SpeakerSpatialResolver.resolveLogical(sable, null, local);
        assertEquals(13, world.x, 1e-6); assertEquals(22, world.y, 1e-6); assertEquals(29, world.z, 1e-6);
        assertArrayEquals(new double[] {-1, 0}, SpeakerSpatialResolver.resolveLogicalFacing(sable, null, pos, 2), 1e-6);
        pose.position().set(100, 20, 30);
        assertEquals(103, SpeakerSpatialResolver.resolveLogical(sable, null, local).x, 1e-6);
    }
    @Test void absentCompatKeepsVanillaPositionButUnresolvedPlotMustBeSilent() {
        var sable = mock(SableCompanion.class); var local = new Vec3(1, 2, 3); var pos = new BlockPos(1, 2, 3);
        assertEquals(local, SpeakerSpatialResolver.resolveLogical(sable, null, local));
        when(sable.isInPlotGrid(null, local)).thenReturn(true);
        when(sable.isInPlotGrid(null, pos)).thenReturn(true);
        assertNull(SpeakerSpatialResolver.resolveLogical(sable, null, local));
        assertNull(SpeakerSpatialResolver.resolveLogicalFacing(sable, null, pos, 2));
    }
}
