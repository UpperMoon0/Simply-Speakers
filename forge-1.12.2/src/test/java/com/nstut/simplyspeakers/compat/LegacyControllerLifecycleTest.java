package com.nstut.simplyspeakers.compat;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import static org.junit.Assert.*;
public class LegacyControllerLifecycleTest {
    @Test public void invalidDetachedAndUnloadedTilesNeverReadWorldTile() {
        BooleanSupplier forbidden=()->{throw new AssertionError("Unexpected world lookup");};
        assertFalse(LegacyControllerLifecycle.current(true,true,forbidden,forbidden));
        assertFalse(LegacyControllerLifecycle.current(false,false,forbidden,forbidden));
        assertFalse(LegacyControllerLifecycle.current(false,true,()->false,forbidden));
    }
    @Test public void loadedWorldMustContainTheExactControllerInstance() {
        assertFalse(LegacyControllerLifecycle.current(false,true,()->true,()->false));
        assertTrue(LegacyControllerLifecycle.current(false,true,()->true,()->true));
    }
}
