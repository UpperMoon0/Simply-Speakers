package com.nstut.simplyspeakers.speakers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerLink;
import com.nstut.simplyspeakers.SpeakerState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative server-side speaker registry.
 * Dimension-safe, isolated per world lifecycle, and protected against state collisions.
 */
public final class ServerSpeakerRegistry {

    private static final Map<String, Set<BlockPos>> speakerPositions = new ConcurrentHashMap<>();
    private static final Map<String, Set<BlockPos>> proxySpeakerPositions = new ConcurrentHashMap<>();
    private static final Map<SpeakerLocation, String> posToStateKey = new ConcurrentHashMap<>();
    private static final Map<String, SpeakerState> speakerStates = new ConcurrentHashMap<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path registryFilePath;

    private ServerSpeakerRegistry() {
    }

    public static String getDimension(Level level) {
        if (level == null) return "minecraft:overworld";
        return level.dimension().identifier().toString();
    }

    public static String getRegistryKey(String dimension, String stateKey) {
        return dimension + "/" + (stateKey != null ? stateKey : "");
    }

    public static String getRegistryKey(Level level, String stateKey) {
        return getRegistryKey(getDimension(level), stateKey);
    }

    public static synchronized void resetForWorld() {
        speakerPositions.clear();
        proxySpeakerPositions.clear();
        posToStateKey.clear();
        speakerStates.clear();
        registryFilePath = null;
        SimplySpeakers.LOGGER.debug("SERVER: Reset ServerSpeakerRegistry state for world.");
    }

    public static synchronized void init(Path worldSavePath) {
        resetForWorld();
        registryFilePath = worldSavePath.resolve("speaker_registry.json");
        loadRegistry();
    }

