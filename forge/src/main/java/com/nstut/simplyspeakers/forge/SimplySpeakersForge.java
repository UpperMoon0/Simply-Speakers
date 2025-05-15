package com.nstut.simplyspeakers.forge;

import com.nstut.simplyspeakers.SimplySpeakers;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.nstut.simplyspeakers.forge.config.ForgeConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

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
    }
}