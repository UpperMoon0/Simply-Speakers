package com.nstut.mixin;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.renderer.GameRenderer.class)
public abstract class ZonePreviewFrameProbeMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method="render",at=@org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void simplyspeakers$beginFrame(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.nstut.simplyspeakers.client.ZonePreviewRuntimeProbe.beginFrame();
    }
    @org.spongepowered.asm.mixin.injection.Inject(method="render",at=@org.spongepowered.asm.mixin.injection.At("RETURN"))
    private void simplyspeakers$endFrame(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.nstut.simplyspeakers.client.ZonePreviewRuntimeProbe.endFrame();
    }
}
