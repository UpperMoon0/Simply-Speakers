package com.nstut.simplyspeakers;

import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

/**
 * Registry system for tracking speakers by their IDs and managing their state.
 * This eliminates the need for expensive block scanning operations and provides
 * persistent state management.
 */
public class SpeakerRegistry {
    // Map of speaker ID to set of speaker positions
    private static final Map<String, Set<BlockPos>> speakerPositions = new ConcurrentHashMap<>();
    
    // Map of speaker ID to set of proxy speaker positions
    private static final Map<String, Set<BlockPos>> proxySpeakerPositions = new ConcurrentHashMap<>();
    
    // Map of level to map of position to speaker ID
    private static final Map<Level, Map<BlockPos, String>> levelSpeakerIds = new ConcurrentHashMap<>();
    
    // Map of speaker ID to speaker state (the centralized state storage)
    private static final Map<String, SpeakerState> speakerStates = new ConcurrentHashMap<>();
    
    // Gson instance for serialization
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // Registry file path
    private static Path registryFilePath;
    
    /**
     * Initializes the speaker registry with the world save path.
     *
     * @param worldSavePath The path to the world save directory
     */
    public static void init(Path worldSavePath) {
        registryFilePath = worldSavePath.resolve("speaker_registry.json");
        loadRegistry();
    }
    
