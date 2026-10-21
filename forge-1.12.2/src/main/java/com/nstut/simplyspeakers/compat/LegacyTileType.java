package com.nstut.simplyspeakers.compat;
/** Native 1.12 tile factory identity, without emulating a newer registry. */
public final class LegacyTileType<T extends net.minecraft.tileentity.TileEntity> {
 public final Class<T> type;
 public LegacyTileType(Class<T> type) { this.type=type; }
 public LegacyTileType<T> get() { return this; }
}
