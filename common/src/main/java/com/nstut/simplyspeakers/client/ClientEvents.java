package com.nstut.simplyspeakers.client;

import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import net.minecraft.client.Minecraft;

public class ClientEvents {

    public static void register() {
        ClientTickEvent.CLIENT_POST.register(ClientEvents::onClientTick);
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(ClientEvents::onPlayerLoggedOut);
    }

    private static void onClientTick(Minecraft client) {
        // Ensure player and level are loaded before updating volumes
        if (client.player != null && client.level != null) {
            ClientAudioPlayer.updateSpeakerVolumes();
        }
    }

    private static void onPlayerLoggedOut(net.minecraft.client.player.LocalPlayer player) {
        ClientAudioPlayer.stopAll();
    }
}
