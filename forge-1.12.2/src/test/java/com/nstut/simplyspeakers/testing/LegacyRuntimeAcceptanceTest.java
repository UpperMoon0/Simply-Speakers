package com.nstut.simplyspeakers.testing;
import org.junit.Test;
import static org.junit.Assert.*;
/** Disabled probes must be inert even when no Minecraft client or server exists. */
public final class LegacyRuntimeAcceptanceTest {
    @Test public void normalDedicatedServerNeverRegistersOrMutatesFixture() {
        String before = System.getProperty(LegacyRuntimeAcceptance.PROPERTY);
        try {
            System.clearProperty(LegacyRuntimeAcceptance.PROPERTY);
            assertFalse(LegacyRuntimeAcceptance.enabled());
            LegacyRuntimeAcceptance.tick(null);
        } finally { restore(before); }
    }
    @Test public void normalClientNeverConnectsOrTouchesNativeAudio() {
        String before = System.getProperty(LegacyRuntimeAcceptance.PROPERTY);
        try {
            System.setProperty(LegacyRuntimeAcceptance.PROPERTY, "false");
            com.nstut.simplyspeakers.client.LegacyClientRuntimeAcceptance.tick(null);
        } finally { restore(before); }
    }
    private static void restore(String before) {
        if (before == null) System.clearProperty(LegacyRuntimeAcceptance.PROPERTY);
        else System.setProperty(LegacyRuntimeAcceptance.PROPERTY, before);
    }
}
