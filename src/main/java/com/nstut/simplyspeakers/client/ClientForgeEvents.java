package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.SimplySpeakers;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SimplySpeakers.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientForgeEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // Only run logic at the end of the tick to ensure player/world state is updated
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            // Ensure player and level are loaded before updating volumes
            if (mc.player != null && mc.level != null) {
                ClientAudioPlayer.updateSpeakerVolumes();
            }
        }
    }
}
