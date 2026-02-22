package com.nstut.simplyspeakers;

import com.nstut.simplyspeakers.audio.AudioFileManager;
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
import java.nio.file.Path;

/**
 * Main class for the Simply Speakers mod.
 */
public class SimplySpeakers {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "simplyspeakers";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LoggerFactory.getLogger("Simply Speakers");
    private static AudioFileManager audioFileManager;
    private static boolean initialized = false;
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(MOD_ID, Registries.SOUND_EVENT);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(MOD_ID, Registries.CREATIVE_MODE_TAB);

    // Register sound events
    public static final ResourceLocation MUSIC_SOUND_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "custom.music");
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
     * NOTE: DeferredRegister registration is handled by platform-specific modules.
     * This method only handles common initialization logic.
     */
    public static void init() {
        // Guard against double initialization (can happen with Architectury transformer)
        if (initialized) {
            LOGGER.info("Simply Speakers already initialized, skipping");
            return;
        }
        initialized = true;
        
        LOGGER.info("Initializing Simply Speakers");
        
        // NOTE: DeferredRegister registration (SOUND_EVENTS, CREATIVE_TABS, BLOCKS, 
        // BLOCK_ENTITIES, ITEMS) must be done in platform-specific modules 
        // (e.g., SimplySpeakersForge for NeoForge) by calling .register() directly.
        // This is required for proper registration with the mod event bus.
        
        // NOTE: PacketRegistries.init() should NOT be called manually!
        // Architectury's transformer automatically registers the NetworkChannel
        // when getChannel() is called. Manual registration causes double registration.

        // Register client-side events only on the client
        if (Platform.getEnv().toString().equals("CLIENT")) {
            ClientEvents.register();
        }
        
        // Let each platform handle their own initialization
        Services.PLATFORM.init();
    }

    public static void initializeAudio(Path worldSavePath) {
        audioFileManager = new AudioFileManager(worldSavePath);
    }

    public static AudioFileManager getAudioFileManager() {
        return audioFileManager;
    }
}
