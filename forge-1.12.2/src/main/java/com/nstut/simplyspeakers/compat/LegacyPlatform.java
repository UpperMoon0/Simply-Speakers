package com.nstut.simplyspeakers.compat;
public final class LegacyPlatform {
 public static java.nio.file.Path getConfigFolder() { return net.minecraftforge.fml.common.Loader.instance().getConfigDir().toPath(); }
 public static boolean isModLoaded(String id) { return net.minecraftforge.fml.common.Loader.isModLoaded(id); }
}