    public static synchronized void saveRegistry() {
        if (registryFilePath == null) return;

        Path tmpPath = registryFilePath.resolveSibling("speaker_registry.json.tmp");
        try {
            Map<String, SpeakerState> statesToSave = new HashMap<>();
            for (Map.Entry<String, SpeakerState> entry : speakerStates.entrySet()) {
                statesToSave.put(entry.getKey(), entry.getValue().copy());
            }

            String json = GSON.toJson(statesToSave);
            Files.createDirectories(registryFilePath.getParent());
            Files.writeString(tmpPath, json);
            Files.move(tmpPath, registryFilePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            SimplySpeakers.LOGGER.debug("SERVER: Saved speaker registry to {}", registryFilePath);
        } catch (IOException e) {
            SimplySpeakers.LOGGER.error("SERVER: Failed to save speaker registry", e);
            try {
                Files.deleteIfExists(tmpPath);
            } catch (IOException ignored) {}
        }
    }

    public static synchronized void loadRegistry() {
        if (registryFilePath == null) return;

        try {
            File file = registryFilePath.toFile();
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    Type type = new TypeToken<Map<String, SpeakerState>>() {}.getType();
                    Map<String, SpeakerState> loadedStates = GSON.fromJson(reader, type);
                    if (loadedStates != null) {
                        speakerStates.clear();
                        speakerStates.putAll(loadedStates);
                        SimplySpeakers.LOGGER.info("SERVER: Loaded speaker registry with {} entries", loadedStates.size());
                    }
                }
            } else {
                SimplySpeakers.LOGGER.info("SERVER: No existing speaker registry file found, starting with empty registry");
            }
        } catch (Exception e) {
            SimplySpeakers.LOGGER.error("SERVER: Failed to load speaker registry, quarantining corrupt file", e);
            try {
                Path corruptPath = registryFilePath.resolveSibling("speaker_registry.json.corrupt." + System.currentTimeMillis());
                Files.move(registryFilePath, corruptPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {}
        }
    }

    public static SpeakerState getOrCreateSpeakerState(Level level, String stateKey) {
        String fullKey = getRegistryKey(level, stateKey);
        return speakerStates.computeIfAbsent(fullKey, k -> new SpeakerState());
    }

    public static SpeakerState getSpeakerState(Level level, String stateKey) {
        String fullKey = getRegistryKey(level, stateKey);
        return speakerStates.get(fullKey);
    }

    public static void updateSpeakerState(Level level, String stateKey, SpeakerState state) {
        if (state == null) return;
        String fullKey = getRegistryKey(level, stateKey);
        speakerStates.put(fullKey, state.copy());
    }

    public static void removeSpeakerState(Level level, String stateKey) {
        String fullKey = getRegistryKey(level, stateKey);
        speakerStates.remove(fullKey);
    }

    public static void registerSpeaker(Level level, BlockPos pos, String stateKey) {
        if (level == null || level.isClientSide()) return;
        String dimension = getDimension(level);
        String fullKey = getRegistryKey(dimension, stateKey);

        speakerPositions.computeIfAbsent(fullKey, k -> ConcurrentHashMap.newKeySet()).add(pos);
        posToStateKey.put(new SpeakerLocation(dimension, pos.getX(), pos.getY(), pos.getZ()), fullKey);
        SimplySpeakers.LOGGER.debug("SERVER: Registered speaker at {} in {} with key {}", pos, dimension, stateKey);
    }

    public static void registerProxySpeaker(Level level, BlockPos pos, String speakerId) {
        if (level == null || level.isClientSide() || !SpeakerLink.isLinkableId(speakerId)) return;
        String dimension = getDimension(level);
        String fullKey = getRegistryKey(dimension, "net_" + speakerId.trim());

        proxySpeakerPositions.computeIfAbsent(fullKey, k -> ConcurrentHashMap.newKeySet()).add(pos);
        posToStateKey.put(new SpeakerLocation(dimension, pos.getX(), pos.getY(), pos.getZ()), fullKey);
        SimplySpeakers.LOGGER.debug("SERVER: Registered proxy speaker at {} in {} with ID {}", pos, dimension, speakerId);
    }

    public static void unregisterSpeaker(Level level, BlockPos pos, String stateKey) {
        if (level == null || level.isClientSide()) return;
        String dimension = getDimension(level);
        String fullKey = getRegistryKey(dimension, stateKey);

        Set<BlockPos> speakers = speakerPositions.get(fullKey);
        if (speakers != null) {
            speakers.remove(pos);
            if (speakers.isEmpty()) {
                speakerPositions.remove(fullKey);
                removeSpeakerState(level, stateKey);
            }
        }
        posToStateKey.remove(new SpeakerLocation(dimension, pos.getX(), pos.getY(), pos.getZ()));
        SimplySpeakers.LOGGER.debug("SERVER: Unregistered speaker at {} in {} with key {}", pos, dimension, stateKey);
    }

    public static void unregisterProxySpeaker(Level level, BlockPos pos, String speakerId) {
        if (level == null || level.isClientSide()) return;
        String dimension = getDimension(level);
        String fullKey = getRegistryKey(dimension, "net_" + (speakerId != null ? speakerId.trim() : ""));

        Set<BlockPos> proxies = proxySpeakerPositions.get(fullKey);
        if (proxies != null) {
            proxies.remove(pos);
            if (proxies.isEmpty()) {
                proxySpeakerPositions.remove(fullKey);
            }
        }
        posToStateKey.remove(new SpeakerLocation(dimension, pos.getX(), pos.getY(), pos.getZ()));
        SimplySpeakers.LOGGER.debug("SERVER: Unregistered proxy speaker at {} in {} with ID {}", pos, dimension, speakerId);
    }

    public static void updateSpeakerKey(Level level, BlockPos pos, String oldKey, String newKey) {
        if (level == null || level.isClientSide()) return;
        unregisterSpeaker(level, pos, oldKey);
        registerSpeaker(level, pos, newKey);
    }

    public static Set<BlockPos> getSpeakerPositions(Level level, String stateKey) {
        String fullKey = getRegistryKey(level, stateKey);
        Set<BlockPos> set = speakerPositions.get(fullKey);
        return set != null ? Collections.unmodifiableSet(new HashSet<>(set)) : Collections.emptySet();
    }

    public static Set<BlockPos> getProxySpeakerPositions(Level level, String speakerId) {
        String fullKey = getRegistryKey(level, "net_" + (speakerId != null ? speakerId.trim() : ""));
        Set<BlockPos> set = proxySpeakerPositions.get(fullKey);
        return set != null ? Collections.unmodifiableSet(new HashSet<>(set)) : Collections.emptySet();
    }

    public static Map<String, SpeakerState> getAllSpeakerStates() {
        return Collections.unmodifiableMap(speakerStates);
    }

    public static Map<String, SpeakerState> findStatesWithAudioId(String audioId) {
        if (audioId == null || audioId.isEmpty()) return Collections.emptyMap();
        Map<String, SpeakerState> matched = new HashMap<>();
        for (Map.Entry<String, SpeakerState> entry : speakerStates.entrySet()) {
            if (audioId.equals(entry.getValue().getAudioId())) {
                matched.put(entry.getKey(), entry.getValue());
            }
        }
        return matched;
    }
}
