package com.nstut.simplyspeakers.fabric.platform;

import com.nstut.simplyspeakers.platform.services.IPlatformHelper;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric implementation of the platform helper.
 */
public class FabricPlatformHelper implements IPlatformHelper {
    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
    
    @Override
    public void init() {
        // Fabric-specific initialization
    }
}
