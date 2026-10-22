package com.nstut.simplyspeakers.platform;
import com.nstut.simplyspeakers.platform.services.*;
public final class Services {
 public static final IItemHelper ITEMS=new IItemHelper() {};
 public static final IClientHelper CLIENT=new IClientHelper(){public void openSpeakerScreen(net.minecraft.util.math.BlockPos p){com.nstut.simplyspeakers.forge.SimplySpeakersForge.PROXY.openSpeakerScreen(p);}public void openProxySpeakerScreen(net.minecraft.util.math.BlockPos p){com.nstut.simplyspeakers.forge.SimplySpeakersForge.PROXY.openProxySpeakerScreen(p);}public void openRedstoneControllerScreen(net.minecraft.util.math.BlockPos p){com.nstut.simplyspeakers.forge.SimplySpeakersForge.PROXY.openRedstoneControllerScreen(p);}public void openFileDialog(String filter,java.util.function.Consumer<java.io.File> callback){com.nstut.simplyspeakers.forge.SimplySpeakersForge.PROXY.openFileDialog(filter,callback);}};
 public static final IPlatformHelper PLATFORM=new IPlatformHelper(){public String getPlatformName(){return "Forge";}public boolean isModLoaded(String id){return net.minecraftforge.fml.common.Loader.isModLoaded(id);}public boolean isDevelopmentEnvironment(){return Boolean.TRUE.equals(net.minecraft.launchwrapper.Launch.blackboard.get("fml.deobfuscatedEnvironment"));}public void init(){ /* Native lifecycle is registered by the @Mod entry point. */ }};
}
