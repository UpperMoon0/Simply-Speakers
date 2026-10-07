package com.nstut.simplyspeakers.playlist;
import com.nstut.simplyspeakers.SpeakerState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class NamedPlaylistTest {
    @Test void legacyMigrationPreservesTheSameListCursorModesAndQueuedPlayback() throws Exception {
        var original=new Playlist();original.add("a","A.wav");original.add("b","B.wav");original.selectIndex(1);
        original.setShuffle(true,42);original.setRepeatMode(RepeatMode.TRACK);original.queueLast("request");original.setResumeIndex(1);
        var state=new SpeakerState();var field=SpeakerState.class.getDeclaredField("playlist");field.setAccessible(true);field.set(state,original);
        assertSame(original,state.getPlaylist());assertEquals("Default",state.getSavedPlaylists().get(0).getName());
        assertEquals(1,state.getPlaylist().getCurrentIndex());assertEquals(List.of("request"),state.getPlaylist().getQueue());assertEquals(42,state.getPlaylist().getShuffleSeed());
        assertEquals(RepeatMode.TRACK,state.getPlaylist().getRepeatMode());
    }
    @Test void creationAndInactiveEditingLeavePlayingSourceAndQueueUntouched() {
        var state=new SpeakerState();state.getPlaylist().add("playing","Playing");state.getPlaylist().queueNext("request");state.startPlaybackAt(10,12);
        String next=state.createSavedPlaylist("Evening");state.findSavedPlaylist(next).getPlaylist().add("other","Other");
        assertEquals("default",state.getActivePlaylistId());assertEquals(List.of("request"),state.getPlaylist().getQueue());assertTrue(state.isPlaying());assertEquals(12,state.getPlaybackPositionSeconds(10));
    }
    @Test void duplicateCopiesTracksAndModesWithoutTransientPlayback() {
        var state=new SpeakerState();var original=state.getPlaylist();original.add("a","A");original.add("a","A repeated");original.setShuffle(true,92);original.setRepeatMode(RepeatMode.PLAYLIST);original.selectIndex(1);original.queueNext("x");
        String duplicate=state.duplicateSavedPlaylist("default","Copy");var copy=state.findSavedPlaylist(duplicate).getPlaylist();
        assertEquals(2,copy.size());assertTrue(copy.isShuffle());assertEquals(92,copy.getShuffleSeed());assertEquals(RepeatMode.PLAYLIST,copy.getRepeatMode());assertEquals(-1,copy.getCurrentIndex());assertTrue(copy.getQueue().isEmpty());
        copy.removeAt(0);assertEquals(2,original.size());
    }
    @Test void copiedSpeakerNetworksDoNotShareInactiveListsOrNames() {
        var state=new SpeakerState();String id=state.createSavedPlaylist("Travel");state.findSavedPlaylist(id).getPlaylist().add("a","A");
        var copy=state.copy();copy.renameSavedPlaylist(id,"Other");copy.findSavedPlaylist(id).getPlaylist().clear();
        assertEquals("Travel",state.findSavedPlaylist(id).getName());assertEquals(1,state.findSavedPlaylist(id).getPlaylist().size());
    }
    @Test void deletePreservesInactivePlaybackAndAllowsEmptyLibrary() {
        var state=new SpeakerState();state.startPlaybackAt(4,0);String id=state.createSavedPlaylist("Other");assertTrue(state.deleteSavedPlaylist(id));assertTrue(state.isPlaying());assertTrue(state.deleteSavedPlaylist("default"));assertTrue(state.getSavedPlaylists().isEmpty());assertTrue(state.copy().getSavedPlaylists().isEmpty());
        state=new SpeakerState();
        id=state.createSavedPlaylist("Other");state.activateSavedPlaylist(id);assertTrue(state.deleteSavedPlaylist(id));assertFalse(state.isPlaying());assertEquals("default",state.getActivePlaylistId());assertEquals("",state.getAudioId());
    }
    @Test void uniqueNamesAndCollectionBoundsAreEnforced() {
        var state=new SpeakerState();assertNull(state.createSavedPlaylist(" DEFAULT "));assertNull(state.createSavedPlaylist(" "));assertNull(state.createSavedPlaylist("x".repeat(65)));assertNull(state.createSavedPlaylist("a\nb"));
        String id=state.createSavedPlaylist("Valid");assertFalse(state.renameSavedPlaylist(id,"default"));assertTrue(state.renameSavedPlaylist(id," Updated "));
        for(int n=2;n<SpeakerState.MAX_SAVED_PLAYLISTS;n++)assertNotNull(state.createSavedPlaylist("List "+n));assertNull(state.createSavedPlaylist("Overflow"));
    }
    @Test void boundedCatalogRoundTripCarriesEveryListAndRejectsMalformedFrames() {
        var state=new SpeakerState();state.getPlaylist().add("a","A");String id=state.createSavedPlaylist("Evening");state.findSavedPlaylist(id).getPlaylist().add("b","B");state.activateSavedPlaylist(id);
        var snapshot=PlaylistLibrarySnapshot.capture(state);assertEquals(snapshot,PlaylistLibrarySnapshot.decode(snapshot.encode()));
        assertThrows(IllegalArgumentException.class,() -> PlaylistLibrarySnapshot.decode(new byte[]{0,0,0}));
        byte[] extra=Arrays.copyOf(snapshot.encode(),snapshot.encode().length+1);assertThrows(IllegalArgumentException.class,() -> PlaylistLibrarySnapshot.decode(extra));
        var duplicate=new PlaylistLibrarySnapshot("default",List.of(snapshot.entries().get(0),snapshot.entries().get(0)));assertThrows(IllegalArgumentException.class,() -> PlaylistLibrarySnapshot.decode(duplicate.encode()));
    }

    @Test void worstCaseLegalCatalogAndPlayerSnapshotFitOneCustomPayload() {
        var state=new SpeakerState();String text="\u4e00".repeat(256);
        for(int n=0;n<256;n++)state.getPlaylist().add(text,text);
        String id=state.createSavedPlaylist("Second");for(int n=0;n<256;n++)state.findSavedPlaylist(id).getPlaylist().add(text,text);
        for(int n=0;n<256;n++)state.getPlaylist().queueLast(text);
        state.setAudioId(text);state.setAudioFilename(text);
        int bytes=PlaylistLibrarySnapshot.capture(state).encode().length+PlayerViewSnapshot.capture(state,0,0).encode().length;
        assertTrue(bytes+2_000<1_048_576,"Legal catalog plus full queue must fit the Minecraft custom payload limit");
    }
}
