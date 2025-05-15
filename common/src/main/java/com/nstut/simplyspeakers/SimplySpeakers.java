package com.nstut.simplyspeakers;

import com.nstut.simplyspeakers.blocks.BlockRegistries;
import com.nstut.simplyspeakers.blocks.entities.BlockEntityRegistries;
import com.nstut.simplyspeakers.items.ItemRegistries;
import com.nstut.simplyspeakers.platform.Services;
import com.nstut.simplyspeakers.network.PacketRegistries;
import com.nstut.simplyspeakers.client.ClientEvents;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.architectury.platform.Platform;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main class for the Simply Speakers mod.
 */
public class SimplySpeakers {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "simplyspeakers";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LoggerFactory.getLogger("Simply Speakers");   
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(MOD_ID, Registries.SOUND_EVENT);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(MOD_ID, Registries.CREATIVE_MODE_TAB);

    // Register sound events
    public static final ResourceLocation MUSIC_SOUND_ID = new ResourceLocation(MOD_ID, "custom.music");
    public static final RegistrySupplier<SoundEvent> MUSIC = SOUND_EVENTS.register("custom.music",
            () -> SoundEvent.createVariableRangeEvent(MUSIC_SOUND_ID));

    public static final RegistrySupplier<CreativeModeTab> CREATIVE_TAB = CREATIVE_TABS.register("tab", () ->
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)  // Adding Row.TOP and position 0
                    .title(Component.translatable("itemGroup." + MOD_ID + ".tab"))
                    .icon(() -> new ItemStack(Services.ITEMS.getSpeakerItem()))
                    .displayItems((parameters, output) -> {
                        // Add all items from our item list to the creative tab
                        for (RegistrySupplier<net.minecraft.world.item.Item> item : ItemRegistries.ITEM_LIST) {
                            output.accept(item.get());
                        }
                    })
                    .build()
    );

    /**
     * Initializes the mod.
     */    
    public static void init() {
        LOGGER.info("Initializing Simply Speakers");
          // Register all of our DeferredRegisters
        SOUND_EVENTS.register();
        CREATIVE_TABS.register(); // Register the creative tab
        BlockRegistries.init();
        BlockEntityRegistries.init();
        ItemRegistries.init();
        PacketRegistries.init();

        // Register client-side events only on the client
        if (Platform.getEnv().toString().equals("CLIENT")) {
            ClientEvents.register();
        }
        
        // Let each platform handle their own initialization
        Services.PLATFORM.init();
    }
}
