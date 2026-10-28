package com.nstut.mixin;

/** Capture the exact projection after vanilla applies bob, hurt and portal effects. */
@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.renderer.GameRenderer.class)
public abstract class ZonePreviewLevelMixin {
    @org.spongepowered.asm.mixin.Unique private org.joml.Matrix4f simplyspeakers$projection;
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method="renderLevel", at=@org.spongepowered.asm.mixin.injection.At("STORE"), ordinal=0)
    private org.joml.Matrix4f simplyspeakers$captureProjection(org.joml.Matrix4f matrix) {
        simplyspeakers$projection=matrix;
        return matrix;
    }
    @org.spongepowered.asm.mixin.injection.Inject(method="renderLevel", at=@org.spongepowered.asm.mixin.injection.At(
        value="INVOKE",target="Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZLnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V",shift=org.spongepowered.asm.mixin.injection.At.Shift.AFTER))
    private void simplyspeakers$drawZone(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        var renderer=(net.minecraft.client.renderer.GameRenderer)(Object)this;
        var camera=renderer.getGameRenderState().levelRenderState.cameraRenderState;
        if(simplyspeakers$projection!=null)
            com.nstut.simplyspeakers.client.DirectionalPreview.renderWorld(camera.viewRotationMatrix,simplyspeakers$projection,camera.pos);
        simplyspeakers$projection=null;
    }
}
