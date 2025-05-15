package com.nstut.simplyspeakers.blocks.entities;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.BlockRegistries;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Registry for all block entities in the Simply Speakers mod.
 */
public class BlockEntityRegistries {
    // Create a DeferredRegister for block entities
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = 
            DeferredRegister.create(SimplySpeakers.MOD_ID, Registries.BLOCK_ENTITY_TYPE);

    // Register the speaker block entity
    public static final RegistrySupplier<BlockEntityType<SpeakerBlockEntity>> SPEAKER = 
            BLOCK_ENTITIES.register("speaker", () ->
                    BlockEntityType.Builder.of(SpeakerBlockEntity::new, BlockRegistries.SPEAKER.get())
                            .build(null));

    /**
     * Initializes the block entity registry.
     */
    public static void init() {
        BLOCK_ENTITIES.register();
    }
}
