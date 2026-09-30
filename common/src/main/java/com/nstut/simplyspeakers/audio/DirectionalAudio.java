package com.nstut.simplyspeakers.audio;

/** Shared helpers for directional emitter cones. Pure data, unit testable. */
public final class DirectionalAudio {

    /**
     * Wire-friendly snapshot of one emitter's cone configuration.
     *
     * @param directionality   0 = omnidirectional .. 1 = fully directional
     * @param coneAngleDegrees full cone angle inside which audio is unattenuated
     * @param rearAttenuation  energy lost directly behind the speaker (0-1)
     * @param facingOrdinal    vanilla Direction ordinal of the emitter
     */
    public record Extras(float directionality, float coneAngleDegrees,
                         float rearAttenuation, byte facingOrdinal) {
    }

    private DirectionalAudio() {
    }

    public static double[] normalize(double x, double z) {
        double len = Math.sqrt(x * x + z * z);
        if (len < 1.0E-6) return new double[] {0.0, -1.0};
        return new double[] {x / len, z / len};
    }

    public static double[] facingFromOrdinal(int ordinal) {
        // Vanilla order: DOWN, UP, NORTH, SOUTH, WEST, EAST
        return switch (ordinal) {
            case 3 -> new double[] {0.0, 1.0};   // SOUTH -> +Z
            case 4 -> new double[] {-1.0, 0.0};  // WEST  -> -X
            case 5 -> new double[] {1.0, 0.0};   // EAST  -> +X
            default -> new double[] {0.0, -1.0}; // NORTH or vertical -> -Z
        };
    }
}
