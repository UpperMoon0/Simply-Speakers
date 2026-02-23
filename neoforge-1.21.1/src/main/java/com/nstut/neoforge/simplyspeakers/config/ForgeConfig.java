package com.nstut.neoforge.simplyspeakers.config;

import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.event.config.ModConfigEvent;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * Forge-specific configuration handler.
 */
public class ForgeConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue SPEAKER_RANGE = BUILDER
            .comment("The max range of the speaker block")
            .defineInRange("speakerRange", 64, Config.MIN_RANGE, Config.MAX_RANGE);

    public static final ModConfigSpec.BooleanValue DISABLE_UPLOAD = BUILDER
            .comment("Whether to disable audio uploads")
            .define("disableUpload", Config.disableUpload);

    public static final ModConfigSpec.IntValue MAX_UPLOAD_SIZE = BUILDER
            .comment("The maximum upload size in bytes")
            .defineInRange("maxUploadSize", Config.maxUploadSize, Config.MIN_UPLOAD_SIZE, Config.MAX_UPLOAD_SIZE);

    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER
            .comment("Enable debug logging for troubleshooting audio/settings issues")
            .define("debugLogging", Config.debugLogging);

    public static final ModConfigSpec SPEC = BUILDER.build();

    /**
     * Handles the config loading event.
     * @param event The config event
     */
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
