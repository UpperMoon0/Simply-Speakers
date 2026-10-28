package com.nstut.mixin;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class ZonePreviewLevelMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method="renderLevel", at=@org.spongepowered.asm.mixin.injection.At("RETURN"))
    private void simplyspeakers$zonePreview(com.mojang.blaze3d.vertex.PoseStack pose, float partial, long finishTime, boolean outline,
            net.minecraft.client.Camera camera, net.minecraft.client.renderer.GameRenderer gameRenderer,
            net.minecraft.client.renderer.LightTexture light, org.joml.Matrix4f projection,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.nstut.simplyspeakers.client.DirectionalPreview.renderWorld(pose.last().pose(), projection, camera.getPosition());
    }
}
