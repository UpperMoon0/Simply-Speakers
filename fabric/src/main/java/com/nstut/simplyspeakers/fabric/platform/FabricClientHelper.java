package com.nstut.simplyspeakers.fabric.platform;

import com.nstut.simplyspeakers.client.ClientEvents;
import com.nstut.simplyspeakers.platform.services.IClientHelper;
import net.minecraft.core.BlockPos;

public class FabricClientHelper implements IClientHelper {
    @Override
    public void openSpeakerScreen(BlockPos pos) {
        ClientEvents.openSpeakerScreen(pos);
    }
}