package com.nstut.simplyspeakers.compat;
import net.minecraft.entity.player.EntityPlayer;
public final class LegacyNetwork {
 public interface PacketContext { EntityPlayer getPlayer();default boolean isClient(){return false;}void queue(Runnable work); }
}
