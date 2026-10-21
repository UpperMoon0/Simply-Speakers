package com.nstut.simplyspeakers.fabric;
import net.fabricmc.api.ModInitializer;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.LegacyLifecycle;
import com.nstut.simplyspeakers.fabric.config.FabricConfig;
public final class SimplySpeakersFabric implements ModInitializer {
    @Override public void onInitialize() {
        FabricConfig.init();
        SimplySpeakers.init();
        LegacyLifecycle.register();
    }
}
