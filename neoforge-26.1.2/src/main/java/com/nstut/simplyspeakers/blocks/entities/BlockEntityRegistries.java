package com.nstut.simplyspeakers.blocks.entities;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.BlockRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BlockEntityRegistries {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SimplySpeakers.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpeakerBlockEntity>> SPEAKER =
            BLOCK_ENTITIES.register("speaker", () -> new BlockEntityType<>(SpeakerBlockEntity::new, BlockRegistries.SPEAKER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProxySpeakerBlockEntity>> PROXY_SPEAKER =
            BLOCK_ENTITIES.register("proxy_speaker", () -> new BlockEntityType<>(ProxySpeakerBlockEntity::new, BlockRegistries.PROXY_SPEAKER.get()));

    private BlockEntityRegistries() {
    }
}
