package com.nstut.fabric.simplyspeakers;
import com.nstut.simplyspeakers.client.ClientEvents;
import com.nstut.simplyspeakers.network.PacketRegistries;
import net.fabricmc.api.ClientModInitializer;
public final class SimplySpeakersFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        PacketRegistries.registerS2C();
        ClientEvents.register();
    }
}
