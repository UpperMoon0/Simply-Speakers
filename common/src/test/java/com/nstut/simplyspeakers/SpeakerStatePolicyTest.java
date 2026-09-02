package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeakerStatePolicyTest {

    @Test
    void playlistAccessorIsLazyButStable() {
        SpeakerState state = new SpeakerState();
        assertNotNull(state.getPlaylist());
        assertFalse(state.hasPlaylist());
        state.getPlaylist().add("a", "a.mp3");
        assertTrue(state.hasPlaylist());
        // Same instance returned on repeat access.
        assertTrue(state.getPlaylist() == state.getPlaylist());
    }

    @Test
    void networkNameIsTrimmedAndNullSafe() {
        SpeakerState state = new SpeakerState();
        state.setNetworkName("  Mall BGM  ");
        assertEquals("Mall BGM", state.getNetworkName());
        state.setNetworkName(null);
        assertEquals("", state.getNetworkName());
        assertFalse(state.hasNetworkName());
    }

    @Test
    void nullEnumSettersFallBackToDefaults() {
        SpeakerState state = new SpeakerState();
        state.setRedstoneMode(null);
        assertEquals(RedstoneMode.DEFAULT, state.getRedstoneMode());
        state.setAccessMode(null);
        assertEquals(SpeakerAccess.DEFAULT, state.getAccessMode());
    }

    @Test
    void trustMutationIgnoresNullPlayers() {
        SpeakerState state = new SpeakerState();
        UUID player = UUID.randomUUID();
        state.trustPlayer(null);
        assertTrue(state.getTrustedPlayers().isEmpty());
        state.trustPlayer(player);
        assertTrue(state.isTrusted(player));
        state.distrustPlayer(null);
        assertTrue(state.isTrusted(player));
        state.distrustPlayer(player);
        assertFalse(state.isTrusted(player));
    }

    @Test
    void copyPreservesTransportAndOwnership() {
        SpeakerState state = new SpeakerState();
        state.startPlaybackAt(100L, 12.5f);
        state.pauseAt(300L); // paused at 10s
        state.setNetworkName("Club");
        state.setOwnerUuid(UUID.randomUUID());
        state.setAccessMode(SpeakerAccess.OWNER_ONLY);
        state.setDirectionality(1.0f);
        state.setConeAngleDegrees(60);

        SpeakerState copy = state.copy();
        assertTrue(copy.isPaused());
        assertTrue(copy.isPlaying());
        assertEquals(state.getPlaybackStartTick(), copy.getPlaybackStartTick());
        assertEquals(state.getPauseOffsetSeconds(), copy.getPauseOffsetSeconds(), 0.0001f);
        assertEquals("Club", copy.getNetworkName());
        assertEquals(state.getOwnerUuid(), copy.getOwnerUuid());
        assertEquals(SpeakerAccess.OWNER_ONLY, copy.getAccessMode());
        assertEquals(1.0f, copy.getDirectionality(), 0.0001f);
        assertEquals(60, copy.getConeAngleDegrees());

        // Resume on the copy must not affect the original.
        copy.resumeAt(900L);
        assertTrue(state.isPaused());
        assertFalse(copy.isPaused());
    }

    @Test
    void pendingSeekTargetSurvivesUntilNextStart() {
        SpeakerState state = new SpeakerState();
        state.seekTo(33.0f, 0L, 0.0f);
        assertFalse(state.isPlaying());
        assertEquals(33.0f, state.getPlaybackPositionSeconds(50L), 0.0001f);
        // Starting playback honours the pending target as the initial offset.
        state.startPlaybackAt(100L, state.getPauseOffsetSeconds());
        assertEquals(34.0f, state.getPlaybackPositionSeconds(120L), 0.0001f);
    }
}
