package com.nstut.neoforge.simplyspeakers;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerRegistry;
import com.nstut.simplyspeakers.blocks.BlockRegistries;
import com.nstut.simplyspeakers.blocks.entities.BlockEntityRegistries;
import com.nstut.simplyspeakers.items.ItemRegistries;
import com.nstut.simplyspeakers.network.PacketRegistries;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;
import com.nstut.neoforge.simplyspeakers.config.ForgeConfig;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Path;

@Mod(SimplySpeakers.MOD_ID)
public final class SimplySpeakersForge {
    public SimplySpeakersForge(ModContainer container, IEventBus modEventBus) {
        // Register Forge config
        container.registerConfig(ModConfig.Type.COMMON, ForgeConfig.SPEC);

        // Register config event listener manually
        modEventBus.addListener(ForgeConfig::onLoad);

        SimplySpeakers.SOUND_EVENTS.register();
        SimplySpeakers.CREATIVE_TABS.register();
        BlockRegistries.BLOCKS.register();
        BlockEntityRegistries.BLOCK_ENTITIES.register();
        ItemRegistries.ITEMS.register();

        // Initialize packet registration
        PacketRegistries.init();

        // Register the server starting event
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        
        // Register the server stopping event
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        
        // Register the server tick event for periodic saving
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
    }

    @SuppressWarnings("null")
    public void onServerStarting(ServerStartingEvent event) {
        Path worldSavePath = event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        SimplySpeakers.initializeAudio(worldSavePath);
        SpeakerRegistry.init(worldSavePath);
    }
    
    public void onServerStopping(ServerStoppingEvent event) {
        SpeakerRegistry.saveRegistry();
    }
    
    public void onServerTick(ServerTickEvent.Post event) {
        // We only want to save periodically, not every tick
        // Save every 6000 ticks (5 minutes at 20 TPS)
        if (event.getServer().getTickCount() % 6000 == 0) {
            SpeakerRegistry.saveRegistry();
        }
    }
}