package com.nstut.fabric.simplyspeakers;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerRegistry;
import com.nstut.simplyspeakers.blocks.BlockRegistries;
import com.nstut.simplyspeakers.blocks.entities.BlockEntityRegistries;
import com.nstut.simplyspeakers.items.ItemRegistries;
import com.nstut.fabric.simplyspeakers.config.FabricConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;

/**
 * Fabric mod initializer for Simply Speakers.
 */
public class SimplySpeakersFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // Load config
        FabricConfig.init();

        // IMPORTANT: Register DeferredRegisters directly here
        // This is required for Fabric to properly register content
        SimplySpeakers.SOUND_EVENTS.register();
        SimplySpeakers.CREATIVE_TABS.register();
        BlockRegistries.BLOCKS.register();
        BlockEntityRegistries.BLOCK_ENTITIES.register();
        ItemRegistries.ITEMS.register();

        // NOTE: Do NOT call PacketRegistries.init() here!
        // Architectury's transformer automatically registers the NetworkChannel
        // when it's created. Calling init() would cause double registration.

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            Path worldSavePath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            SimplySpeakers.initializeAudio(worldSavePath);
            SpeakerRegistry.init(worldSavePath);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SpeakerRegistry.saveRegistry();
        });

        // Add periodic saving every 6000 ticks (5 minutes)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 6000 == 0) {
                SpeakerRegistry.saveRegistry();
            }
        });
    }
}