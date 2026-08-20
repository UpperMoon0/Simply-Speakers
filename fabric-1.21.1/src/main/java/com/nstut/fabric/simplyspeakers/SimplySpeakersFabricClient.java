package com.nstut.fabric.simplyspeakers;

import com.nstut.simplyspeakers.client.ClientEvents;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import com.nstut.simplyspeakers.network.PacketRegistries;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/**
 * Fabric client mod initializer for Simply Speakers.
 * Registers client-side only packet receivers (S2C).
 */
public class SimplySpeakersFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Register S2C packet receivers on client side only
        PacketRegistries.registerS2C();
        ClientEvents.register();
        WorldRenderEvents.END.register(context -> ClientAudioPlayer.updateSpatialAudio());
    }
}
