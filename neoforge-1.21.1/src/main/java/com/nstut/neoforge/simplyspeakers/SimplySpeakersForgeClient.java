package com.nstut.neoforge.simplyspeakers;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.network.PacketRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = SimplySpeakers.MOD_ID, dist = Dist.CLIENT)
public final class SimplySpeakersForgeClient {
    public SimplySpeakersForgeClient(IEventBus modEventBus) {
        // Register client setup event
        modEventBus.addListener(this::onClientSetup);
    }
    
    private void onClientSetup(FMLClientSetupEvent event) {
        // Ensure packet registration happens on client side
        // This is needed for C2S packets to have their codecs registered
        PacketRegistries.init();
    }
}
