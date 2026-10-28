package com.nstut.simplyspeakers.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.nstut.simplyspeakers.audio.ZonePreviewMesh;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ZonePreviewRendererTest {
    @Test void replacingAndClearingPreviewClosesEveryOwnedBufferExactlyOnce() {
        var device=mock(GpuDevice.class);
        var compiled=mock(CompiledRenderPipeline.class);
        when(compiled.isValid()).thenReturn(true);
        when(device.precompilePipeline(any(RenderPipeline.class))).thenReturn(compiled);
        GpuBuffer vertices=mock(GpuBuffer.class),visible=mock(GpuBuffer.class),hidden=mock(GpuBuffer.class);
        when(device.createBuffer(any(),anyInt(),any(ByteBuffer.class))).thenReturn(vertices);
        when(device.createBuffer(any(),anyInt(),eq(112L))).thenReturn(visible,hidden);
        try(var renderSystem=mockStatic(RenderSystem.class)) {
            renderSystem.when(RenderSystem::getDevice).thenReturn(device);
            var renderer=new ZonePreviewRenderer();
            renderer.upload(ZonePreviewMesh.create(512,1,1,90,.9f,2));
            renderer.close(); renderer.close();
            verify(vertices).close(); verify(visible).close(); verify(hidden).close();
        }
    }

    @Test void shaderFailureFallsBackAndReloadPermitsANewAttempt() {
        var device=mock(GpuDevice.class);
        var compiled=mock(CompiledRenderPipeline.class);
        when(device.precompilePipeline(any(RenderPipeline.class))).thenReturn(compiled);
        when(compiled.isValid()).thenReturn(false);
        try(var renderSystem=mockStatic(RenderSystem.class)) {
            renderSystem.when(RenderSystem::getDevice).thenReturn(device);
            var renderer=new ZonePreviewRenderer();
            renderer.upload(ZonePreviewMesh.create(16,1,1,90,.9f,2));
            assertFalse(renderer.available());
            verify(device,never()).createBuffer(any(),anyInt(),any(ByteBuffer.class));
            renderer.reload();
            assertTrue(renderer.available());
        }
    }

    @Test void explicitHudFallbackDoesNotTouchTheGpu() {
        System.setProperty("simplyspeakers.previewHudOnly","true");
        try(var renderSystem=mockStatic(RenderSystem.class)) {
            var renderer=new ZonePreviewRenderer();
            renderer.upload(ZonePreviewMesh.create(512,1,1,90,.9f,2));
            assertFalse(renderer.available());
            renderSystem.verifyNoInteractions();
        } finally { System.clearProperty("simplyspeakers.previewHudOnly"); }
    }
}
