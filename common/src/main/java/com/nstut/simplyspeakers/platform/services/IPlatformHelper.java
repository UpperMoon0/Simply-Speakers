package com.nstut.simplyspeakers.platform.services;

/**
 * Platform-specific helper methods.
 */
public interface IPlatformHelper {
    /**
     * Gets the name of the current platform.
     *
     * @return The name of the current platform.
     */
    String getPlatformName();

    /**
     * Checks if a mod with the given id is loaded.
     *
     * @param modId The mod to check if it is loaded.
     * @return True if the mod is loaded, false otherwise.
     */
    boolean isModLoaded(String modId);

    /**
     * Check if the game is currently in a development environment.
     *
     * @return True if in a development environment, false otherwise.
     */
    boolean isDevelopmentEnvironment();
    
    /**
     * Platform-specific initialization.
     */
    void init();
}
