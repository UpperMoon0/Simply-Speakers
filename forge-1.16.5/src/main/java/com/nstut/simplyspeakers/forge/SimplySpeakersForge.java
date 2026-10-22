package com.nstut.simplyspeakers.forge;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.LegacyLifecycle;
import com.nstut.simplyspeakers.forge.config.ForgeConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import me.shedaniel.architectury.platform.forge.EventBuses;
@Mod("simplyspeakers")
public final class SimplySpeakersForge {
    public SimplySpeakersForge() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ForgeConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ForgeConfig::onLoad);
        EventBuses.registerModEventBus(SimplySpeakers.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        SimplySpeakers.init();
        LegacyLifecycle.register();
    }
}
