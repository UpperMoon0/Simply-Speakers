package com.nstut.simplyspeakers.blocks.entities;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.BlockRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.architectury.registry.registries.DeferredRegister;

public final class BlockEntityRegistries {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(SimplySpeakers.MOD_ID, Registries.BLOCK_ENTITY_TYPE);
    public static final RegistrySupplier<BlockEntityType<SpeakerBlockEntity>> SPEAKER =
            BLOCK_ENTITIES.register("speaker", () -> new BlockEntityType<>(SpeakerBlockEntity::new, java.util.Set.of(BlockRegistries.SPEAKER.get())));
    public static final RegistrySupplier<BlockEntityType<ProxySpeakerBlockEntity>> PROXY_SPEAKER =
            BLOCK_ENTITIES.register("proxy_speaker", () -> new BlockEntityType<>(ProxySpeakerBlockEntity::new, java.util.Set.of(BlockRegistries.PROXY_SPEAKER.get())));

    public static final RegistrySupplier<BlockEntityType<RedstoneControllerBlockEntity>> REDSTONE_CONTROLLER = BLOCK_ENTITIES.register("redstone_controller", () -> new BlockEntityType<>(RedstoneControllerBlockEntity::new, java.util.Set.of(BlockRegistries.REDSTONE_CONTROLLER.get())));

    private BlockEntityRegistries() {
    }
}
