package com.nstut.simplyspeakers.client;

import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import com.nstut.simplyspeakers.client.screens.SpeakerScreen;
import com.nstut.simplyspeakers.client.screens.ProxySpeakerScreen;
import com.nstut.simplyspeakers.network.PlayAudioPacketS2C;
import com.nstut.simplyspeakers.testing.LiveJoinTestProtocol;

public class ClientEvents {

    public static void register() {
        ClientTickEvent.CLIENT_POST.register(ClientEvents::onClientTick);
        // Also update mobile poses between ticks for smooth sound while walking.
        dev.architectury.event.events.client.ClientGuiEvent.RENDER_HUD.register((graphics, delta) -> ClientAudioPlayer.updateSpeakerVolumes());
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(ClientEvents::onPlayerLoggedOut);
        ClientPlayerEvent.CLIENT_PLAYER_RESPAWN.register(ClientEvents::onPlayerRespawn);
        PlayAudioPacketS2C.startLiveJoinProbe();
    }

    private static void onClientTick(Minecraft client) {
        ClientPortableSpeakers.tick();
        SpeakerUiPreviewProbe.tick(client);
        if (client.player != null && client.level != null) {
            PlayAudioPacketS2C.processPendingPlays();
            finishLiveJoinTest(client);
            LivePlaybackClientProbe.tick(client);
            ClientAudioPlayer.updateSpeakerVolumes();
            DirectionalPreview.tick(client);
        }
    }

    private static void finishLiveJoinTest(Minecraft client) {
        if (LiveJoinTestProtocol.isEnabled()
                && LiveJoinTestProtocol.passed()
                && LiveJoinTestProtocol.markReported()) {
            System.out.println(LiveJoinTestProtocol.PASS_MARKER);
            if (!com.nstut.simplyspeakers.testing.LivePlaybackServerProbe.enabled()) LiveJoinTestProtocol.stopClient(client::stop);
        }
    }

    private static void onPlayerLoggedOut(net.minecraft.client.player.LocalPlayer player) {
        System.out.println("[SimplySpeakers] Player logging out, initiating fast audio cleanup...");
        DirectionalPreview.clear();
        ClientAudioPlayer.stopAll();
        PlayAudioPacketS2C.clearPendingPlays();
        ClientAudioPlayer.clearAudioList();
        ClientSpeakerRegistry.clear();
        com.nstut.simplyspeakers.Config.restoreLocalConfig();
    }

    private static void onPlayerRespawn(net.minecraft.client.player.LocalPlayer oldPlayer, net.minecraft.client.player.LocalPlayer newPlayer) {
        if (oldPlayer == null || newPlayer == null || oldPlayer.level() == null || newPlayer.level() == null) return;

        if (!oldPlayer.level().dimension().equals(newPlayer.level().dimension())) {
            System.out.println("[SimplySpeakers] Player changed dimension ("
                    + oldPlayer.level().dimension() + " -> "
                    + newPlayer.level().dimension() + "), clearing client audio playback...");
            DirectionalPreview.clear();
            ClientAudioPlayer.stopAll();
            PlayAudioPacketS2C.clearPendingPlays();
            ClientSpeakerRegistry.clear();
        }
    }

    public static void openSpeakerScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new SpeakerScreen(pos));
    }
    
    public static void openRedstoneControllerScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new com.nstut.simplyspeakers.client.screens.RedstoneControllerScreen(pos));
    }

    public static void openProxySpeakerScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new ProxySpeakerScreen(pos));
    }
}
