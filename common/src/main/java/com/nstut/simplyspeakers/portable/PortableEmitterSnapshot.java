package com.nstut.simplyspeakers.portable;

import java.util.Objects;
import java.util.UUID;

/** Authoritative mobile-emitter identity and pose, independent of entity tracking range. */
public record PortableEmitterSnapshot(UUID identity, UUID holderId, String dimension,
                                      double x, double y, double z, float yaw) {
    public static final double EMITTER_HEIGHT = 1.0;

    public PortableEmitterSnapshot {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(holderId, "holderId");
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.isBlank() || dimension.length() > 256
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw)) throw new IllegalArgumentException("Invalid portable emitter pose");
    }
}
