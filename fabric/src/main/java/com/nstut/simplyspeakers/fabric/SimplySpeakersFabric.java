package com.nstut.simplyspeakers.fabric;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerRegistry;
import com.nstut.simplyspeakers.fabric.config.FabricConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.nio.file.Path;

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

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            Path worldSavePath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            SimplySpeakers.initializeAudio(worldSavePath);
            SpeakerRegistry.init(worldSavePath);
        });
        
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SpeakerRegistry.saveRegistry();
        });
    }
}