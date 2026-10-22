package com.nstut.simplyspeakers.compat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LegacyControllerLifecycleTest {
    @Test void unloadedOrDetachedTilesNeverRequestAChunkOrBlockEntity() {
        java.util.function.BooleanSupplier forbidden=()->{throw new AssertionError("Unexpected world lookup");};
        assertFalse(LegacyControllerLifecycle.current(true,true,forbidden,forbidden));
        assertFalse(LegacyControllerLifecycle.current(false,false,forbidden,forbidden));
        assertFalse(LegacyControllerLifecycle.current(false,true,()->false,forbidden));
    }
    @Test void loadedReplacementDoesNotKeepAnOldControllersOverrideAlive() {
        assertFalse(LegacyControllerLifecycle.current(false,true,()->true,()->false));
        assertTrue(LegacyControllerLifecycle.current(false,true,()->true,()->true));
    }
    @Test void nativeCoordinatorRetiresDetachedTileAndResetsItsFirstObservation() throws Exception {
        Class<?> unsafeType=Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field singleton=unsafeType.getDeclaredField("theUnsafe");singleton.setAccessible(true);
        Object unsafe=singleton.get(null);
        com.nstut.simplyspeakers.blocks.entities.RedstoneControllerBlockEntity tile=
            (com.nstut.simplyspeakers.blocks.entities.RedstoneControllerBlockEntity)unsafeType.getMethod("allocateInstance",Class.class)
                .invoke(unsafe,com.nstut.simplyspeakers.blocks.entities.RedstoneControllerBlockEntity.class);
        Class<?> coordinator=com.nstut.simplyspeakers.blocks.entities.ControllerCoordinator.class;
        java.lang.reflect.Field controllers=coordinator.getDeclaredField("controllers");controllers.setAccessible(true);
        java.lang.reflect.Field observation=tile.getClass().getDeclaredField("awaitingFirstObservation");observation.setAccessible(true);
        com.nstut.simplyspeakers.blocks.entities.ControllerCoordinator.reset();
        try {
            com.nstut.simplyspeakers.blocks.entities.ControllerCoordinator.register(tile);
            assertFalse(observation.getBoolean(tile));
            com.nstut.simplyspeakers.blocks.entities.ControllerCoordinator.flush(null);
            assertFalse(((java.util.Set<?>)controllers.get(null)).contains(tile));
            assertTrue(observation.getBoolean(tile));
            assertFalse(tile.isRemoved(),"Retirement must not permanently invalidate a reusable tile");
        } finally {com.nstut.simplyspeakers.blocks.entities.ControllerCoordinator.reset();}
    }
}
