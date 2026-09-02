package com.nstut.simplyspeakers.forge;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.speakers.ServerPlaybackManager;
import com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.nstut.simplyspeakers.forge.config.ForgeConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import java.nio.file.Path;

@Mod(SimplySpeakers.MOD_ID)
public final class SimplySpeakersForge {
    @SuppressWarnings("removal")
    public SimplySpeakersForge() {
        // Register Forge config
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ForgeConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ForgeConfig::onLoad);

        // Submit our event bus to let Architectury API register our content at the right time.
        EventBuses.registerModEventBus(SimplySpeakers.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        // Run our common setup.
        SimplySpeakers.init();

        // Register the server starting event
        // 0.8.x: /simplyspeakers command tree (operators only)
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent event) ->
                com.nstut.simplyspeakers.commands.SpeakerCommands.register(event.getDispatcher()));

        // 0.8.x: CC:Tweaked peripheral support (optional dependency)
        if (net.minecraftforge.fml.ModList.get().isLoaded("computercraft")) {
            com.nstut.simplyspeakers.forge.compat.computercraft.SimplySpeakersPeripheral.registerProvider();
        }

        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        
        // Register the server stopping event
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
        
        // Register the server tick event for periodic saving
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
    }

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
        if (net.minecraftforge.fml.ModList.get().isLoaded("computercraft")) {
            com.nstut.simplyspeakers.forge.compat.computercraft.SimplySpeakersPeripheral.reset();
        }
    }

    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ServerPlaybackManager.serverTick(event.getServer());
            // Flush pending registry writes every 6000 ticks (5 minutes at 20 TPS).
            if (event.getServer().getTickCount() % 6000 == 0) {
                ServerSpeakerRegistry.flushDirty();
            }
        }
    }
}