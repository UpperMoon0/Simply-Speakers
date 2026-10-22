package com.nstut.simplyspeakers.forge;
import com.nstut.simplyspeakers.platform.services.IClientHelper;
public class CommonProxy implements IClientHelper {
 public void preInit() { /* Dedicated servers have no client subsystem. */ }
 protected IllegalStateException clientOnly(){return new IllegalStateException("Client UI is unavailable on a dedicated server");}
 public void openSpeakerScreen(net.minecraft.util.math.BlockPos pos){throw clientOnly();}
 public void openProxySpeakerScreen(net.minecraft.util.math.BlockPos pos){throw clientOnly();}
 public void openRedstoneControllerScreen(net.minecraft.util.math.BlockPos pos){throw clientOnly();}
 public void openFileDialog(String filter,java.util.function.Consumer<java.io.File> callback){throw clientOnly();}
}
