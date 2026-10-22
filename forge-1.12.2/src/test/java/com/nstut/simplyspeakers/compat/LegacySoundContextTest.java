package com.nstut.simplyspeakers.compat;

import com.nstut.simplyspeakers.audio.AudioContextGate;
import java.util.function.Supplier;
import org.junit.Test;
import static org.junit.Assert.*;

public class LegacySoundContextTest {
    @Test public void initialPlaybackIsSuspendedUntilNativeReadiness() {
        AudioContextGate gate = LegacySoundContext.newSuspendedGate();
        assertFalse(gate.isAvailable());
        assertFalse(gate.isCurrent(gate.epoch()));
    }

    @Test public void loadedFlagCannotResumeSilentFallbackOrMissingCurrentContext() {
        Object context = new Object();
        assertFalse(LegacySoundContext.available(true, () -> false, () -> context, () -> context));
        assertFalse(LegacySoundContext.available(true, () -> true, () -> null, () -> context));
        assertFalse(LegacySoundContext.available(true, () -> true, () -> context, () -> null));
        assertFalse(LegacySoundContext.available(true, () -> true, () -> context, Object::new));
        assertTrue(LegacySoundContext.available(true, () -> true, () -> context, () -> context));
    }

    @Test public void failedLoadAndNativeFailureNeverProbeOrResumeUnsafely() {
        Supplier<Object> forbidden = () -> { throw new AssertionError("Unexpected native context probe"); };
        assertFalse(LegacySoundContext.available(false, () -> { throw new AssertionError("Unexpected native probe"); }, forbidden, forbidden));
        assertFalse(LegacySoundContext.available(true, () -> false, forbidden, forbidden));
        assertFalse(LegacySoundContext.available(true, () -> { throw new UnsatisfiedLinkError("Unavailable OpenAL"); }, forbidden, forbidden));
    }

    @Test public void failureInvalidatesEpochAndSuccessfulReloadNeverRevivesOldHandles() {
        AudioContextGate gate = LegacySoundContext.newSuspendedGate();
        Object context = new Object();
        LegacySoundContext.ready(true, () -> true, () -> context, () -> context, gate::resume, gate::suspend);
        long old = gate.epoch(); assertTrue(gate.isCurrent(old));
        gate.suspend(); // destruction hook runs before vanilla destroys old native handles
        assertFalse(gate.isCurrent(old));
        LegacySoundContext.ready(true, () -> false, () -> null, () -> null, gate::resume, gate::suspend);
        long failed = gate.epoch(); assertFalse(gate.isAvailable());
        assertTrue(failed > old);
        LegacySoundContext.ready(true, () -> true, () -> context, () -> context, gate::resume, gate::suspend);
        assertTrue(gate.isCurrent(failed)); assertFalse(gate.isCurrent(old));
        try { gate.run(old, () -> fail("Stale AL operation executed")); fail("Old epoch accepted"); }
        catch (AudioContextGate.StaleContextException expected) { }
    }
}
