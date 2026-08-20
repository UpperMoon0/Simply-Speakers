package com.nstut.simplyspeakers;

import com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Compatibility bridge delegating all registry operations to ServerSpeakerRegistry.
 */
public class SpeakerRegistry {

    public static void init(Path worldSavePath) {
        ServerSpeakerRegistry.init(worldSavePath);
    }

    public static void saveRegistry() {
        ServerSpeakerRegistry.saveRegistry();
    }

    public static void loadRegistry() {
        ServerSpeakerRegistry.loadRegistry();
    }

    public static void registerSpeaker(Level level, BlockPos pos, String id) {
        ServerSpeakerRegistry.registerSpeaker(level, pos, id);
    }

    public static void registerProxySpeaker(Level level, BlockPos pos, String id) {
        if (SpeakerLink.isLinkableId(id)) {
            ServerSpeakerRegistry.registerProxySpeaker(level, pos, id);
        }
    }

    public static void unregisterSpeaker(Level level, BlockPos pos, String id) {
        ServerSpeakerRegistry.unregisterSpeaker(level, pos, id);
    }

    public static void unregisterProxySpeaker(Level level, BlockPos pos, String id) {
        if (SpeakerLink.isLinkableId(id)) {
            ServerSpeakerRegistry.unregisterProxySpeaker(level, pos, id);
        }
    }

    public static void updateSpeakerId(Level level, BlockPos pos, String oldId, String newId) {
        ServerSpeakerRegistry.updateSpeakerKey(level, pos, oldId, newId);
    }

    public static SpeakerState getOrCreateSpeakerState(Level level, String id) {
        return ServerSpeakerRegistry.getOrCreateSpeakerState(level, id);
    }

    public static SpeakerState getSpeakerState(Level level, String id) {
        return ServerSpeakerRegistry.getSpeakerState(level, id);
    }

    public static void updateSpeakerState(Level level, String id, SpeakerState state) {
        ServerSpeakerRegistry.updateSpeakerState(level, id, state);
    }

    public static Set<BlockPos> getSpeakerPositions(Level level, String id) {
        return ServerSpeakerRegistry.getSpeakerPositions(level, id);
    }

    public static Set<BlockPos> getProxySpeakerPositions(Level level, String id) {
        return ServerSpeakerRegistry.getProxySpeakerPositions(level, id);
    }

    public static Map<String, SpeakerState> getAllSpeakerStates() {
        return ServerSpeakerRegistry.getAllSpeakerStates();
    }
}
