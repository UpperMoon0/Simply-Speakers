package com.nstut.simplyspeakers.testing;

import com.nstut.simplyspeakers.client.IntegratedAudioClientProbe;
import com.nstut.simplyspeakers.client.testing.VanillaSingleplayerDriver;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Native classes may be loaded on ordinary launches, but disabled probes never touch the game. */
class IntegratedAcceptanceDisabledTest {
    @Test void disabledProbesAreInertBeforeDereferencingNativeObjects() {
        String key="simplyspeakers.integratedAcceptance";
        String previous=System.getProperty(key);
        try {
            System.clearProperty(key);
            assertFalse(IntegratedAudioServerProbe.enabled());
            assertDoesNotThrow(() -> IntegratedAudioServerProbe.tick(null));
            assertDoesNotThrow(() -> IntegratedAudioClientProbe.tick(null));
            assertDoesNotThrow(() -> VanillaSingleplayerDriver.tick(null));
        } finally {
            if(previous==null)System.clearProperty(key);else System.setProperty(key,previous);
        }
    }
}
