package com.nstut.mixin;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.Minecraft.class)
public abstract class ZonePreviewReloadMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method="reloadResourcePacks", at=@org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void simplyspeakers$reloadPreview(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<?> ci) {
        com.nstut.simplyspeakers.client.DirectionalPreview.reload();
    }
}
