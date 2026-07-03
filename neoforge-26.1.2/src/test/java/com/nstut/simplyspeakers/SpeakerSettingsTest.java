package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpeakerSettingsTest {
    @Test
    void readsAllPersistedSettingsAndAppliesThem() {
        Map<String, Float> floats = Map.of(
                SpeakerSettings.MAX_VOLUME_KEY, 0.35f,
                SpeakerSettings.AUDIO_DROPOFF_KEY, 0.6f);
        Map<String, Integer> ints = Map.of(SpeakerSettings.MAX_RANGE_KEY, 48);

        SpeakerSettings settings = SpeakerSettings.read(
                (key, fallback) -> floats.getOrDefault(key, fallback),
                (key, fallback) -> ints.getOrDefault(key, fallback),
                new SpeakerSettings(1.0f, 16, 1.0f));
        SpeakerState state = new SpeakerState();
        settings.applyTo(state);

        assertEquals(0.35f, state.getMaxVolume());
        assertEquals(48, state.getMaxRange());
        assertEquals(0.6f, state.getAudioDropoff());
    }

    @Test
    void writesEverySettingToBlockEntityData() {
        Map<String, Float> floats = new HashMap<>();
        Map<String, Integer> ints = new HashMap<>();
        new SpeakerSettings(0.4f, 32, 0.75f).write(floats::put, ints::put);

        assertEquals(0.4f, floats.get(SpeakerSettings.MAX_VOLUME_KEY));
        assertEquals(32, ints.get(SpeakerSettings.MAX_RANGE_KEY));
        assertEquals(0.75f, floats.get(SpeakerSettings.AUDIO_DROPOFF_KEY));
    }

    @Test
    void clampsInvalidNetworkOrSaveValues() {
        SpeakerSettings settings = new SpeakerSettings(2.0f, -4, -1.0f);
        assertEquals(1.0f, settings.maxVolume());
        assertEquals(1, settings.maxRange());
        assertEquals(0.0f, settings.audioDropoff());
    }
}
