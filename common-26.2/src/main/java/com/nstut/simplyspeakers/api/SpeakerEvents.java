package com.nstut.simplyspeakers.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-side lifecycle events for speaker networks. Intended for other mods,
 * CC:Tweaked bridge add-ons, and internal integrations. Callbacks run on the
 * main server thread.
 */
public final class SpeakerEvents {

    public enum Type { STARTED, PAUSED, RESUMED, STOPPED, TRACK_CHANGED, FINISHED }

    @FunctionalInterface
    public interface Listener {
        void onSpeakerEvent(Type type, String stateKey, String networkName, String audioId);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private SpeakerEvents() {
    }

    public static void register(Listener listener) {
        if (listener != null) LISTENERS.add(listener);
    }

    public static void unregister(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static void fire(Type type, String stateKey, String networkName, String audioId) {
        for (Listener listener : LISTENERS) {
            try {
                listener.onSpeakerEvent(type, stateKey, networkName != null ? networkName : "", audioId != null ? audioId : "");
            } catch (Exception e) {
                com.nstut.simplyspeakers.SimplySpeakers.LOGGER.error("Speaker event listener failed", e);
            }
        }
    }
}
