package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.SimplySpeakers;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side registry for tracking proxy speakers by their IDs.
 * This eliminates the need for expensive block scanning operations on the client.
 */
public class ClientSpeakerRegistry {
    // Map of speaker ID to set of proxy speaker positions
    private static final Map<String, Set<BlockPos>> proxySpeakerPositions = new ConcurrentHashMap<>();
    
    // Map of position to speaker ID
    private static final Map<BlockPos, String> proxySpeakerIds = new ConcurrentHashMap<>();
    
    /**
     * Registers a proxy speaker with the registry.
     *
     * @param pos The position of the proxy speaker
     * @param speakerId The speaker ID
     */
    public static void registerProxySpeaker(BlockPos pos, String speakerId) {
        SimplySpeakers.LOGGER.info("CLIENT: Registering proxy speaker at {} with speakerId: '{}'", pos, speakerId);
        // Add to proxy speaker positions map
        proxySpeakerPositions.computeIfAbsent(speakerId, k -> ConcurrentHashMap.newKeySet()).add(pos);
        SimplySpeakers.LOGGER.info("CLIENT: Added proxy speaker at {} to speakerId: '{}' list. List size: {}", pos, speakerId, proxySpeakerPositions.get(speakerId).size());
        
        // Add to position tracking map
        proxySpeakerIds.put(pos, speakerId);
        SimplySpeakers.LOGGER.info("CLIENT: Added proxy speaker at {} to position tracking map", pos);
    }
    
    /**
     * Unregisters a proxy speaker from the registry.
     *
     * @param pos The position of the proxy speaker
     * @param speakerId The speaker ID
     */
    public static void unregisterProxySpeaker(BlockPos pos, String speakerId) {
        // Remove from proxy speaker positions map
        Set<BlockPos> proxySpeakers = proxySpeakerPositions.get(speakerId);
        if (proxySpeakers != null) {
            proxySpeakers.remove(pos);
            if (proxySpeakers.isEmpty()) {
                proxySpeakerPositions.remove(speakerId);
            }
        }
        
        // Remove from position tracking map
        proxySpeakerIds.remove(pos);
    }
    
    /**
     * Updates the speaker ID for a registered proxy speaker.
     *
     * @param pos The position of the proxy speaker
     * @param oldSpeakerId The old speaker ID
     * @param newSpeakerId The new speaker ID
     */
    public static void updateProxySpeakerId(BlockPos pos, String oldSpeakerId, String newSpeakerId) {
        // Unregister with old ID
        unregisterProxySpeaker(pos, oldSpeakerId);
        
        // Register with new ID
        registerProxySpeaker(pos, newSpeakerId);
    }
    
    /**
     * Gets all proxy speaker positions with the specified speaker ID.
     *
     * @param speakerId The speaker ID to look for
     * @return Set of positions of proxy speakers with the specified ID
     */
    public static Set<BlockPos> getProxySpeakerPositions(String speakerId) {
        SimplySpeakers.LOGGER.info("CLIENT: Getting proxy speaker positions for speakerId: '{}'", speakerId);
        Set<BlockPos> positions = proxySpeakerPositions.get(speakerId);
        SimplySpeakers.LOGGER.info("CLIENT: Found {} positions for speakerId: '{}'", positions != null ? positions.size() : 0, speakerId);
        return positions != null ? new HashSet<>(positions) : new HashSet<>();
    }
    
    /**
     * Clears all registry data. Should be called when the client disconnects.
     */
    public static void clear() {
        proxySpeakerPositions.clear();
        proxySpeakerIds.clear();
    }
}