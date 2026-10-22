package com.nstut.simplyspeakers.compat;

import com.nstut.simplyspeakers.audio.AudioContextGate;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC10;

/** Paulscode's loaded flag also succeeds for its silent fallback library. */
public final class LegacySoundContext {
    private LegacySoundContext() { }

    public static AudioContextGate newSuspendedGate() {
        AudioContextGate gate = new AudioContextGate();
        gate.suspend();
        return gate;
    }

    public static void ready(boolean loaded, Runnable resume, Runnable suspend) {
        ready(loaded, AL::isCreated, AL::getContext, ALC10::alcGetCurrentContext, resume, suspend);
    }

    static void ready(boolean loaded, BooleanSupplier created, Supplier<?> context,
                      Supplier<?> current, Runnable resume, Runnable suspend) {
        if (available(loaded, created, context, current)) resume.run();
        else suspend.run();
    }

    static boolean available(boolean loaded, BooleanSupplier created, Supplier<?> context, Supplier<?> current) {
        try {
            if (!loaded || !created.getAsBoolean()) return false;
            Object owned = context.get();
            return owned != null && owned == current.get();
        } catch (RuntimeException | LinkageError unavailable) {
            // A device/library initialization failure must not reopen the AL gate.
            return false;
        }
    }
}
