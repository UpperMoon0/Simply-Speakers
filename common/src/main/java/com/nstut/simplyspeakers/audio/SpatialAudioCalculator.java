package com.nstut.simplyspeakers.audio;

import java.util.List;

/**
 * Pure, codec-neutral spatial audio calculations for virtual multi-point speaker emitters.
 */
public class SpatialAudioCalculator {

    public record SpeakerEmitter(
            double x, double y, double z,
            int maxRange, float maxVolume, float audioDropoff
    ) {}

    public record VirtualEmitterResult(
            double x, double y, double z,
            float maxGain,
            float totalWeight
    ) {}

    /**
     * Calculates the individual speaker gain based on distance, range, volume, and dropoff factor.
     *
     * @param distance Distance between listener and speaker
     * @param maxRange Maximum hearing range of the speaker (in blocks)
     * @param maxVolume Maximum volume factor (0.0 to 1.0)
     * @param audioDropoff Dropoff curve exponent factor (0.0 = no dropoff until maxRange, 1.0 = standard dropoff)
     * @return Calculated gain (clamped between 0.0 and maxVolume)
     */
    public static float calculateDistanceGain(double distance, int maxRange, float maxVolume, float audioDropoff) {
        if (distance < 0 || maxRange <= 0 || maxVolume <= 0.0f) {
            return 0.0f;
        }
        if (distance >= maxRange) {
            return 0.0f;
        }
        if (audioDropoff <= 0.0f) {
            return maxVolume;
        }
        double dropoffFactor = Math.pow(1.0 - (distance / (double) maxRange), audioDropoff * 2.0);
        float gain = (float) (maxVolume * dropoffFactor);
        return Math.max(0.0f, Math.min(maxVolume, gain));
    }

    // ------------------------------------------------------------------
    // Directional audio (0.8.x)
    // ------------------------------------------------------------------

    /** Directional emission settings for one emitter. */
    public record ConeSettings(float directionality, float coneAngleDegrees, float rearAttenuation) {
        public static final ConeSettings OMNIDIRECTIONAL =
                new ConeSettings(0.0f, 360.0f, 0.0f);
    }

    /**
     * Distance gain combined with a horizontal directional cone.
     *
     * @param facingX      normalized emitter facing X component
     * @param facingZ      normalized emitter facing Z component
     * @param toListenerX  normalized vector from emitter towards the listener (X)
     * @param toListenerZ  normalized vector from emitter towards the listener (Z)
     */
    public static float calculateDistanceGain(
            double distance, int maxRange, float maxVolume, float audioDropoff,
            double facingX, double facingZ,
            double toListenerX, double toListenerZ,
            ConeSettings cone) {
        float baseGain = calculateDistanceGain(distance, maxRange, maxVolume, audioDropoff);
        if (baseGain <= 0.0f || cone == null) return baseGain;
        float directionality = clamp01(cone.directionality());
        if (directionality <= 0.0f) return baseGain;

        double facingLength = Math.sqrt(facingX * facingX + facingZ * facingZ);
        double toListenerLength = Math.sqrt(toListenerX * toListenerX + toListenerZ * toListenerZ);
        if (facingLength < 1.0E-6 || toListenerLength < 1.0E-6) return baseGain;

        double cosAngle = (facingX * toListenerX + facingZ * toListenerZ) / (facingLength * toListenerLength);
        cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle));
        double angleDegrees = Math.toDegrees(Math.acos(cosAngle));

        float coneAngle = Math.max(5.0f, Math.min(355.0f, cone.coneAngleDegrees()));
        float halfCone = coneAngle / 2.0f;

        if (angleDegrees <= halfCone) {
            return baseGain; // inside the main lobe
        }

        float rearFactor = clamp01(1.0f - clamp01(cone.rearAttenuation()) * directionality);
        double span = 180.0 - halfCone;
        double t = span > 0 ? (angleDegrees - halfCone) / span : 1.0;
        t = Math.max(0.0, Math.min(1.0, t));
        double smooth = t * t * (3.0 - 2.0 * t); // smoothstep between lobe edge and rear
        float factor = (float) (1.0 - (1.0 - rearFactor) * smooth);
        return baseGain * factor;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    /**
     * Computes the weighted virtual emitter position and maximum dominant gain for a collection of overlapping speakers.
     *
     * @param listenerX Listener X coordinate
     * @param listenerY Listener Y coordinate
     * @param listenerZ Listener Z coordinate
     * @param emitters List of active speaker emitters in the network
     * @return VirtualEmitterResult with weighted 3D coordinates and dominant peak gain
     */
    public static VirtualEmitterResult calculateVirtualEmitter(
            double listenerX, double listenerY, double listenerZ,
            List<SpeakerEmitter> emitters
    ) {
        if (emitters == null || emitters.isEmpty()) {
            return new VirtualEmitterResult(listenerX, listenerY, listenerZ, 0.0f, 0.0f);
        }

        double weightedX = 0.0;
        double weightedY = 0.0;
        double weightedZ = 0.0;
        float totalWeight = 0.0f;
        float maxGain = 0.0f;

        for (SpeakerEmitter emitter : emitters) {
            double dx = emitter.x() - listenerX;
            double dy = emitter.y() - listenerY;
            double dz = emitter.z() - listenerZ;
            double distSq = dx * dx + dy * dy + dz * dz;
            double maxRangeSq = (double) emitter.maxRange() * emitter.maxRange();

            if (distSq < maxRangeSq) {
                double distance = Math.sqrt(distSq);
                float gain = calculateDistanceGain(distance, emitter.maxRange(), emitter.maxVolume(), emitter.audioDropoff());
                if (gain > 0.0f) {
                    weightedX += emitter.x() * gain;
                    weightedY += emitter.y() * gain;
                    weightedZ += emitter.z() * gain;
                    totalWeight += gain;
                    if (gain > maxGain) {
                        maxGain = gain;
                    }
                }
            }
        }

        if (totalWeight > 0.0f) {
            return new VirtualEmitterResult(
                    weightedX / totalWeight,
                    weightedY / totalWeight,
                    weightedZ / totalWeight,
                    maxGain,
                    totalWeight
            );
        } else {
            // Fallback to the first emitter position when out of range
            SpeakerEmitter first = emitters.get(0);
            return new VirtualEmitterResult(first.x(), first.y(), first.z(), 0.0f, 0.0f);
        }
    }
}
