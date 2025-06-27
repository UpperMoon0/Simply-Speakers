package com.nstut.simplyspeakers.platform;

import com.nstut.simplyspeakers.platform.services.IPlatformHelper;
import com.nstut.simplyspeakers.platform.services.IItemHelper;
import com.nstut.simplyspeakers.platform.services.IClientHelper;

import java.util.ServiceLoader;

/**
 * Service provider for platform-specific implementations.
 */
public class Services {
    /**
     * Platform-specific helper implementation.
     */
    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);
    
    /**
     * Item helper implementation.
     */
    public static final IItemHelper ITEMS = load(IItemHelper.class);

    /**
     * Client helper implementation.
     */
    public static final IClientHelper CLIENT = load(IClientHelper.class);
    
    /**
     * Helper method to load a service for the appropriate platform.
     *
     * @param clazz The service class to load
     * @param <T> The type of the service
     * @return The loaded service
     */
    public static <T> T load(Class<T> clazz) {
        return ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
    }
}
