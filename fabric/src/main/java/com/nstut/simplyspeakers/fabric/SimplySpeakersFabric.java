package com.nstut.simplyspeakers.fabric;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.fabric.config.FabricConfig;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric mod initializer for Simply Speakers.
 */
public class SimplySpeakersFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // Load config
        FabricConfig.init();
        
        // Initialize the common elements of our mod
        SimplySpeakers.init();
    }
}
