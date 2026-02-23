package com.nstut.fabric.simplyspeakers.config;

import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

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
            
            // Read speaker range
            try {
                int range = Integer.parseInt(props.getProperty("speakerRange", "64"));
                Config.speakerRange = Math.max(Config.MIN_RANGE, Math.min(Config.MAX_RANGE, range));
            } catch (NumberFormatException e) {
                SimplySpeakers.LOGGER.error("Failed to parse speaker range from config", e);
            }

            // Read disable upload
            Config.disableUpload = Boolean.parseBoolean(props.getProperty("disableUpload", String.valueOf(Config.disableUpload)));

            // Read max upload size
            try {
                int size = Integer.parseInt(props.getProperty("maxUploadSize", String.valueOf(Config.maxUploadSize)));
                Config.maxUploadSize = Math.max(Config.MIN_UPLOAD_SIZE, Math.min(Config.MAX_UPLOAD_SIZE, size));
            } catch (NumberFormatException e) {
                SimplySpeakers.LOGGER.error("Failed to parse max upload size from config", e);
            }
            
            // Read debug logging
            Config.debugLogging = Boolean.parseBoolean(props.getProperty("debugLogging", String.valueOf(Config.debugLogging)));
            
            // Set logger level based on debug config
            // The logger name is "Simply Speakers" (as defined in SimplySpeakers.java)
            if (Config.debugLogging) {
                Configurator.setLevel("Simply Speakers", Level.DEBUG);
                SimplySpeakers.LOGGER.info("Debug logging enabled for Simply Speakers");
            }
            
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
            props.setProperty("debugLogging", String.valueOf(Config.debugLogging));
            
            props.store(writer, "Simply Speakers Configuration");
        } catch (IOException e) {
            SimplySpeakers.LOGGER.error("Failed to write config file", e);
        }
    }
}
