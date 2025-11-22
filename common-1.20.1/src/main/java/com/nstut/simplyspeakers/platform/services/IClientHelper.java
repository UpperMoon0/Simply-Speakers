package com.nstut.simplyspeakers.platform.services;

import net.minecraft.core.BlockPos;

import java.io.File;
import java.util.function.Consumer;

public interface IClientHelper {
    void openSpeakerScreen(BlockPos pos);
    
    void openProxySpeakerScreen(BlockPos pos);

    void openFileDialog(String filter, Consumer<File> callback);
}