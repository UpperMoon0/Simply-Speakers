package com.nstut.mixin;

import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Covers explicit reload and vanilla's automatic default-device/loss recovery. */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Shadow private boolean loaded;

    @Inject(method = "destroy", at = @At("HEAD"))
    private void simplyspeakers$beforeContextDestroy(CallbackInfo callback) {
        ClientAudioPlayer.soundContextDestroying();
    }

    @Inject(method = "loadLibrary", at = @At("RETURN"))
    private void simplyspeakers$afterContextReady(CallbackInfo callback) {
        // loadLibrary catches initialization failures, so RETURN alone is not success.
        if (loaded) ClientAudioPlayer.soundContextReady();
    }
}
