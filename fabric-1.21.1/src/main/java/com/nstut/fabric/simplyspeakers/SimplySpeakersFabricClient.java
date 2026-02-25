package com.nstut.fabric.simplyspeakers;

import com.nstut.simplyspeakers.network.PacketRegistries;
import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric client mod initializer for Simply Speakers.
 * Registers client-side only packet receivers (S2C).
 */
public class SimplySpeakersFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Register S2C packet receivers on client side only
        PacketRegistries.registerS2C();
    }
}
