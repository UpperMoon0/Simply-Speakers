package com.nstut.simplyspeakers;

import com.nstut.simplyspeakers.speakers.RangeFadeAudience;
import com.nstut.simplyspeakers.speakers.SpeakerLocation;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RangeFadeAudienceTest {
    private final SpeakerLocation location = new SpeakerLocation("minecraft:overworld", 1, 2, 3);

    @Test void lifecycleAudienceExpiresAndIsConsumedExactlyOnce() {
        var audience = new RangeFadeAudience();
        UUID player = UUID.randomUUID();
        audience.leave(location, player, 0);
        assertEquals(Set.of(player), audience.removeEmitter(location, 1));
        assertTrue(audience.removeEmitter(location, 2).isEmpty());
        audience.leave(location, player, 0);
        assertTrue(audience.removeEmitter(location, RangeFadeAudience.RETENTION_NANOS).isEmpty());
    }

    @Test void reentryLogoutAndExpiryClearDepartedAudience() {
        var audience = new RangeFadeAudience();
        UUID player = UUID.randomUUID();
        audience.leave(location, player, 0);
        audience.reenter(location, player);
        assertTrue(audience.removeEmitter(location, 1).isEmpty());
        audience.leave(location, player, 0);
        audience.removePlayer(player);
        assertTrue(audience.removeEmitter(location, 1).isEmpty());
        audience.leave(location, player, 0);
        audience.prune(RangeFadeAudience.RETENTION_NANOS);
        assertTrue(audience.removeEmitter(location, 1).isEmpty());
    }
}
