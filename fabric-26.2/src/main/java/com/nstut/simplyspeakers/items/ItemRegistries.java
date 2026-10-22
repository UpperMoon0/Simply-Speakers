package com.nstut.simplyspeakers.items;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.BlockRegistries;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import java.util.List;
public final class ItemRegistries {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(SimplySpeakers.MOD_ID, Registries.ITEM);
    private static Item.Properties properties(String id) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(SimplySpeakers.MOD_ID, id)));
    }
    public static final RegistrySupplier<BlockItem> SPEAKER = ITEMS.register("speaker", () -> new BlockItem(BlockRegistries.SPEAKER.get(), properties("speaker").useBlockDescriptionPrefix()));
    public static final RegistrySupplier<BlockItem> PROXY_SPEAKER = ITEMS.register("proxy_speaker", () -> new BlockItem(BlockRegistries.PROXY_SPEAKER.get(), properties("proxy_speaker").useBlockDescriptionPrefix()));
    public static final RegistrySupplier<BlockItem> REDSTONE_CONTROLLER = ITEMS.register("redstone_controller", () -> new BlockItem(BlockRegistries.REDSTONE_CONTROLLER.get(), properties("redstone_controller").useBlockDescriptionPrefix()));
    public static final RegistrySupplier<PortableSpeakerItem> PORTABLE_SPEAKER = ITEMS.register("portable_speaker", () -> new PortableSpeakerItem(properties("portable_speaker")));
    public static final RegistrySupplier<Item> AUDIO_CIRCUIT = ITEMS.register("audio_circuit", () -> new Item(properties("audio_circuit")));
    public static final List<RegistrySupplier<? extends Item>> ITEM_LIST = List.of(SPEAKER, PROXY_SPEAKER, REDSTONE_CONTROLLER, PORTABLE_SPEAKER, AUDIO_CIRCUIT);
    private ItemRegistries() {}
}
