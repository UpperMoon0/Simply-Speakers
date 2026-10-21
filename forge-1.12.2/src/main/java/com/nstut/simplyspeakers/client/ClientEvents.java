package com.nstut.simplyspeakers.client;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.Minecraft;
public final class ClientEvents {
 public static void openSpeakerScreen(BlockPos pos){Minecraft.getMinecraft().displayGuiScreen(new com.nstut.simplyspeakers.client.screens.SpeakerScreen(pos));}
 public static void openProxySpeakerScreen(BlockPos pos){Minecraft.getMinecraft().displayGuiScreen(new com.nstut.simplyspeakers.client.screens.ProxySpeakerScreen(pos));}
 public static void openRedstoneControllerScreen(BlockPos pos){Minecraft.getMinecraft().displayGuiScreen(new com.nstut.simplyspeakers.client.screens.RedstoneControllerScreen(pos));}
}
