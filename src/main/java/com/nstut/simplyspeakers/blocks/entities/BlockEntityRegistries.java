package com.nstut.simplyspeakers.blocks.entities;

import com.nstut.simplyspeakers.SimplySpeakers;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.nstut.simplyspeakers.blocks.BlockRegistries;

@SuppressWarnings("ConstantConditions")
public class BlockEntityRegistries {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SimplySpeakers.MOD_ID);

    public static final RegistryObject<BlockEntityType<SpeakerBlockEntity>> SPEAKER = BLOCK_ENTITIES.register("speaker", () -> BlockEntityType.Builder.of(SpeakerBlockEntity::new, BlockRegistries.SPEAKER.get()).build(null));
}