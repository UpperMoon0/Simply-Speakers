package com.nstut.simplyspeakers.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.nstut.simplyspeakers.SimplySpeakers;

public class BlockRegistries {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SimplySpeakers.MOD_ID);

    public static final RegistryObject<Block> SPEAKER = BLOCKS.register("speaker", SpeakerBlock::new);
}
