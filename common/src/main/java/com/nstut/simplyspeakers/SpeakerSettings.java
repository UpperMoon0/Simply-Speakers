package com.nstut.simplyspeakers;

import com.nstut.simplyspeakers.math.AudioMath;

/** Shared codec-neutral representation of settings persisted by every version module. */
public record SpeakerSettings(float maxVolume, int maxRange, float audioDropoff) {
    public static final String MAX_VOLUME_KEY = "MaxVolume";
    public static final String MAX_RANGE_KEY = "MaxRange";
    public static final String AUDIO_DROPOFF_KEY = "AudioDropoff";

    public SpeakerSettings {
        maxVolume = AudioMath.sanitizeFloat(maxVolume, 0.0f, 1.0f, 1.0f);
        maxRange = effectiveRange(maxRange);
        audioDropoff = AudioMath.sanitizeFloat(audioDropoff, 0.0f, 1.0f, 1.0f);
    }

    public static int effectiveRange(int maxRange) {
        return Math.max(1, Math.min(Config.speakerRange, maxRange));
    }

    public static SpeakerSettings from(SpeakerState state) {
        return new SpeakerSettings(state.getMaxVolume(), state.getMaxRange(), state.getAudioDropoff());
    }

    public static SpeakerSettings read(FloatReader floats, IntReader ints, SpeakerSettings defaults) {
        return new SpeakerSettings(
                floats.get(MAX_VOLUME_KEY, defaults.maxVolume),
                ints.get(MAX_RANGE_KEY, defaults.maxRange),
                floats.get(AUDIO_DROPOFF_KEY, defaults.audioDropoff));
    }

    public void write(FloatWriter floats, IntWriter ints) {
        floats.put(MAX_VOLUME_KEY, maxVolume);
        ints.put(MAX_RANGE_KEY, maxRange);
        floats.put(AUDIO_DROPOFF_KEY, audioDropoff);
    }

    public void applyTo(SpeakerState state) {
        state.setMaxVolume(maxVolume);
        state.setMaxRange(maxRange);
        state.setAudioDropoff(audioDropoff);
    }

    @FunctionalInterface public interface FloatReader { float get(String key, float fallback); }
    @FunctionalInterface public interface IntReader { int get(String key, int fallback); }
    @FunctionalInterface public interface FloatWriter { void put(String key, float value); }
    @FunctionalInterface public interface IntWriter { void put(String key, int value); }
}
