package com.nstut.simplyspeakers.platform.services;

import com.nstut.simplyspeakers.items.ItemRegistries;
import net.minecraft.world.item.Item;

/**
 * Helper for platform-specific item functionality.
 */
public interface IItemHelper {
    /**
     * Gets the speaker item.
     *
     * @return The speaker item.
     */
    default Item getSpeakerItem() {
        return ItemRegistries.SPEAKER.get();
    }
}
