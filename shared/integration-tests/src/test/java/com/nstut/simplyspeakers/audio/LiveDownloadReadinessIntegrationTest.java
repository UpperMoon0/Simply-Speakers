package com.nstut.simplyspeakers.audio;

import com.nstut.simplyspeakers.testing.LivePlaybackServerProbe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual opt-in server barrier: no carrier control can preempt the slower decoder. */
class LiveDownloadReadinessIntegrationTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static Field field(String name) throws Exception {
        Field field = LivePlaybackServerProbe.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static Method method(String name, Class<?>... types) throws Exception {
        Method method = LivePlaybackServerProbe.class.getDeclaredMethod(name, types); method.setAccessible(true); return method;
    }
    @Test @SuppressWarnings("unchecked") void bothCurrentParticipantsMustAcknowledgeTheSameAudioBeforeInitialControlsAdvance() throws Exception {
        Map<Field,Object> saved = new HashMap<>();
        for (String name : List.of("player","observer","audio","currentServer","phase","done","initialStartedPending")) {
            Field field = field(name); saved.put(field,field.get(null));
        }
        Set<UUID> ready = (Set<UUID>)field("initialDownloads").get(null);
        Set<UUID> savedReady = new HashSet<>(ready); ready.clear();
        ServerPlayer first = mock(ServerPlayer.class), second = mock(ServerPlayer.class), outsider = mock(ServerPlayer.class);
        UUID firstId = UUID.randomUUID(), secondId = UUID.randomUUID(); String audioId = UUID.randomUUID().toString();
        when(first.getUUID()).thenReturn(firstId); when(second.getUUID()).thenReturn(secondId);
        when(outsider.getUUID()).thenReturn(UUID.randomUUID());
        MinecraftServer server = mock(MinecraftServer.class, RETURNS_DEEP_STUBS);
        when(server.getPlayerList().getPlayer(firstId)).thenReturn(first);
        when(server.getPlayerList().getPlayer(secondId)).thenReturn(second);
        Method acknowledge = method("acknowledgeInitialDownload",ServerPlayer.class,String.class);
        Method readyCheck = method("initialDownloadsReady");
        try {
            field("player").set(null,first); field("observer").set(null,second); field("currentServer").set(null,server);
            field("audio").set(null,new AudioFileMetadata(audioId,"verification.wav",firstId.toString(),120));
            field("phase").setInt(null,0); field("done").setBoolean(null,false); field("initialStartedPending").setBoolean(null,false);
            method("acknowledge",String.class).invoke(null,"started");
            assertTrue(field("initialStartedPending").getBoolean(null)); assertEquals(0,field("phase").getInt(null));
            acknowledge.invoke(null,first,audioId); acknowledge.invoke(null,first,audioId);
            assertEquals(1,ready.size()); assertEquals(false,readyCheck.invoke(null));
            assertThrows(InvocationTargetException.class,()->acknowledge.invoke(null,outsider,audioId));
            assertThrows(InvocationTargetException.class,()->acknowledge.invoke(null,second,UUID.randomUUID().toString()));
            when(server.getPlayerList().getPlayer(secondId)).thenReturn(null);
            assertThrows(InvocationTargetException.class,()->acknowledge.invoke(null,second,audioId));
            assertEquals(1,ready.size());
            when(server.getPlayerList().getPlayer(secondId)).thenReturn(second);
            acknowledge.invoke(null,second,audioId);
            assertEquals(2,ready.size()); assertEquals(true,readyCheck.invoke(null));
        } finally {
            ready.clear(); ready.addAll(savedReady);
            for (var entry : saved.entrySet()) entry.getKey().set(null,entry.getValue());
        }
    }
}
