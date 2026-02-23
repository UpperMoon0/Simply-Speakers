package com.nstut.simplyspeakers.forge.config;

import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * Forge-specific configuration handler.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class ForgeConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue SPEAKER_RANGE = BUILDER
            .comment("The max range of the speaker block")
            .defineInRange("speakerRange", 64, Config.MIN_RANGE, Config.MAX_RANGE);

    public static final ForgeConfigSpec.BooleanValue DISABLE_UPLOAD = BUILDER
            .comment("Whether to disable audio uploads")
            .define("disableUpload", Config.disableUpload);

    public static final ForgeConfigSpec.IntValue MAX_UPLOAD_SIZE = BUILDER
            .comment("The maximum upload size in bytes")
            .defineInRange("maxUploadSize", Config.maxUploadSize, Config.MIN_UPLOAD_SIZE, Config.MAX_UPLOAD_SIZE);

    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER
            .comment("Enable debug logging for troubleshooting audio/settings issues")
            .define("debugLogging", Config.debugLogging);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    /**
     * Handles the config loading event.
     * @param event The config event
     */
    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            Config.speakerRange = SPEAKER_RANGE.get();
            Config.disableUpload = DISABLE_UPLOAD.get();
            Config.maxUploadSize = MAX_UPLOAD_SIZE.get();
            Config.debugLogging = DEBUG_LOGGING.get();
            
            // Set logger level based on debug config
            // The logger name is the MOD_ID ("simplyspeakers")
            if (Config.debugLogging) {
                Configurator.setLevel(SimplySpeakers.MOD_ID, Level.DEBUG);
                SimplySpeakers.LOGGER.info("Debug logging enabled for Simply Speakers");
            } else {
                Configurator.setLevel(SimplySpeakers.MOD_ID, Level.INFO);
            }
        }
    }
}
