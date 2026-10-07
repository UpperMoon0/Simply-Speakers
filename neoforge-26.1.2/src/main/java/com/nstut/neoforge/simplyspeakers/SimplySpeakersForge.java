package com.nstut.neoforge.simplyspeakers;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.BlockRegistries;
import com.nstut.simplyspeakers.blocks.entities.BlockEntityRegistries;
import com.nstut.simplyspeakers.items.ItemRegistries;
import com.nstut.simplyspeakers.network.PacketRegistries;
import com.nstut.simplyspeakers.speakers.ServerPlaybackManager;
import com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;
import com.nstut.neoforge.simplyspeakers.config.ForgeConfig;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.nio.file.Path;

@Mod(SimplySpeakers.MOD_ID)
public final class SimplySpeakersForge {
    public SimplySpeakersForge(ModContainer container, IEventBus modEventBus) {
        // Register Forge config
        container.registerConfig(ModConfig.Type.COMMON, ForgeConfig.SPEC);

        // Register config event listener manually
        modEventBus.addListener(ForgeConfig::onLoad);

        SimplySpeakers.SOUND_EVENTS.register(modEventBus);
        SimplySpeakers.CREATIVE_TABS.register(modEventBus);
        BlockRegistries.BLOCKS.register(modEventBus);
        BlockEntityRegistries.BLOCK_ENTITIES.register(modEventBus);
        ItemRegistries.ITEMS.register(modEventBus);

        // Initialize packet registration (C2S only - server side receivers)
        PacketRegistries.init();

        // Register client setup event for S2C packet registration
        modEventBus.addListener(this::onClientSetup);

        // Run common setup (registers client events for volume updates)
        SimplySpeakers.init();

        // Register the server starting event
        // 0.8.x: /simplyspeakers command tree (operators only)
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent event) ->
                com.nstut.simplyspeakers.commands.SpeakerCommands.register(event.getDispatcher()));

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        
        // Register the server stopping event
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        
        // Register the server tick event for periodic saving
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        // Register S2C packet receivers on client side only
        PacketRegistries.registerS2C();
    }

    @SuppressWarnings("null")
    public void onServerStarting(ServerStartingEvent event) {
        Path worldSavePath = event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        SimplySpeakers.initializeAudio(worldSavePath);
        ServerSpeakerRegistry.init(worldSavePath);
    }
    
    public void onServerStopping(ServerStoppingEvent event) {
        ServerSpeakerRegistry.flushDirty();
        SimplySpeakers.shutdownAudio();
    }

    public void onServerStopped(ServerStoppedEvent event) {
        ServerPlaybackManager.resetForWorld();
        ServerSpeakerRegistry.resetForWorld();
    }

    public void onServerTick(ServerTickEvent.Post event) {
        ServerPlaybackManager.serverTick(event.getServer());
        // We only want to save periodically, not every tick
        // Save every 6000 ticks (5 minutes at 20 TPS)
        if (event.getServer().getTickCount() % 6000 == 0) {
            ServerSpeakerRegistry.flushDirty();
        }
    }
}
