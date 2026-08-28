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
import java.util.Collection;
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
    private static final Map<String, Set<BlockPos>> poweredSpeakerPositions = new ConcurrentHashMap<>();
    private static final Map<SpeakerLocation, String> posToStateKey = new ConcurrentHashMap<>();
    private static final Map<String, SpeakerState> speakerStates = new ConcurrentHashMap<>();
    private static final Map<SpeakerLocation, ServerEmitter> emitters = new ConcurrentHashMap<>();
    private static SpeakerState legacyDefaultTemplate = null;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path registryFilePath;

    private ServerSpeakerRegistry() {
    }

    public static String getDimension(Level level) {
        if (level == null) return "minecraft:overworld";
        return level.dimension().location().toString();
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
        poweredSpeakerPositions.clear();
        posToStateKey.clear();
        speakerStates.clear();
        emitters.clear();
        legacyDefaultTemplate = null;
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
            try {
                Files.move(tmpPath, registryFilePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                Files.move(tmpPath, registryFilePath, StandardCopyOption.REPLACE_EXISTING);
            }
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
                        try {
                            Path backupPath = registryFilePath.resolveSibling("speaker_registry.json.bak");
                            Files.copy(registryFilePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            SimplySpeakers.LOGGER.warn("SERVER: Failed to create backup of speaker_registry.json", e);
                        }
                        speakerStates.clear();
                        if (loadedStates.containsKey("")) {
                            legacyDefaultTemplate = loadedStates.get("").copy();
                        } else if (loadedStates.containsKey("net_")) {
                            legacyDefaultTemplate = loadedStates.get("net_").copy();
                        } else if (loadedStates.containsKey("minecraft:overworld/net_")) {
                            legacyDefaultTemplate = loadedStates.get("minecraft:overworld/net_").copy();
                        }

                        boolean migratedAny = false;
                        for (Map.Entry<String, SpeakerState> entry : loadedStates.entrySet()) {
                            String key = entry.getKey();
                            SpeakerState state = entry.getValue();
                            if (key == null || state == null) continue;

                            String normalizedKey = key;
                            if (!key.contains("/")) {
                                // Old legacy unprefixed format
                                migratedAny = true;
                                if (key.startsWith("internal_") || key.startsWith("net_")) {
                                    normalizedKey = "minecraft:overworld/" + key;
                                } else {
                                    normalizedKey = "minecraft:overworld/net_" + key.trim();
                                }
                            }
                            speakerStates.put(normalizedKey, state);
                        }
                        SimplySpeakers.LOGGER.info("SERVER: Loaded speaker registry with {} entries (migrated: {})", speakerStates.size(), migratedAny);
                        if (migratedAny) {
                            saveRegistry();
                        }
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

    public static void applyLegacyStandaloneTemplate(Level level, String stateKey) {
        if (legacyDefaultTemplate == null) return;
        String fullKey = getRegistryKey(level, stateKey);
        speakerStates.computeIfAbsent(fullKey, k -> legacyDefaultTemplate.copy());
    }

    public static SpeakerState getSpeakerState(Level level, String stateKey) {
        String fullKey = getRegistryKey(level, stateKey);
        return speakerStates.get(fullKey);
    }

    public static String getStateKey(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        String dimension = getDimension(level);
        return posToStateKey.get(new SpeakerLocation(dimension, pos.getX(), pos.getY(), pos.getZ()));
    }

    public static SpeakerState getSpeakerStateByPos(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        String fullKey = getStateKey(level, pos);
        if (fullKey == null) return null;
        return speakerStates.get(fullKey);
    }

    public static SpeakerState getSpeakerStateByFullKey(String fullKey) {
        if (fullKey == null) return null;
        return speakerStates.get(fullKey);
    }

    public static void updateSpeakerStateByFullKey(String fullKey, SpeakerState state) {
        if (fullKey == null || state == null) return;
        speakerStates.put(fullKey, state.copy());
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


    /**
     * Persists an emitter snapshot. Snapshots survive chunk unloads so that centralized
     * playback management is independent of block entity ticking.
     */
    public static void upsertEmitter(ServerEmitter emitter) {
        if (emitter == null || emitter.location() == null) return;
        emitters.put(emitter.location(), emitter);
    }

    public static ServerEmitter getEmitter(SpeakerLocation location) {
        return location != null ? emitters.get(location) : null;
    }

    public static void removeEmitter(SpeakerLocation location) {
        if (location != null) emitters.remove(location);
    }

    public static Collection<ServerEmitter> getEmitters() {
        return Collections.unmodifiableCollection(emitters.values());
    }

    private static SpeakerLocation locationOf(String dimension, BlockPos pos) {
        return new SpeakerLocation(dimension, pos.getX(), pos.getY(), pos.getZ());
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

    public static void setSpeakerPowered(Level level, BlockPos pos, String stateKey, boolean powered) {
        if (level == null || level.isClientSide()) return;
        String dimension = getDimension(level);
        String fullKey = getRegistryKey(dimension, stateKey);
        if (powered) {
            Set<BlockPos> poweredSet = poweredSpeakerPositions.computeIfAbsent(fullKey, k -> ConcurrentHashMap.newKeySet());
            poweredSet.add(pos);
        } else {
            Set<BlockPos> poweredSet = poweredSpeakerPositions.get(fullKey);
            if (poweredSet != null) {
                poweredSet.remove(pos);
                if (poweredSet.isEmpty()) poweredSpeakerPositions.remove(fullKey, poweredSet);
            }
        }
    }

    public static boolean hasOtherPoweredMain(Level level, BlockPos currentPos, String stateKey) {
        if (level == null || level.isClientSide()) return false;
        String dimension = getDimension(level);
        String fullKey = getRegistryKey(dimension, stateKey);
        Set<BlockPos> powered = poweredSpeakerPositions.get(fullKey);
        if (powered == null || powered.isEmpty()) return false;
        for (BlockPos pos : powered) {
            if (!pos.equals(currentPos)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasAnyPoweredMain(Level level, String stateKey) {
        if (level == null || level.isClientSide()) return false;
        String dimension = getDimension(level);
        String fullKey = getRegistryKey(dimension, stateKey);
        Set<BlockPos> positions = poweredSpeakerPositions.get(fullKey);
        return positions != null && !positions.isEmpty();
    }

    public static void unregisterSpeaker(Level level, BlockPos pos, String stateKey) {
        if (level == null || level.isClientSide()) return;
        String dimension = getDimension(level);
        String fullKey = getRegistryKey(dimension, stateKey);

        Set<BlockPos> powered = poweredSpeakerPositions.get(fullKey);
        if (powered != null) {
            powered.remove(pos);
            if (powered.isEmpty()) {
                poweredSpeakerPositions.remove(fullKey);
            }
        }

        Set<BlockPos> speakers = speakerPositions.get(fullKey);
        if (speakers != null) {
            speakers.remove(pos);
            if (speakers.isEmpty()) {
                speakerPositions.remove(fullKey);
                removeSpeakerState(level, stateKey);
            }
        }
        posToStateKey.remove(new SpeakerLocation(dimension, pos.getX(), pos.getY(), pos.getZ()));
        SpeakerLocation loc = locationOf(dimension, pos);
        ServerPlaybackManager.unregisterEmitter(level.getServer(), loc);
    }

    public static void unregisterSpeaker(Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) return;
        String dimension = getDimension(level);
        SpeakerLocation loc = locationOf(dimension, pos);
        String fullKey = posToStateKey.remove(loc);
        if (fullKey != null) {
            Set<BlockPos> speakers = speakerPositions.get(fullKey);
            if (speakers != null) {
                speakers.remove(pos);
                if (speakers.isEmpty()) {
                    speakerPositions.remove(fullKey);
                }
            }
            Set<BlockPos> powered = poweredSpeakerPositions.get(fullKey);
            if (powered != null) {
                powered.remove(pos);
                if (powered.isEmpty()) {
                    poweredSpeakerPositions.remove(fullKey);
                }
            }
        }
        removeEmitter(loc);
        ServerPlaybackManager.unregisterEmitter(level.getServer(), loc);
    }

    public static void unregisterProxySpeaker(Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) return;
        String dimension = getDimension(level);
        SpeakerLocation loc = locationOf(dimension, pos);
        String fullKey = posToStateKey.remove(loc);
        if (fullKey != null) {
            Set<BlockPos> proxies = proxySpeakerPositions.get(fullKey);
            if (proxies != null) {
                proxies.remove(pos);
                if (proxies.isEmpty()) {
                    proxySpeakerPositions.remove(fullKey);
                }
            }
        }
        removeEmitter(loc);
        ServerPlaybackManager.unregisterEmitter(level.getServer(), loc);
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
        SpeakerLocation loc = locationOf(dimension, pos);
        ServerPlaybackManager.unregisterEmitter(level.getServer(), loc);
        SimplySpeakers.LOGGER.debug("SERVER: Unregistered proxy speaker at {} in {} with ID {}", pos, dimension, speakerId);
    }

    public static void updateSpeakerId(Level level, BlockPos pos, String oldKey, String newKey) {
        updateSpeakerKey(level, pos, oldKey, newKey);
    }

    public static void updateSpeakerKey(Level level, BlockPos pos, String oldKey, String newKey) {
        if (level == null || level.isClientSide()) return;
        if (oldKey == null) oldKey = "";
        if (newKey == null) newKey = "";
        if (oldKey.equals(newKey)) return;

        String dimension = getDimension(level);
        String oldFullKey = getRegistryKey(dimension, oldKey);
        String newFullKey = getRegistryKey(dimension, newKey);

        SpeakerState sourceState = speakerStates.get(oldFullKey);
        SpeakerState destinationState = speakerStates.get(newFullKey);
        Set<BlockPos> existingMainSpeakers = speakerPositions.get(newFullKey);
        boolean destinationHasMainSpeaker = existingMainSpeakers != null && !existingMainSpeakers.isEmpty();

        SpeakerState finalState = com.nstut.simplyspeakers.SpeakerStateRelinker.stateForNewId(sourceState, destinationState, destinationHasMainSpeaker);
        if (finalState != null) {
            speakerStates.put(newFullKey, finalState);
        }

        Set<BlockPos> oldPowered = poweredSpeakerPositions.get(oldFullKey);
        boolean wasPowered = oldPowered != null && oldPowered.contains(pos);

        unregisterSpeaker(level, pos, oldKey);
        registerSpeaker(level, pos, newKey);

        if (wasPowered) {
            setSpeakerPowered(level, pos, newKey, true);
        }

        saveRegistry();
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
