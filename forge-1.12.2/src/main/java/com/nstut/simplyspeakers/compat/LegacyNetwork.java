package com.nstut.simplyspeakers.compat;
import net.minecraft.entity.player.EntityPlayer;
public final class LegacyNetwork {
 public interface PacketContext { EntityPlayer getPlayer();void queue(Runnable work); }
}
