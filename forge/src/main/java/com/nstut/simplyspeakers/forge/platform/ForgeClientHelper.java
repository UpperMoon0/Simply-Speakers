package com.nstut.simplyspeakers.forge.platform;

import com.nstut.simplyspeakers.client.ClientEvents;
import com.nstut.simplyspeakers.platform.services.IClientHelper;
import net.minecraft.core.BlockPos;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.util.function.Consumer;

public class ForgeClientHelper implements IClientHelper {
    @Override
    public void openSpeakerScreen(BlockPos pos) {
        ClientEvents.openSpeakerScreen(pos);
    }

    @Override
    public void openFileDialog(String filter, Consumer<File> callback) {
        new Thread(() -> {
            String result = TinyFileDialogs.tinyfd_openFileDialog("Open Audio File", "", null, filter, false);
            if (result != null) {
                callback.accept(new File(result));
            }
        }).start();
    }
}