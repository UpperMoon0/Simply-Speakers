package com.nstut.simplyspeakers.testing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class JoinWaitDiagnosticsTest {
    @Test public void reportsChangesWithoutReadingScreenContents() {
        JoinWaitDiagnostics diagnostics = new JoinWaitDiagnostics();
        Object screen = new Object() {
            @Override public String toString() { throw new AssertionError("Screen contents must not be read"); }
        };
        assertEquals("screen=none, overlay=none, multiplayerAllowed=true", diagnostics.update(false, null, null, true));
        assertNull(diagnostics.update(false, null, null, true));
        assertEquals("screen=" + screen.getClass().getName() + ", overlay=none, multiplayerAllowed=true", diagnostics.update(false, screen, null, true));
        assertNull(diagnostics.update(false, screen, null, true));
        assertEquals("screen=none, overlay=" + screen.getClass().getName() + ", multiplayerAllowed=true", diagnostics.update(false, null, screen, true));
    }

    @Test public void nativePermissionObservationDoesNotAlterIt() {
        JoinWaitDiagnostics diagnostics = new JoinWaitDiagnostics();
        assertEquals("screen=none, overlay=none, multiplayerAllowed=false", diagnostics.update(false, null, null, false));
        assertNull(diagnostics.update(false, null, null, false));
        assertEquals("screen=none, overlay=none, multiplayerAllowed=true", diagnostics.update(false, null, null, true));
    }

    @Test public void connectionSuppressesOutputAndResetsNextWait() {
        JoinWaitDiagnostics diagnostics = new JoinWaitDiagnostics();
        assertNotNull(diagnostics.update(false, null, null, true));
        assertNull(diagnostics.update(true, new Object(), new Object(), true));
        assertNotNull(diagnostics.update(false, null, null, true));
    }
}
