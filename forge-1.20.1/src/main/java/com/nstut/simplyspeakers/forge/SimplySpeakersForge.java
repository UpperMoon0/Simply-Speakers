package com.nstut.simplyspeakers.forge;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerRegistry;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.nstut.simplyspeakers.forge.config.ForgeConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import java.nio.file.Path;

@Mod(SimplySpeakers.MOD_ID)
public final class SimplySpeakersForge {
    @SuppressWarnings("removal")
    public SimplySpeakersForge() {
        // Register Forge config
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ForgeConfig.SPEC);

        // Submit our event bus to let Architectury API register our content at the right time.
        EventBuses.registerModEventBus(SimplySpeakers.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        // Run our common setup.
        SimplySpeakers.init();

        // Register the server starting event
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        
        // Register the server stopping event
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
        
        // Register the server tick event for periodic saving
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
    }

    public void onServerStarting(ServerStartingEvent event) {
        Path worldSavePath = event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        SimplySpeakers.initializeAudio(worldSavePath);
        SpeakerRegistry.init(worldSavePath);
    }
    
    public void onServerStopping(ServerStoppingEvent event) {
        SpeakerRegistry.saveRegistry();
    }
    
    public void onServerTick(TickEvent.ServerTickEvent event) {
        // We only want to save periodically, not every tick
        if (event.phase == TickEvent.Phase.END) {
            // Save every 6000 ticks (5 minutes at 20 TPS)
            if (event.getServer().getTickCount() % 6000 == 0) {
                SpeakerRegistry.saveRegistry();
            }
        }
    }
}