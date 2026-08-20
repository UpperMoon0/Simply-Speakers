package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.SpeakerLink;
import com.nstut.simplyspeakers.SpeakerState;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-only isolated cache of speaker states.
 * Updated strictly by S2C packets (e.g. SpeakerStateUpdatePacketS2C).
 * Never mutates or shares objects with the server-side registry.
 */
public final class ClientSpeakerRegistry {

    private static final Map<String, SpeakerState> clientStates = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> liveLoopStates = new ConcurrentHashMap<>();

    private ClientSpeakerRegistry() {
    }

    public static void registerProxySpeaker(BlockPos pos, String speakerId) {
        if (!SpeakerLink.isLinkableId(speakerId)) {
            return;
        }
    }

    public static void unregisterProxySpeaker(BlockPos pos, String speakerId) {
        if (!SpeakerLink.isLinkableId(speakerId)) {
            return;
        }
    }

    public static void unregisterSpeaker(BlockPos pos, String speakerId) {
    }

    public static SpeakerState getOrCreateState(String stateKey) {
        if (stateKey == null) stateKey = "";
        return clientStates.computeIfAbsent(stateKey, k -> new SpeakerState());
    }

    public static SpeakerState getState(String stateKey) {
        if (stateKey == null) return null;
        return clientStates.get(stateKey);
    }

    public static void updateState(String stateKey, SpeakerState state) {
        if (stateKey == null || state == null) return;
        clientStates.put(stateKey, state.copy());
        liveLoopStates.put(stateKey, state.isLooping());
    }

    public static void setLooping(String stateKey, boolean looping) {
        if (stateKey == null) return;
        liveLoopStates.put(stateKey, looping);
        SpeakerState state = clientStates.get(stateKey);
        if (state != null) {
            state.setLooping(looping);
        }
    }

    public static boolean getLooping(String stateKey, boolean defaultLooping) {
        if (stateKey == null) return defaultLooping;
        return liveLoopStates.getOrDefault(stateKey, defaultLooping);
    }

    public static void clear() {
        clientStates.clear();
        liveLoopStates.clear();
    }
}
