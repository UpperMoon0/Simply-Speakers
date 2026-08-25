package com.nstut.fabric.simplyspeakers;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.BlockRegistries;
import com.nstut.simplyspeakers.blocks.entities.BlockEntityRegistries;
import com.nstut.simplyspeakers.items.ItemRegistries;
import com.nstut.simplyspeakers.network.PacketRegistries;
import com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry;
import com.nstut.fabric.simplyspeakers.config.FabricConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.nstut.simplyspeakers.blocks.entities.BlockEntityRegistries;
import com.nstut.fabric.simplyspeakers.compat.computercraft.SimplySpeakersPeripheral;
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

        // IMPORTANT: Register DeferredRegisters directly here
        // This is required for Fabric to properly register content
        SimplySpeakers.SOUND_EVENTS.register();
        SimplySpeakers.CREATIVE_TABS.register();
        BlockRegistries.BLOCKS.register();
        BlockEntityRegistries.BLOCK_ENTITIES.register();
        ItemRegistries.ITEMS.register();

        // Initialize packet registration
        PacketRegistries.init();

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