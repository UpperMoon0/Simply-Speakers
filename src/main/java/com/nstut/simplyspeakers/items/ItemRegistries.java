package com.nstut.simplyspeakers.items;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.BlockRegistries;

import java.util.HashSet;
import java.util.Set;

public class ItemRegistries {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SimplySpeakers.MOD_ID);

    public static final RegistryObject<Item> SPEAKER = ITEMS.register("speaker", () -> new BlockItem(BlockRegistries.SPEAKER.get(), new Item.Properties()));


    public static final Set<RegistryObject<Item>> ITEM_SET;

    static {
        ITEM_SET = new HashSet<>(ITEMS.getEntries());
    }
}