    /**
     * Saves the speaker registry to disk.
     */
    public static void saveRegistry() {
        if (registryFilePath == null) return;
        
        try {
            // Create a copy of the speaker states for serialization
            Map<String, SpeakerState> statesToSave = new ConcurrentHashMap<>();
            for (Map.Entry<String, SpeakerState> entry : speakerStates.entrySet()) {
                statesToSave.put(entry.getKey(), entry.getValue().copy());
            }
            
            String json = GSON.toJson(statesToSave);
            File file = registryFilePath.toFile();
            file.getParentFile().mkdirs(); // Ensure directory exists
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(json);
            }
            SimplySpeakers.LOGGER.info("Saved speaker registry to {}", registryFilePath);
        } catch (IOException e) {
            SimplySpeakers.LOGGER.error("Failed to save speaker registry", e);
        }
    }
    
    /**
     * Loads the speaker registry from disk.
     */
    public static void loadRegistry() {
        if (registryFilePath == null) return;
        
        try {
            File file = registryFilePath.toFile();
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    Type type = new TypeToken<Map<String, SpeakerState>>(){}.getType();
                    Map<String, SpeakerState> loadedStates = GSON.fromJson(reader, type);
                    if (loadedStates != null) {
                        speakerStates.clear();
                        speakerStates.putAll(loadedStates);
                        SimplySpeakers.LOGGER.info("Loaded speaker registry with {} entries", loadedStates.size());
                    }
                }
            } else {
                SimplySpeakers.LOGGER.info("No existing speaker registry file found, starting with empty registry");
            }
        } catch (Exception e) {
            SimplySpeakers.LOGGER.error("Failed to load speaker registry", e);
        }
    }
    
    /**
     * Gets the state for a speaker ID, creating a new one if it doesn't exist.
     *
     * @param speakerId The speaker ID
     * @return The speaker state
     */
    public static SpeakerState getOrCreateSpeakerState(String speakerId) {
        return speakerStates.computeIfAbsent(speakerId, k -> new SpeakerState());
    }
    
    /**
     * Gets the state for a speaker ID, or null if it doesn't exist.
     *
     * @param speakerId The speaker ID
     * @return The speaker state or null
     */
    public static SpeakerState getSpeakerState(String speakerId) {
        return speakerStates.get(speakerId);
    }
    
    /**
     * Updates the state for a speaker ID.
     *
     * @param speakerId The speaker ID
     * @param state The new state
     */
    public static void updateSpeakerState(String speakerId, SpeakerState state) {
        speakerStates.put(speakerId, state.copy());
        saveRegistry(); // Persist changes immediately
    }
    
    /**
     * Removes the state for a speaker ID.
     *
     * @param speakerId The speaker ID
     */
    public static void removeSpeakerState(String speakerId) {
        speakerStates.remove(speakerId);
        saveRegistry(); // Persist changes immediately
    }
    
    /**
     * Registers a speaker with the registry.
     *
     * @param level The level the speaker is in
     * @param pos The position of the speaker
     * @param speakerId The speaker ID
     */
    public static void registerSpeaker(Level level, BlockPos pos, String speakerId) {
        if (level.isClientSide()) return;
        
        // Add to speaker positions map
        speakerPositions.computeIfAbsent(speakerId, k -> ConcurrentHashMap.newKeySet()).add(pos);
        
        // Add to level tracking map
        levelSpeakerIds.computeIfAbsent(level, k -> new ConcurrentHashMap<>()).put(pos, speakerId);
        
        SimplySpeakers.LOGGER.debug("Registered speaker at {} with ID {}", pos, speakerId);
    }
    
    /**
     * Registers a proxy speaker with the registry.
     *
     * @param level The level the proxy speaker is in
     * @param pos The position of the proxy speaker
     * @param speakerId The speaker ID
     */
    public static void registerProxySpeaker(Level level, BlockPos pos, String speakerId) {
        if (level.isClientSide()) return;
        
        // Add to proxy speaker positions map
        proxySpeakerPositions.computeIfAbsent(speakerId, k -> ConcurrentHashMap.newKeySet()).add(pos);
        
        // Add to level tracking map
        levelSpeakerIds.computeIfAbsent(level, k -> new ConcurrentHashMap<>()).put(pos, speakerId);
        
        SimplySpeakers.LOGGER.debug("Registered proxy speaker at {} with ID {}", pos, speakerId);
    }
    
    /**
     * Unregisters a speaker from the registry.
     *
     * @param level The level the speaker is in
     * @param pos The position of the speaker
     * @param speakerId The speaker ID
     */
    public static void unregisterSpeaker(Level level, BlockPos pos, String speakerId) {
        if (level.isClientSide()) return;
        
        // Remove from speaker positions map
        Set<BlockPos> speakers = speakerPositions.get(speakerId);
        if (speakers != null) {
            speakers.remove(pos);
            if (speakers.isEmpty()) {
                speakerPositions.remove(speakerId);
                // Also remove the state when no speakers are left with this ID
                removeSpeakerState(speakerId);
            }
        }
        
        // Remove from level tracking map
        Map<BlockPos, String> levelMap = levelSpeakerIds.get(level);
        if (levelMap != null) {
            levelMap.remove(pos);
            if (levelMap.isEmpty()) {
                levelSpeakerIds.remove(level);
            }
        }
        
        SimplySpeakers.LOGGER.debug("Unregistered speaker at {} with ID {}", pos, speakerId);
    }
    
    /**
     * Unregisters a proxy speaker from the registry.
     *
     * @param level The level the proxy speaker is in
     * @param pos The position of the proxy speaker
     * @param speakerId The speaker ID
     */
    public static void unregisterProxySpeaker(Level level, BlockPos pos, String speakerId) {
        if (level.isClientSide()) return;
        
        // Remove from proxy speaker positions map
        Set<BlockPos> proxySpeakers = proxySpeakerPositions.get(speakerId);
        if (proxySpeakers != null) {
            proxySpeakers.remove(pos);
            if (proxySpeakers.isEmpty()) {
                proxySpeakerPositions.remove(speakerId);
            }
        }
        
        // Remove from level tracking map
        Map<BlockPos, String> levelMap = levelSpeakerIds.get(level);
        if (levelMap != null) {
            levelMap.remove(pos);
            if (levelMap.isEmpty()) {
                levelSpeakerIds.remove(level);
            }
        }
        
        SimplySpeakers.LOGGER.debug("Unregistered proxy speaker at {} with ID {}", pos, speakerId);
    }
    
    /**
     * Updates the speaker ID for a registered speaker.
     *
     * @param level The level the speaker is in
     * @param pos The position of the speaker
     * @param oldSpeakerId The old speaker ID
     * @param newSpeakerId The new speaker ID
     */
    public static void updateSpeakerId(Level level, BlockPos pos, String oldSpeakerId, String newSpeakerId) {
        if (level.isClientSide()) return;
        
        // Unregister with old ID
        unregisterSpeaker(level, pos, oldSpeakerId);
        
        // Register with new ID
        registerSpeaker(level, pos, newSpeakerId);
        
        SimplySpeakers.LOGGER.debug("Updated speaker ID at {} from {} to {}", pos, oldSpeakerId, newSpeakerId);
    }
    
    /**
     * Updates the speaker ID for a registered proxy speaker.
     *
     * @param level The level the proxy speaker is in
     * @param pos The position of the proxy speaker
     * @param oldSpeakerId The old speaker ID
     * @param newSpeakerId The new speaker ID
     */
    public static void updateProxySpeakerId(Level level, BlockPos pos, String oldSpeakerId, String newSpeakerId) {
        if (level.isClientSide()) return;
        
        // Unregister with old ID
        unregisterProxySpeaker(level, pos, oldSpeakerId);
        
        // Register with new ID
        registerProxySpeaker(level, pos, newSpeakerId);
        
        SimplySpeakers.LOGGER.debug("Updated proxy speaker ID at {} from {} to {}", pos, oldSpeakerId, newSpeakerId);
    }
    
    /**
     * Gets all speaker positions with the specified speaker ID.
     *
     * @param speakerId The speaker ID to look for
     * @return Set of positions of speakers with the specified ID
     */
    public static Set<BlockPos> getSpeakerPositions(String speakerId) {
        Set<BlockPos> positions = speakerPositions.get(speakerId);
        return positions != null ? new HashSet<>(positions) : new HashSet<>();
    }
    
    /**
     * Gets all proxy speaker positions with the specified speaker ID.
     *
     * @param speakerId The speaker ID to look for
     * @return Set of positions of proxy speakers with the specified ID
     */
    public static Set<BlockPos> getProxySpeakerPositions(String speakerId) {
        Set<BlockPos> positions = proxySpeakerPositions.get(speakerId);
        return positions != null ? new HashSet<>(positions) : new HashSet<>();
    }
    
    /**
     * Clears all registry data for a level. Should be called when a level is unloaded.
     *
     * @param level The level to clear
     */
    public static void clearLevel(Level level) {
        if (level.isClientSide()) return;
        
        // Remove all entries for this level from the level tracking map
        levelSpeakerIds.remove(level);
        
        // Note: We don't remove from speakerPositions and proxySpeakerPositions here
        // because those are global maps that might be used by other levels
        // In a more sophisticated implementation, we might want to track which levels
        // each speaker is associated with
    }
}