package com.nstut.simplyspeakers.blocks;

import com.nstut.simplyspeakers.SimplySpeakers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BlockRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SimplySpeakers.MOD_ID);
    public static final DeferredBlock<Block> SPEAKER = BLOCKS.registerBlock("speaker", SpeakerBlock::new,
            properties -> BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    public static final DeferredBlock<Block> PROXY_SPEAKER = BLOCKS.registerBlock("proxy_speaker", ProxySpeakerBlock::new,
            properties -> BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());

    private BlockRegistries() {
    }
}
