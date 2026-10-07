package com.nstut.simplyspeakers.control;

import com.nstut.simplyspeakers.RedstoneLogic;
import com.nstut.simplyspeakers.RedstoneMode;
import com.nstut.simplyspeakers.SpeakerState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControllerActionTest {
    @Test void signalSelectsOneBasedPlaylistSlotsWithoutExceedingItsSize() {
        assertEquals(-1,ControllerAction.trackIndex(0,3));assertEquals(-1,ControllerAction.trackIndex(1,0));
        assertEquals(0,ControllerAction.trackIndex(1,3));assertEquals(2,ControllerAction.trackIndex(15,3));assertEquals(14,ControllerAction.trackIndex(40,30));
    }
    @Test void pulseActionsDoNotRepeatWhileHeldOrOnRelease() {
        for (var action : ControllerAction.values()) if (!action.continuous()) {
            assertTrue(action.shouldApply(0, 1), action.id());
            assertFalse(action.shouldApply(1, 15), action.id());
            assertFalse(action.shouldApply(15, 15), action.id());
            assertFalse(action.shouldApply(15, 0), action.id());
        }
    }
    @Test void continuousActionsObserveAnalogChangesAndRelease() {
        assertTrue(ControllerAction.VOLUME.shouldApply(3, 8));
        assertTrue(ControllerAction.ENABLED.shouldApply(15, 0));
        assertFalse(ControllerAction.VOLUME.shouldApply(8, 8));
        assertEquals(0.4f, ControllerAction.volume(12, 0.5f), 0.0001);
        assertEquals(0, ControllerAction.volume(-1, 0.5f));
        assertEquals(1, ControllerAction.volume(30, Float.NaN));
    }
    @Test void proxyControlsCannotAffectSharedTransport() {
        for (var action : ControllerAction.values()) assertEquals(action.continuous(),action.supportsProxy());
    }
    @Test void ignoreModeNeverEmitsAnActionAndKeepsLegacyOrdinals() {
        assertEquals(0, RedstoneMode.POWER.ordinal());
        assertEquals(5, RedstoneMode.ANALOG_TRACK.ordinal());
        assertEquals(RedstoneMode.IGNORE, RedstoneMode.byId("ignore"));
        assertFalse(RedstoneLogic.evaluate(RedstoneMode.IGNORE,0,15,3).hasAction());
        assertFalse(RedstoneLogic.evaluate(RedstoneMode.IGNORE,15,0,3).hasAction());
    }
    @Test void announcementOverridesOnlyPlaybackLoopingAndNormalStartRestoresIt() {
        var state = new SpeakerState();
        state.setLooping(true);
        state.getPlaylist().add("music", "music.wav");
        state.startPlaybackAt(10,0);
        state.setOneShotPlayback(true);
        assertTrue(state.isLooping());
        assertFalse(state.isPlaybackLooping());
        assertTrue(state.copy().isOneShotPlayback());
        state.pauseAt(20); state.resumeAt(30);
        assertTrue(state.isOneShotPlayback());
        state.startPlaybackAt(40,0);
        assertTrue(state.isLooping());
        assertFalse(state.isPlaybackLooping(), "Playlist repeat advances on server so requests are not starved");
        assertFalse(state.isOneShotPlayback());
        assertEquals(1, state.getPlaylist().size());
    }
}
