package com.nstut.simplyspeakers.forge.config;

import com.nstut.simplyspeakers.Config;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Forge-specific configuration handler.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class ForgeConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue SPEAKER_RANGE = BUILDER
            .comment("The range of the speaker block")
            .defineInRange("speakerRange", Config.speakerRange, Config.MIN_RANGE, Config.MAX_RANGE);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    /**
     * Handles the config loading event.
     * @param event The config event
     */
    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            Config.speakerRange = SPEAKER_RANGE.get();
        }
    }
}
