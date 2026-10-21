package com.nstut.simplyspeakers.testing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class JoinWaitDiagnosticsTest {
    @Test public void reportsChangesWithoutReadingScreenContents() {
        JoinWaitDiagnostics diagnostics = new JoinWaitDiagnostics();
        Object screen = new Object() {
            @Override public String toString() { throw new AssertionError("Screen contents must not be read"); }
        };
        assertEquals("screen=none, overlay=none", diagnostics.update(false, null, null));
        assertNull(diagnostics.update(false, null, null));
        assertEquals("screen=" + screen.getClass().getName() + ", overlay=none", diagnostics.update(false, screen, null));
        assertNull(diagnostics.update(false, screen, null));
        assertEquals("screen=none, overlay=" + screen.getClass().getName(), diagnostics.update(false, null, screen));
    }

    @Test public void connectionSuppressesOutputAndResetsNextWait() {
        JoinWaitDiagnostics diagnostics = new JoinWaitDiagnostics();
        assertNotNull(diagnostics.update(false, null, null));
        assertNull(diagnostics.update(true, new Object(), new Object()));
        assertNotNull(diagnostics.update(false, null, null));
    }
}
