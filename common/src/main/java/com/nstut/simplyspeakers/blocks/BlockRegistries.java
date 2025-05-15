package com.nstut.simplyspeakers.blocks;

import com.nstut.simplyspeakers.SimplySpeakers;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;

/**
 * Registry for all blocks in the Simply Speakers mod.
 */
public class BlockRegistries {
    // Create a DeferredRegister for blocks
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(SimplySpeakers.MOD_ID, Registries.BLOCK);

    // Register the speaker block
    public static final RegistrySupplier<Block> SPEAKER = BLOCKS.register("speaker", SpeakerBlock::new);

    /**
     * Initializes the block registry.
     */
    public static void init() {
        BLOCKS.register();
    }
}
