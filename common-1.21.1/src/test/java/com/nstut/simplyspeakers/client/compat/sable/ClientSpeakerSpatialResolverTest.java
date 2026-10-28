package com.nstut.simplyspeakers.client.compat.sable;

import dev.ryanhcode.sable.companion.*;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientSpeakerSpatialResolverTest {
    @Test void retainedPoseMatchesAllocatingResolverAcrossMovingScaledBodyAndOrdinaryWorld() {
        var sable=mock(SableCompanion.class);
        var body=mock(ClientSubLevelAccess.class);
        var transform=new Pose3d();
        transform.position().set(10,20,30);
        transform.rotationPoint().set(2,3,4);
        transform.scale().set(2,3,4);
        transform.orientation().rotateY(.7).rotateX(.2);
        when(body.renderPose()).thenReturn(transform);
        var block=new BlockPos(1,2,3);
        var continuous=new net.minecraft.world.phys.Vec3(1.1,2.2,3.3);
        var destination=new ClientSpeakerSpatialResolver.RenderPose();
        var position=destination.position;var facing=destination.facing;
        for(Object source:new Object[]{block,continuous}) {
            if(source instanceof BlockPos b) when(sable.getContaining(null,b)).thenReturn(body);
            else when(sable.getContaining(null,(net.minecraft.core.Position)source)).thenReturn(body);
            for(int frame=0;frame<100;frame++) {
                transform.position().add(.1,.2,.3);
                assertTrue(source instanceof BlockPos b
                    ? ClientSpeakerSpatialResolver.resolveRenderPose(sable,null,b,.6,.8,destination)
                    : ClientSpeakerSpatialResolver.resolveRenderPose(sable,null,(net.minecraft.core.Position)source,.6,.8,destination));
                var expected=source instanceof BlockPos b
                    ? ClientSpeakerSpatialResolver.resolveRender(sable,null,b)
                    : ClientSpeakerSpatialResolver.resolveRender(sable,null,(net.minecraft.core.Position)source);
                var normal=transform.transformNormal(new net.minecraft.world.phys.Vec3(.6,0,.8));
                var expectedFacing=com.nstut.simplyspeakers.audio.DirectionalAudio.normalize(normal.x,normal.z);
                assertEquals(expected.x,destination.position.x,1e-9);
                assertEquals(expected.y,destination.position.y,1e-9);
                assertEquals(expected.z,destination.position.z,1e-9);
                assertEquals(expectedFacing[0],destination.facing.x,1e-9);
                assertEquals(expectedFacing[1],destination.facing.z,1e-9);
                assertSame(position,destination.position);assertSame(facing,destination.facing);
            }
        }
        when(sable.getContaining(null,block)).thenReturn(null);
        assertTrue(ClientSpeakerSpatialResolver.resolveRenderPose(sable,null,block,0,0,destination));
        assertEquals(1.5,destination.position.x);assertEquals(2.5,destination.position.y);assertEquals(3.5,destination.position.z);
        assertEquals(-1,destination.facing.z);
        when(sable.isInPlotGrid(null,block)).thenReturn(true);
        assertFalse(ClientSpeakerSpatialResolver.resolveRenderPose(sable,null,block,0,-1,destination));
        verify(body,never()).logicalPose();
    }
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
