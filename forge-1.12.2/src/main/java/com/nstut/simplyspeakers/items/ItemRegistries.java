package com.nstut.simplyspeakers.items;
import net.minecraft.item.*;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.BlockRegistries;
import com.nstut.simplyspeakers.compat.RegistryValue;
public final class ItemRegistries {
 private static RegistryValue<Item> item(String id,Item item){item.setRegistryName("simplyspeakers",id);item.setTranslationKey("simplyspeakers."+id);item.setCreativeTab(SimplySpeakers.CREATIVE_TAB);return new RegistryValue<>(item);}
 public static final RegistryValue<Item> SPEAKER=item("speaker",new ItemBlock(BlockRegistries.SPEAKER.get()));
 public static final RegistryValue<Item> PROXY_SPEAKER=item("proxy_speaker",new ItemBlock(BlockRegistries.PROXY_SPEAKER.get()));
 public static final RegistryValue<Item> REDSTONE_CONTROLLER=item("redstone_controller",new ItemBlock(BlockRegistries.REDSTONE_CONTROLLER.get()));
 public static final RegistryValue<Item> PORTABLE_SPEAKER=item("portable_speaker",new PortableSpeakerItem());
 public static final RegistryValue<Item> AUDIO_CIRCUIT=item("audio_circuit",new Item());
 public static final java.util.List<RegistryValue<Item>> ITEM_LIST=java.util.Arrays.asList(SPEAKER,PROXY_SPEAKER,REDSTONE_CONTROLLER,PORTABLE_SPEAKER,AUDIO_CIRCUIT);
}
