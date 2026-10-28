package com.nstut.simplyspeakers.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.nstut.mixin.ZonePreviewMixinPlugin;

class ZonePreviewMixinPluginTest {
    @Test void verificationHooksRequireExplicitCaptureFlagWhileProductionHooksRemain() {
        String key="simplyspeakers.zoneShaderAcceptance", previous=System.getProperty(key);
        var plugin=new ZonePreviewMixinPlugin();
        try {
            System.clearProperty(key);
            for(String name:new String[]{"ZonePreviewFrameProbeMixin","ZonePreviewOffscreenMixin",
                    "ZonePreviewShaderTimingMixin","ZonePreviewParticleTimingMixin"}) {
                assertFalse(plugin.shouldApplyMixin("unused","com.nstut.mixin."+name));
                System.setProperty(key,"true");
                assertTrue(plugin.shouldApplyMixin("unused","com.nstut.mixin."+name));
                System.clearProperty(key);
            }
            assertTrue(plugin.shouldApplyMixin("unused","com.nstut.mixin.ZonePreviewLevelMixin"));
            assertTrue(plugin.shouldApplyMixin("unused","com.nstut.mixin.ZonePreviewReloadMixin"));
            assertTrue(plugin.shouldApplyMixin("unused","com.nstut.mixin.SoundEngineMixin"));
        } finally {
            if(previous==null) System.clearProperty(key); else System.setProperty(key,previous);
        }
    }
}
