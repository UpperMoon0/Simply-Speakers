package com.nstut.simplyspeakers.compat;
public final class LegacyMouse { public double xpos(){return org.lwjgl.input.Mouse.getX();}public double ypos(){return net.minecraft.client.Minecraft.getMinecraft().displayHeight-org.lwjgl.input.Mouse.getY()-1;} }
