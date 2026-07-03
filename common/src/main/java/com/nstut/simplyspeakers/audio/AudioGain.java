package com.nstut.simplyspeakers.audio;

/** Version-neutral gain composition for custom OpenAL speaker sources. */
public final class AudioGain {
    private AudioGain() {
    }

    public static float applyGameVolume(float speakerGain, float masterVolume, float recordsVolume) {
        return clamp(speakerGain) * clamp(masterVolume) * clamp(recordsVolume);
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
