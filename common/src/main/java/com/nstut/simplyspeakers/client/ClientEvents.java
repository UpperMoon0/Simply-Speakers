package com.nstut.simplyspeakers.client;

import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import com.nstut.simplyspeakers.client.screens.SpeakerScreen;
import com.nstut.simplyspeakers.client.screens.ProxySpeakerScreen;

public class ClientEvents {

    private static int volumeUpdateTicks = 0;
    private static final int VOLUME_UPDATE_INTERVAL = 5; // Update every 5 ticks instead of every tick

    public static void register() {
        ClientTickEvent.CLIENT_POST.register(ClientEvents::onClientTick);
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(ClientEvents::onPlayerLoggedOut);
    }

    private static void onClientTick(Minecraft client) {
        // PERFORMANCE FIX: Reduce volume update frequency to prevent excessive OpenAL calls during world operations
        if (client.player != null && client.level != null) {
            volumeUpdateTicks++;
            if (volumeUpdateTicks >= VOLUME_UPDATE_INTERVAL) {
                ClientAudioPlayer.updateSpeakerVolumes();
                volumeUpdateTicks = 0;
            }
        }
    }

    private static void onPlayerLoggedOut(net.minecraft.client.player.LocalPlayer player) {
        System.out.println("[SimplySpeakers] Player logging out, initiating fast audio cleanup...");
        ClientAudioPlayer.stopAll();
        ClientAudioPlayer.clearAudioList();
    }

    public static void openSpeakerScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new SpeakerScreen(pos));
    }
    
    public static void openProxySpeakerScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new ProxySpeakerScreen(pos));
    }
}
