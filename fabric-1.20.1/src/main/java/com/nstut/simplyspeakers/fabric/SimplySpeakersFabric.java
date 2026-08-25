package com.nstut.simplyspeakers.fabric;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.fabric.config.FabricConfig;
import com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.nstut.simplyspeakers.blocks.entities.BlockEntityRegistries;
import com.nstut.simplyspeakers.fabric.compat.computercraft.SimplySpeakersPeripheral;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.nio.file.Path;

/**
 * Fabric mod initializer for Simply Speakers.
 */
public class SimplySpeakersFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // 0.8.x: /simplyspeakers command tree (operators only)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                com.nstut.simplyspeakers.commands.SpeakerCommands.register(dispatcher));

        // 0.8.x: CC:Tweaked peripheral support (optional dependency)
        if (FabricLoader.getInstance().isModLoaded("computercraft")) {
            dan200.computercraft.api.peripheral.PeripheralLookup.get().registerForBlockEntity(
                    (be, side) -> new SimplySpeakersPeripheral(be),
                    BlockEntityRegistries.SPEAKER.get());
        }
        // Load config
        FabricConfig.init();
        
        // Initialize the common elements of our mod
        SimplySpeakers.init();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            Path worldSavePath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            SimplySpeakers.initializeAudio(worldSavePath);
            ServerSpeakerRegistry.init(worldSavePath);
        });
        
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ServerSpeakerRegistry.saveRegistry();
            SimplySpeakers.shutdownAudio();
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ServerSpeakerRegistry.resetForWorld();
        });
        
        // Add periodic saving every 6000 ticks (5 minutes)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 6000 == 0) {
                ServerSpeakerRegistry.saveRegistry();
            }
        });
    }
}