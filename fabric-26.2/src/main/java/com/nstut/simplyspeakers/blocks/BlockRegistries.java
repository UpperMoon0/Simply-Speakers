package com.nstut.simplyspeakers.blocks;
import com.nstut.simplyspeakers.SimplySpeakers;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
public final class BlockRegistries {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(SimplySpeakers.MOD_ID, Registries.BLOCK);
    private static BlockBehaviour.Properties properties(String id) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()
            .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(SimplySpeakers.MOD_ID, id)));
    }
    public static final RegistrySupplier<Block> SPEAKER = BLOCKS.register("speaker", () -> new SpeakerBlock(properties("speaker")));
    public static final RegistrySupplier<Block> PROXY_SPEAKER = BLOCKS.register("proxy_speaker", () -> new ProxySpeakerBlock(properties("proxy_speaker")));
    public static final RegistrySupplier<Block> REDSTONE_CONTROLLER = BLOCKS.register("redstone_controller", () -> new RedstoneControllerBlock(properties("redstone_controller")));
    private BlockRegistries() {}
}
