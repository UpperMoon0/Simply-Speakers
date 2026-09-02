package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeakerStateTransportTest {
    private static final long START_TICK = 1000L;

    @Test
    void positionAdvancesWithTicksWhilePlaying() {
        SpeakerState state = new SpeakerState();
        state.startPlaybackAt(START_TICK, 0.0f);
        assertTrue(state.isPlaying());
        assertFalse(state.isPaused());
        assertEquals(0.0f, state.getPlaybackPositionSeconds(START_TICK), 0.0001f);
        assertEquals(2.5f, state.getPlaybackPositionSeconds(START_TICK + 50), 0.0001f);
    }

    @Test
    void startOffsetIsIncludedInPosition() {
        SpeakerState state = new SpeakerState();
        state.startPlaybackAt(START_TICK, 10.0f);
        assertEquals(15.0f, state.getPlaybackPositionSeconds(START_TICK + 100), 0.0001f);
    }

    @Test
    void eachStartCreatesANewPlaybackOccurrence() {
        SpeakerState state = new SpeakerState();
        state.startPlaybackAt(START_TICK, 0.0f);
        int first = state.getPlaybackSessionGeneration();
        state.startPlaybackAt(START_TICK, 0.0f); // same tick and same track is still a restart
        int restart = state.getPlaybackSessionGeneration();
        state.stopPlayback();
        state.startPlaybackAt(START_TICK, 0.0f); // stop -> play in the same tick must not reuse identity
        int replay = state.getPlaybackSessionGeneration();

        assertTrue(first > 0);
        assertNotEquals(first, restart);
        assertNotEquals(restart, replay);
    }

    @Test
    void resumeAndSeekKeepTheSamePlaybackOccurrence() {
        SpeakerState state = new SpeakerState();
        state.startPlaybackAt(START_TICK, 0.0f);
        int generation = state.getPlaybackSessionGeneration();
        state.pauseAt(START_TICK + 100);
        state.resumeAt(START_TICK + 200);
        state.seekTo(20.0f, START_TICK + 300, 100.0f);
        assertEquals(generation, state.getPlaybackSessionGeneration());
    }

    @Test
    void legacyPlayingStateCanAcquireGenerationWithoutRestart() {
        SpeakerState state = new SpeakerState("id", "file.mp3", true, false, START_TICK);
        assertEquals(0, state.getPlaybackSessionGeneration());
        assertEquals(1, state.ensurePlaybackSessionGeneration());
        assertEquals(1, state.ensurePlaybackSessionGeneration());
    }

    @Test
    void pauseFreezesPositionWithoutClearingIt() {
        SpeakerState state = new SpeakerState();
        state.startPlaybackAt(START_TICK, 0.0f);
        state.pauseAt(START_TICK + 600);
        assertTrue(state.isPaused());
        assertTrue(state.isPlaying());
        assertEquals(30.0f, state.getPauseOffsetSeconds(), 0.0001f);
        assertEquals(30.0f, state.getPlaybackPositionSeconds(START_TICK + 1200), 0.0001f);
    }

    @Test
    void resumeContinuesFromPreservedPosition() {
        SpeakerState state = new SpeakerState();
        state.startPlaybackAt(START_TICK, 0.0f);
        state.pauseAt(START_TICK + 600);
        state.resumeAt(START_TICK + 1400);
        assertFalse(state.isPaused());
        assertEquals(31.0f, state.getPlaybackPositionSeconds(START_TICK + 1420), 0.0001f);
    }

    @Test
    void seekWhilePlayingRebasesStartTick() {
        SpeakerState state = new SpeakerState();
        state.startPlaybackAt(START_TICK, 0.0f);
        state.seekTo(42.0f, START_TICK + 100, 300.0f);
        assertEquals(42.0f, state.getPlaybackPositionSeconds(START_TICK + 100), 0.0001f);
        assertEquals(43.0f, state.getPlaybackPositionSeconds(START_TICK + 120), 0.0001f);
    }

    @Test
    void seekClampsToKnownDuration() {
        SpeakerState state = new SpeakerState();
        state.seekTo(500.0f, START_TICK, 300.0f);
        assertEquals(300.0f, state.getPlaybackPositionSeconds(START_TICK), 0.0001f);
        state.seekTo(-5.0f, START_TICK, 300.0f);
        assertEquals(0.0f, state.getPlaybackPositionSeconds(START_TICK), 0.0001f);
    }

    @Test
    void seekWhilePausedKeepsFrozenTarget() {
        SpeakerState state = new SpeakerState();
        state.startPlaybackAt(START_TICK, 0.0f);
        state.pauseAt(START_TICK + 200);
        state.seekTo(77.0f, START_TICK + 400, 0.0f);
        assertTrue(state.isPaused());
        assertEquals(77.0f, state.getPlaybackPositionSeconds(START_TICK + 4000), 0.0001f);
    }

    @Test
    void stopClearsTransportOffsetsButRetainsGeneration() {
        SpeakerState state = new SpeakerState();
        state.startPlaybackAt(START_TICK, 25.0f);
        int generation = state.getPlaybackSessionGeneration();
        state.pauseAt(START_TICK + 100);
        state.stopPlayback();
        assertFalse(state.isPlaying());
        assertFalse(state.isPaused());
        assertEquals(-1, state.getPlaybackStartTick());
        assertEquals(0.0f, state.getPlaybackPositionSeconds(START_TICK + 999), 0.0001f);
        assertEquals(generation, state.getPlaybackSessionGeneration());
    }

    @Test
    void legacySetPlayingFalseAlsoResetsTransport() {
        SpeakerState state = new SpeakerState();
        state.startPlaybackAt(START_TICK, 5.0f);
        state.setPlaying(false);
        assertEquals(-1, state.getPlaybackStartTick());
        assertEquals(0.0f, state.getPauseOffsetSeconds(), 0.0001f);
    }

    @Test
    void copyDeepCopiesPlaylistSettingsAndPlaybackGeneration() {
        SpeakerState state = new SpeakerState("id", "file.mp3", false, false, START_TICK, 0.5f, 32, 0.4f);
        state.startPlaybackAt(START_TICK, 0.0f);
        state.startPlaybackAt(START_TICK + 1, 0.0f);
        state.setNetworkName("Mall BGM");
        state.setRedstoneMode(RedstoneMode.TOGGLE);
        state.setOwnerUuid(UUID.randomUUID());
        state.setAccessMode(SpeakerAccess.TRUSTED);
        state.trustPlayer(UUID.randomUUID());
        state.setDirectionality(0.75f);
        state.setConeAngleDegrees(80);
        state.setRearAttenuation(0.6f);
        state.getPlaylist().add("t1", "one.mp3");
        state.getPlaylist().selectIndex(0);

        SpeakerState copy = state.copy();

        assertEquals("Mall BGM", copy.getNetworkName());
        assertEquals(RedstoneMode.TOGGLE, copy.getRedstoneMode());
        assertEquals(SpeakerAccess.TRUSTED, copy.getAccessMode());
        assertEquals(0.75f, copy.getDirectionality(), 0.0001f);
        assertEquals(80, copy.getConeAngleDegrees());
        assertEquals(0.6f, copy.getRearAttenuation(), 0.0001f);
        assertEquals(state.getPlaybackSessionGeneration(), copy.getPlaybackSessionGeneration());
        assertEquals(1, copy.getPlaylist().size());
        assertEquals(0, copy.getPlaylist().getCurrentIndex());

        copy.getPlaylist().clear();
        copy.distrustPlayer(state.getTrustedPlayers().iterator().next());
        assertEquals(1, state.getPlaylist().size());
        assertEquals(1, state.getTrustedPlayers().size());
    }

    @Test
    void directionalSettingsSanitizeInputs() {
        SpeakerState state = new SpeakerState();
        state.setDirectionality(2.0f);
        assertEquals(1.0f, state.getDirectionality(), 0.0001f);
        state.setDirectionality(-1.0f);
        assertEquals(0.0f, state.getDirectionality(), 0.0001f);
        state.setConeAngleDegrees(1000);
        assertEquals(350, state.getConeAngleDegrees());
        state.setRearAttenuation(5.0f);
        assertEquals(1.0f, state.getRearAttenuation(), 0.0001f);
    }
}