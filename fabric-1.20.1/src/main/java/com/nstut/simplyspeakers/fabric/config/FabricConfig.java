package com.nstut.simplyspeakers.fabric.config;

import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

/**
 * Fabric-specific configuration handler.
 */
public class FabricConfig {
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve(SimplySpeakers.MOD_ID + ".properties").toFile();
    
    /**
     * Initializes the Fabric config.
     */
    public static void init() {
        if (!CONFIG_FILE.exists()) {
            writeConfig();
        } else {
            readConfig();
        }
    }
    
    private static void readConfig() {
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            Properties props = new Properties();
            props.load(reader);
            
            int range = 64;
            // Read speaker range
            try {
                range = Integer.parseInt(props.getProperty("speakerRange", "64"));
            } catch (NumberFormatException e) {
                SimplySpeakers.LOGGER.error("Failed to parse speaker range from config", e);
            }

            // Read disable upload
            boolean disableUpload = Boolean.parseBoolean(props.getProperty("disableUpload", String.valueOf(Config.isLocalDisableUpload())));

            // Read max upload size
            int size = 5 * 1024 * 1024;
            try {
                size = Integer.parseInt(props.getProperty("maxUploadSize", String.valueOf(Config.getLocalMaxUploadSize())));
            } catch (NumberFormatException e) {
                SimplySpeakers.LOGGER.error("Failed to parse max upload size from config", e);
            }

            // Read allow remote streams
            boolean allowRemoteStreams = Boolean.parseBoolean(props.getProperty("allowRemoteStreams", String.valueOf(Config.isLocalAllowRemoteStreams())));

            Config.setLocalConfig(range, disableUpload, size, allowRemoteStreams);
            
        } catch (IOException e) {
            SimplySpeakers.LOGGER.error("Failed to read config file", e);
            writeConfig();
        }
    }
    
    private static void writeConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            Properties props = new Properties();
            props.setProperty("speakerRange", "64");
            props.setProperty("disableUpload", String.valueOf(Config.disableUpload));
            props.setProperty("maxUploadSize", String.valueOf(Config.maxUploadSize));
            props.setProperty("allowRemoteStreams", String.valueOf(Config.allowRemoteStreams));
            
            props.store(writer, "Simply Speakers Configuration");
        } catch (IOException e) {
            SimplySpeakers.LOGGER.error("Failed to write config file", e);
        }
    }
}
