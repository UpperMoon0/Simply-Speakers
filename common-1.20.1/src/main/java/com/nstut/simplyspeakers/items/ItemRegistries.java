package com.nstut.simplyspeakers.items;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.Registries;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.BlockRegistries;

import java.util.ArrayList;
import java.util.List;

public class ItemRegistries {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(SimplySpeakers.MOD_ID, Registries.ITEM);

    public static final RegistrySupplier<Item> SPEAKER = ITEMS.register("speaker", () -> new BlockItem(BlockRegistries.SPEAKER.get(), new Item.Properties()));
    public static final RegistrySupplier<Item> PROXY_SPEAKER = ITEMS.register("proxy_speaker", () -> new BlockItem(BlockRegistries.PROXY_SPEAKER.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> REDSTONE_CONTROLLER = ITEMS.register("redstone_controller", () -> new BlockItem(BlockRegistries.REDSTONE_CONTROLLER.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> PORTABLE_SPEAKER = ITEMS.register("portable_speaker", () -> new PortableSpeakerItem(new Item.Properties()));

    public static final RegistrySupplier<Item> AUDIO_CIRCUIT = ITEMS.register("audio_circuit", () -> new Item(new Item.Properties()));

    // Add a list of items for use in creative tab
    public static final List<RegistrySupplier<Item>> ITEM_LIST = new ArrayList<>();

    static {
        // Add items to the list
        ITEM_LIST.add(SPEAKER);
        ITEM_LIST.add(PROXY_SPEAKER);
        ITEM_LIST.add(REDSTONE_CONTROLLER);
        ITEM_LIST.add(PORTABLE_SPEAKER);
        ITEM_LIST.add(AUDIO_CIRCUIT);
    }

    public static void init() {
        ITEMS.register();
    }
}
