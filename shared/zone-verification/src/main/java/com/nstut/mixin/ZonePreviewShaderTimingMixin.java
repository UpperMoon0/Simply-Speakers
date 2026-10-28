package com.nstut.mixin;

@org.spongepowered.asm.mixin.Mixin(value=com.nstut.simplyspeakers.client.DirectionalPreview.class,remap=false)
public abstract class ZonePreviewShaderTimingMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method="renderWorld",at=@org.spongepowered.asm.mixin.injection.At("HEAD"))
    private static void simplyspeakers$begin(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.nstut.simplyspeakers.client.ZonePreviewRuntimeProbe.beginPreview(0);
    }
    @org.spongepowered.asm.mixin.injection.Inject(method="renderWorld",at=@org.spongepowered.asm.mixin.injection.At("RETURN"))
    private static void simplyspeakers$end(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.nstut.simplyspeakers.client.ZonePreviewRuntimeProbe.endPreview(0);
    }
}
