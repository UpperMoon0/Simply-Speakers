package com.nstut.simplyspeakers.math;

public final class AudioMath {

    private AudioMath() {
    }

    /**
     * Sanitizes a float value against NaN and Infinities, then clamps it between min and max.
     *
     * @param value        The input float value to sanitize.
     * @param min          The inclusive minimum allowed value.
     * @param max          The inclusive maximum allowed value.
     * @param defaultValue The fallback value to use if input is NaN or Infinite.
     * @return A finite float guaranteed to be within [min, max].
     */
    public static float sanitizeFloat(float value, float min, float max, float defaultValue) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return Math.max(min, Math.min(max, defaultValue));
        }
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Sanitizes a float value clamped between 0.0f and 1.0f.
     */
    public static float sanitizeFloat(float value, float defaultValue) {
        return sanitizeFloat(value, 0.0f, 1.0f, defaultValue);
    }

    /**
     * Sanitizes a double value against NaN and Infinities, then clamps it between min and max.
     */
    public static double sanitizeDouble(double value, double min, double max, double defaultValue) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Math.max(min, Math.min(max, defaultValue));
        }
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Sanitizes a double value clamped between 0.0 and 1.0.
     */
    public static double sanitizeDouble(double value, double defaultValue) {
        return sanitizeDouble(value, 0.0, 1.0, defaultValue);
    }
}
