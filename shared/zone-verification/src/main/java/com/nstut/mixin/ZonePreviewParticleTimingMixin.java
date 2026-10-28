package com.nstut.mixin;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.particle.ParticleEngine.class)
public abstract class ZonePreviewParticleTimingMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method="render",at=@org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void simplyspeakers$begin(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.nstut.simplyspeakers.client.ZonePreviewRuntimeProbe.beginPreview(1);
    }
    @org.spongepowered.asm.mixin.injection.Inject(method="render",at=@org.spongepowered.asm.mixin.injection.At("RETURN"))
    private void simplyspeakers$end(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.nstut.simplyspeakers.client.ZonePreviewRuntimeProbe.endPreview(1);
    }
}
