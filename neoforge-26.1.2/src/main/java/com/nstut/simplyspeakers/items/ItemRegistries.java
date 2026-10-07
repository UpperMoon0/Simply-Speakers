package com.nstut.simplyspeakers.items;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.BlockRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class ItemRegistries {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SimplySpeakers.MOD_ID);
    public static final DeferredItem<BlockItem> SPEAKER = ITEMS.registerSimpleBlockItem("speaker", BlockRegistries.SPEAKER);
    public static final DeferredItem<BlockItem> PROXY_SPEAKER = ITEMS.registerSimpleBlockItem("proxy_speaker", BlockRegistries.PROXY_SPEAKER);
    public static final DeferredItem<BlockItem> REDSTONE_CONTROLLER = ITEMS.registerSimpleBlockItem("redstone_controller", BlockRegistries.REDSTONE_CONTROLLER);
    public static final DeferredItem<Item> AUDIO_CIRCUIT = ITEMS.registerSimpleItem("audio_circuit");
    public static final List<DeferredItem<? extends Item>> ITEM_LIST = List.of(SPEAKER, PROXY_SPEAKER, REDSTONE_CONTROLLER, AUDIO_CIRCUIT);

    private ItemRegistries() {
    }
}
