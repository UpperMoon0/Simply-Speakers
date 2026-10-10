package com.nstut.simplyspeakers.portable;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PortableEmitterSnapshotTest {
    private final UUID identity = UUID.randomUUID(), holder = UUID.randomUUID();
    @Test void preservesStableIdentityAndFractionalPose() {
        var snapshot = new PortableEmitterSnapshot(identity, holder, "minecraft:overworld", 12.25, 64.75, -10.5, -47.5f);
        assertEquals(identity, snapshot.identity()); assertEquals(holder, snapshot.holderId());
        assertEquals(12.25, snapshot.x()); assertEquals(64.75, snapshot.y());
        assertEquals(-10.5, snapshot.z()); assertEquals(-47.5f, snapshot.yaw());
    }
    @Test void nonFinitePosesAndMissingDimensionsAreRejectedAtTheWireBoundary() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new PortableEmitterSnapshot(identity, holder, "minecraft:overworld", invalid, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new PortableEmitterSnapshot(identity, holder, "minecraft:overworld", 0, invalid, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new PortableEmitterSnapshot(identity, holder, "minecraft:overworld", 0, 0, invalid, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> new PortableEmitterSnapshot(identity, holder, "minecraft:overworld", 0, 0, 0, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new PortableEmitterSnapshot(identity, holder, "", 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PortableEmitterSnapshot(identity, holder, "x".repeat(257), 0, 0, 0, 0));
    }
}
