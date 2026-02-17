package com.nstut.neoforge.simplyspeakers.config;

import com.nstut.simplyspeakers.Config;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.event.config.ModConfigEvent;

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
        }
    }
}
