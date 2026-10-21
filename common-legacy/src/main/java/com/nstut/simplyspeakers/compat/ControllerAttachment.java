package com.nstut.simplyspeakers.compat;
import java.util.function.BooleanSupplier;
/** Shared non-loading attachment predicate for both legacy native tile adapters. */
public final class ControllerAttachment {
    private ControllerAttachment() {}
    public static boolean current(boolean removed, boolean attached,
                                  BooleanSupplier loaded, BooleanSupplier identity) {
        // Identity lookup can load a chunk, so it must remain after the cheap guards.
        return !removed && attached && loaded.getAsBoolean() && identity.getAsBoolean();
    }
}
