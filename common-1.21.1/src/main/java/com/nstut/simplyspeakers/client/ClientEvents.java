package com.nstut.simplyspeakers.client;

import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import com.nstut.simplyspeakers.client.screens.SpeakerScreen;
import com.nstut.simplyspeakers.client.screens.ProxySpeakerScreen;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.network.PlayAudioPacketS2C;
import com.nstut.simplyspeakers.testing.LiveJoinTestProtocol;

public class ClientEvents {

    public static void register() {
        SimplySpeakers.LOGGER.info("Registering client events...");
        ClientTickEvent.CLIENT_POST.register(ClientEvents::onClientTick);
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(ClientEvents::onPlayerLoggedOut);
        ClientLifecycleEvent.CLIENT_STOPPING.register(ClientEvents::onClientStopping);
        PlayAudioPacketS2C.startLiveJoinProbe();
        SimplySpeakers.LOGGER.info("Client events registered");
    }

    private static void onClientTick(Minecraft client) {
        if (client.player != null && client.level != null) {
            PlayAudioPacketS2C.processPendingPlays();
            finishLiveJoinTest(client);
            ClientAudioPlayer.updateSpeakerVolumes();
        }
    }

    private static void finishLiveJoinTest(Minecraft client) {
        if (LiveJoinTestProtocol.isEnabled()
                && LiveJoinTestProtocol.passed()
                && LiveJoinTestProtocol.markReported()) {
            SimplySpeakers.LOGGER.info(LiveJoinTestProtocol.PASS_MARKER);
            LiveJoinTestProtocol.stopClient(client::stop);
        }
    }

    private static void onPlayerLoggedOut(net.minecraft.client.player.LocalPlayer player) {
        SimplySpeakers.LOGGER.info("CLIENT_PLAYER_QUIT event fired - Player logging out, initiating fast audio cleanup...");
        ClientAudioPlayer.stopAll();
        PlayAudioPacketS2C.clearPendingPlays();
        ClientAudioPlayer.clearAudioList();
        ClientSpeakerRegistry.clear();
        com.nstut.simplyspeakers.Config.restoreLocalConfig();
    }
    
    private static void onClientStopping(Minecraft client) {
        SimplySpeakers.LOGGER.info("CLIENT_STOPPING event fired - Client stopping, initiating audio cleanup...");
        ClientAudioPlayer.stopAll();
        PlayAudioPacketS2C.clearPendingPlays();
        ClientAudioPlayer.clearAudioList();
        ClientSpeakerRegistry.clear();
        com.nstut.simplyspeakers.Config.restoreLocalConfig();
    }

    public static void openSpeakerScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new SpeakerScreen(pos));
    }
    
    public static void openProxySpeakerScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new ProxySpeakerScreen(pos));
    }
}
