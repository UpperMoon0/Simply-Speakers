package com.nstut.neoforge.simplyspeakers;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = SimplySpeakers.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class SimplySpeakersClientEvents {
    private SimplySpeakersClientEvents() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            ClientAudioPlayer.updateSpatialAudio();
        }
    }
}
