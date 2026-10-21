package com.nstut.simplyspeakers.compat;
import java.util.function.BooleanSupplier;
import net.minecraft.world.level.block.entity.BlockEntity;
/** Legacy chunk unload does not reliably call BlockEntity.setRemoved(). */
public final class LegacyControllerLifecycle {
    private LegacyControllerLifecycle() {}
    public static boolean isCurrent(BlockEntity tile) {
        return current(tile.isRemoved(),tile.getLevel()!=null,
                ()->tile.getLevel().hasChunkAt(tile.getBlockPos()),
                ()->tile.getLevel().getBlockEntity(tile.getBlockPos())==tile);
    }
    static boolean current(boolean removed,boolean attached,BooleanSupplier loaded,BooleanSupplier identity) {
        // Never resolve a block entity before the non-loading chunk existence check.
        return !removed && attached && loaded.getAsBoolean() && identity.getAsBoolean();
    }
}
