package com.nstut.simplyspeakers.playlist;
import com.nstut.simplyspeakers.SpeakerState;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PlayerPlaylistStoreTest {
    @AfterEach void cleanup(){PlayerPlaylistStore.reset();}
    @Test void librariesArePrivateAndTheLastDeletionSurvivesReload(@TempDir Path world) {
        var a=UUID.randomUUID();var b=UUID.randomUUID();PlayerPlaylistStore.init(world);
        var own=PlayerPlaylistStore.library(a);String id=own.createSavedPlaylist("Clips");own.findSavedPlaylist(id).getPlaylist().add("a","A.wav");
        assertTrue(PlayerPlaylistStore.library(b).getSavedPlaylists().isEmpty());
        PlayerPlaylistStore.changed();assertTrue(PlayerPlaylistStore.save());PlayerPlaylistStore.init(world);
        assertEquals("Clips",PlayerPlaylistStore.library(a).getSavedPlaylists().get(0).getName());
        assertTrue(PlayerPlaylistStore.library(a).deleteSavedPlaylist(id));PlayerPlaylistStore.changed();assertTrue(PlayerPlaylistStore.save());
        PlayerPlaylistStore.init(world);assertTrue(PlayerPlaylistStore.library(a).getSavedPlaylists().isEmpty());
        var empty=PlayerPlaylistStore.snapshot(a,new SpeakerState());assertEquals(empty,PlaylistLibrarySnapshot.decode(empty.encode()));
    }
    @Test void migrationIsOwnerBoundOnceAndNeverCopiesQueue(@TempDir Path world) {
        var owner=UUID.randomUUID();var other=UUID.randomUUID();PlayerPlaylistStore.init(world);
        var speaker=new SpeakerState();speaker.setOwnerUuid(owner);speaker.getPlaylist().add("a","A");speaker.getPlaylist().queueLast("request");
        PlayerPlaylistStore.migrate("dimension/network",speaker);String id=speaker.getActivePlaylistId();
        assertTrue(speaker.isPersonalPlaylistSource());assertEquals(List.of("request"),speaker.getPlaylist().getQueue());
        assertTrue(PlayerPlaylistStore.library(owner).findSavedPlaylist(id).getPlaylist().getQueue().isEmpty());
        assertTrue(PlayerPlaylistStore.snapshot(other,speaker).entries().isEmpty());
        PlayerPlaylistStore.migrate("dimension/network",speaker);assertEquals(1,PlayerPlaylistStore.library(owner).getSavedPlaylists().size());
        assertTrue(PlayerPlaylistStore.save());PlayerPlaylistStore.init(world);PlayerPlaylistStore.migrate("dimension/network",speaker);assertEquals(1,PlayerPlaylistStore.library(owner).getSavedPlaylists().size());
    }
    @Test void freshSpeakersDoNotCreatePhantomDefaultPlaylists() {
        var owner=UUID.randomUUID();var state=new SpeakerState();state.setOwnerUuid(owner);PlayerPlaylistStore.migrate("new",state);
        assertTrue(PlayerPlaylistStore.library(owner).getSavedPlaylists().isEmpty());assertTrue(state.getPlaylist().isEmpty());
    }
    @Test void twoSpeakersUseCopiesAndKeepSeparateQueuesAndCursors() {
        var owner=UUID.randomUUID();var catalog=PlayerPlaylistStore.library(owner);String id=catalog.createSavedPlaylist("Sounds");
        var template=catalog.findSavedPlaylist(id).getPlaylist();template.add("a","A");template.add("b","B");
        var first=new SpeakerState();var second=new SpeakerState();first.getPlaylist().queueLast("first");second.getPlaylist().queueLast("second");
        first.usePlayerPlaylist(owner,id,template);second.usePlayerPlaylist(owner,id,template);first.getPlaylist().selectIndex(1);first.getPlaylist().removeAt(0);
        first.getPlaylist().getTracks().get(0).setFilename("Changed");assertEquals("B",template.getTracks().get(1).getFilename());assertEquals("A",second.getPlaylist().getTracks().get(0).getFilename());
        assertEquals(2,template.size());assertEquals(2,second.getPlaylist().size());assertEquals(List.of("first"),first.getPlaylist().getQueue());assertEquals(List.of("second"),second.getPlaylist().getQueue());
        var persisted=first.copy();assertEquals(owner,persisted.getPlaylistOwnerUuid());assertEquals(id,persisted.getActivePlaylistId());assertEquals(List.of("first"),persisted.getPlaylist().getQueue());
        first.detachPlayerPlaylist();assertTrue(first.getPlaylist().isEmpty());assertEquals(List.of("first"),first.getPlaylist().getQueue());
    }
    @Test void malformedSaveFailsClosedInsteadOfReplacingTheLibrary(@TempDir Path world)throws Exception {
        Files.writeString(world.resolve("player_playlists.json"),"{broken");assertThrows(IllegalStateException.class,()->PlayerPlaylistStore.init(world));
        assertEquals("{broken",Files.readString(world.resolve("player_playlists.json")));
    }
}
