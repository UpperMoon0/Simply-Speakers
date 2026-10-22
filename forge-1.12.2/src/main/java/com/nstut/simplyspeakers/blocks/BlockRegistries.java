package com.nstut.simplyspeakers.blocks;
import net.minecraft.block.Block;
import com.nstut.simplyspeakers.compat.RegistryValue;
public final class BlockRegistries {
 public static final RegistryValue<Block> SPEAKER=new RegistryValue<>(new SpeakerBlock());
 public static final RegistryValue<Block> PROXY_SPEAKER=new RegistryValue<>(new ProxySpeakerBlock());
 public static final RegistryValue<Block> REDSTONE_CONTROLLER=new RegistryValue<>(new RedstoneControllerBlock());
}
