package com.nstut.simplyspeakers.compat;

import java.util.function.BooleanSupplier;
import net.minecraft.tileentity.TileEntity;

/** Forge chunk unload removes ticking tiles without permanently invalidating them. */
public final class LegacyControllerLifecycle {
    private LegacyControllerLifecycle() { }

    public static boolean isCurrent(TileEntity tile) {
        return current(tile.isInvalid(), tile.getWorld() != null,
                () -> tile.getWorld().isBlockLoaded(tile.getPos()),
                () -> tile.getWorld().getTileEntity(tile.getPos()) == tile);
    }

    static boolean current(boolean invalid, boolean attached, BooleanSupplier loaded, BooleanSupplier identity) {
        // The identity lookup must never force an unloaded chunk to become available.
        return ControllerAttachment.current(invalid, attached, loaded, identity);
    }
}
