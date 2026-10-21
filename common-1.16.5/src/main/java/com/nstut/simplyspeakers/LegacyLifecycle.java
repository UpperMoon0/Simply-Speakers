package com.nstut.simplyspeakers;

import com.nstut.simplyspeakers.speakers.ServerPlaybackManager;
import com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry;
import me.shedaniel.architectury.event.events.CommandRegistrationEvent;
import me.shedaniel.architectury.event.events.LifecycleEvent;
import me.shedaniel.architectury.event.events.TickEvent;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Path;

/** Same world lifecycle on both official 1.16.5 loaders. */
public final class LegacyLifecycle {
    private static boolean registered;
    private LegacyLifecycle() {}
    public static void register() {
        if (registered) return;
        registered=true;
        CommandRegistrationEvent.EVENT.register((dispatcher,selection) -> com.nstut.simplyspeakers.commands.SpeakerCommands.register(dispatcher));
        LifecycleEvent.SERVER_STARTING.register(server -> {
            Path root=server.getWorldPath(LevelResource.ROOT);
            SimplySpeakers.initializeAudio(root);
            ServerSpeakerRegistry.init(root);
        });
        LifecycleEvent.SERVER_STOPPING.register(server -> {
            com.nstut.simplyspeakers.portable.PortableSpeakerManager.shutdown(server);
            ServerSpeakerRegistry.flushDirty();
            SimplySpeakers.shutdownAudio();
        });
        LifecycleEvent.SERVER_STOPPED.register(server -> {
            ServerPlaybackManager.resetForWorld();
            ServerSpeakerRegistry.resetForWorld();
        });
        TickEvent.SERVER_POST.register(server -> {
            ServerPlaybackManager.serverTick(server);
            com.nstut.simplyspeakers.testing.IntegratedAudioServerProbe.tick(server);
            if (server.getTickCount()%6000==0) ServerSpeakerRegistry.flushDirty();
        });
    }
}
