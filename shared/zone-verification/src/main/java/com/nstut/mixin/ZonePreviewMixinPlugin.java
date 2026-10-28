package com.nstut.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Keep opt-in capture/timing hooks out of ordinary client transformations. */
public final class ZonePreviewMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> VERIFICATION = Set.of(
        "com.nstut.mixin.ZonePreviewFrameProbeMixin", "com.nstut.mixin.ZonePreviewOffscreenMixin",
        "com.nstut.mixin.ZonePreviewShaderTimingMixin", "com.nstut.mixin.ZonePreviewParticleTimingMixin");

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return !VERIFICATION.contains(mixin) || Boolean.getBoolean("simplyspeakers.zoneShaderAcceptance");
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }
    @Override public void acceptTargets(Set<String> ours, Set<String> others) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
