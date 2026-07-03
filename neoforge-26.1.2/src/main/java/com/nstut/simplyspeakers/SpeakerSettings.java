package com.nstut.simplyspeakers;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Version-local codec for settings stored in block entity update and save data. */
public record SpeakerSettings(float maxVolume, int maxRange, float audioDropoff) {
    public static final String MAX_VOLUME_KEY = "MaxVolume";
    public static final String MAX_RANGE_KEY = "MaxRange";
    public static final String AUDIO_DROPOFF_KEY = "AudioDropoff";

    public SpeakerSettings {
        maxVolume = Math.max(0.0f, Math.min(1.0f, maxVolume));
        maxRange = Math.max(1, Math.min(Config.MAX_RANGE, maxRange));
        audioDropoff = Math.max(0.0f, Math.min(1.0f, audioDropoff));
    }

    public static SpeakerSettings from(SpeakerState state) {
        return new SpeakerSettings(state.getMaxVolume(), state.getMaxRange(), state.getAudioDropoff());
    }

    public static SpeakerSettings read(ValueInput input, SpeakerSettings defaults) {
        return read(input::getFloatOr, input::getIntOr, defaults);
    }

    static SpeakerSettings read(FloatReader floats, IntReader ints, SpeakerSettings defaults) {
        return new SpeakerSettings(
                floats.get(MAX_VOLUME_KEY, defaults.maxVolume),
                ints.get(MAX_RANGE_KEY, defaults.maxRange),
                floats.get(AUDIO_DROPOFF_KEY, defaults.audioDropoff));
    }

    public void write(ValueOutput output) {
        write(output::putFloat, output::putInt);
    }

    void write(FloatWriter floats, IntWriter ints) {
        floats.put(MAX_VOLUME_KEY, maxVolume);
        ints.put(MAX_RANGE_KEY, maxRange);
        floats.put(AUDIO_DROPOFF_KEY, audioDropoff);
    }

    public void applyTo(SpeakerState state) {
        state.setMaxVolume(maxVolume);
        state.setMaxRange(maxRange);
        state.setAudioDropoff(audioDropoff);
    }

    @FunctionalInterface interface FloatReader { float get(String key, float fallback); }
    @FunctionalInterface interface IntReader { int get(String key, int fallback); }
    @FunctionalInterface interface FloatWriter { void put(String key, float value); }
    @FunctionalInterface interface IntWriter { void put(String key, int value); }
}
