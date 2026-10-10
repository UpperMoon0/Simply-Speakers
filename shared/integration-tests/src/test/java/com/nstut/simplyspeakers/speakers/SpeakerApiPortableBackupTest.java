package com.nstut.simplyspeakers.speakers;

import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.api.SpeakerApi;
import com.nstut.simplyspeakers.portable.PortableSpeakerManager;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Public discovery must not expose portable persistence copies as phantom networks. */
class SpeakerApiPortableBackupTest {
    private static final String AUDIO = "https://example.invalid/portable-backup.wav";

    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach void reset() { ServerSpeakerRegistry.resetForWorld(); }
    @AfterEach void cleanup() { ServerSpeakerRegistry.resetForWorld(); }

    private static SpeakerState named(String name) {
        SpeakerState state = new SpeakerState(AUDIO, "portable-backup.wav", false, false, -1);
        state.setNetworkName(name);
        state.getPlaylist().add(AUDIO, "portable-backup.wav");
        return state;
    }

    @Test void namedBackupsAreHiddenWithoutHidingRealPortableOrLinkedNetworks() {
        String first = UUID.randomUUID().toString(), second = UUID.randomUUID().toString();
        String backup = "simplyspeakers:portable/portable_" + first;
        ServerSpeakerRegistry.updateSpeakerStateByFullKey("minecraft:overworld/net_station", named("Station"));
        ServerSpeakerRegistry.updateSpeakerStateByFullKey(backup, named("Station"));
        ServerSpeakerRegistry.updateSpeakerStateByFullKey("simplyspeakers:portable/portable_" + second, named("Station"));
        ServerSpeakerRegistry.updateSpeakerStateByFullKey("minecraft:overworld/portable_" + first, named("Walking"));

        var networks = SpeakerApi.listNamedNetworks(null);
        assertEquals(2, networks.size());
        assertTrue(networks.containsKey("Station"));
        assertTrue(networks.containsKey("Walking"));
        assertTrue(networks.values().stream().noneMatch(value -> value.contains("simplyspeakers:portable/")));
        assertEquals(4, ServerSpeakerRegistry.getAllSpeakerStates().size(), "discovery must not delete persistence copies");
        assertNotNull(ServerSpeakerRegistry.getSpeakerStateByFullKey(backup));
        assertTrue(PortableSpeakerManager.isBackupStateKey(backup));
        assertFalse(PortableSpeakerManager.isBackupStateKey("minecraft:overworld/portable_" + first));
        assertFalse(PortableSpeakerManager.isBackupStateKey(null));
    }

    @Test void hiddenBackupsStillParticipateInDeletedAudioCleanup() {
        String backup = "simplyspeakers:portable/portable_" + UUID.randomUUID();
        ServerSpeakerRegistry.updateSpeakerStateByFullKey(backup, named("Stored speaker"));
        assertTrue(SpeakerApi.listNamedNetworks(null).isEmpty());

        ServerSpeakerControlService.removeDeletedAudio(null, AUDIO);

        SpeakerState saved = ServerSpeakerRegistry.getSpeakerStateByFullKey(backup);
        assertNotNull(saved, "backup must remain available to restore the item");
        assertFalse(saved.hasAudio());
        assertTrue(saved.getPlaylist().isEmpty());
        assertTrue(SpeakerApi.listNamedNetworks(null).isEmpty());
    }
}
