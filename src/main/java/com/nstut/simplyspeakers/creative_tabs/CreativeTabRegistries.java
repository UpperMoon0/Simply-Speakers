package com.nstut.simplyspeakers.creative_tabs;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.items.ItemRegistries;

public class CreativeTabRegistries {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SimplySpeakers.MOD_ID);
    public static final RegistryObject<CreativeModeTab> SIMPLY_SPEAKER_TAB = CREATIVE_MODE_TABS.register(SimplySpeakers.MOD_ID, () -> CreativeModeTab.builder()
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> ItemRegistries.SPEAKER.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                for (RegistryObject<Item> i : ItemRegistries.ITEM_SET) {
                    output.accept(i.get());
                }
            })
            .title(Component.translatable("itemGroup.simplyspeakers"))
            .build());
}
