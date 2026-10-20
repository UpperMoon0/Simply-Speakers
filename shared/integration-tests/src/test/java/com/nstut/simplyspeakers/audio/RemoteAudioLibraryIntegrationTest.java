package com.nstut.simplyspeakers.audio;

import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.network.SpeakerPacketSecurity;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemoteAudioLibraryIntegrationTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}

    @TempDir Path world;
    private final String owner=UUID.randomUUID().toString(),other=UUID.randomUUID().toString();
    private static final String URL="https://example.invalid/ambient.wav";
    boolean previous;
    @BeforeEach void enable() {previous=Config.allowRemoteStreams;Config.allowRemoteStreams=true;}
    @AfterEach void restore() {Config.allowRemoteStreams=previous;}

    @Test void importedLinksAreOwnedDeduplicatedAndPersistWithoutFetchingAudio() throws Exception {
        var manager=new AudioFileManager(world);
        var a=manager.addRemoteAudio(URL,"Rain",owner);
        assertEquals(a.getUuid(),manager.addRemoteAudio(URL,"Again",owner).getUuid());
        var b=manager.addRemoteAudio(URL,"Forest",other);
        assertNotEquals(a.getUuid(),b.getUuid());
        assertEquals(1,manager.getAudioListForPlayer(owner).size());
        assertEquals("Rain",manager.getAudioListForPlayer(owner).get(0).effectiveDisplayName());
        assertEquals("Forest",manager.getAudioListForPlayer(other).get(0).effectiveDisplayName());
        assertNull(manager.getAudioFilePath(a.getUuid()));
        Path reload=Files.createDirectories(world.resolve("reload/simply_speakers_audios"));
        Files.copy(world.resolve("simply_speakers_audios/audio_manifest.json"),reload.resolve("audio_manifest.json"));
        var loaded=new AudioFileManager(reload.getParent());
        assertEquals(URL,loaded.getManifest().get(a.getUuid()).getRemoteUrl());
        assertEquals("Rain",loaded.getManifest().get(a.getUuid()).effectiveDisplayName());
        try(var files=Files.list(reload)){assertEquals(1,files.count(),"Import must not create audio files");}
    }

    @Test void serverPolicyAndValidationGuardImports() throws Exception {
        var manager=new AudioFileManager(world);
        Config.allowRemoteStreams=false;
        assertThrows(IllegalArgumentException.class,()->manager.addRemoteAudio(URL,"",owner));
        Config.allowRemoteStreams=true;
        for(String url:List.of("http://127.0.0.1/a.wav","http://192.168.1.2/a.mp3",
                "https://youtube.com/watch?v=test","https://open.spotify.com/track/test",
                "file:///a.wav","https://example.invalid/a.wav?x="+"a".repeat(256)))
            assertThrows(IllegalArgumentException.class,()->manager.addRemoteAudio(url,"",owner),url);
        assertThrows(IllegalArgumentException.class,()->manager.addRemoteAudio(URL,"a".repeat(129),owner));
        assertThrows(IllegalArgumentException.class,()->manager.addRemoteAudio(URL,"a\nb",owner));
        assertTrue(manager.getManifest().isEmpty());
        assertEquals("ambient.wav",manager.addRemoteAudio(URL,"",owner).effectiveDisplayName());
    }

    @Test void removingBookmarkKeepsOtherOwnersAndNeverDeletesSourceOrPlayback() throws Exception {
        var manager=new AudioFileManager(world);
        var a=manager.addRemoteAudio(URL,"Mine",owner);var b=manager.addRemoteAudio(URL,"Theirs",other);
        assertFalse(manager.deleteAudioFile(a.getUuid(),other,null));
        assertTrue(manager.deleteAudioFile(a.getUuid(),owner,null),"Bookmarks need no playback purge or server instance");
        assertEquals(b.getUuid(),manager.getAudioListForPlayer(other).get(0).getUuid());
        assertTrue(manager.getAudioListForPlayer(owner).isEmpty());
    }

    @Test void playbackResolvesOwnedLibraryIdsAndRechecksStreamPolicy() throws Exception {
        var manager=new AudioFileManager(world);var a=manager.addRemoteAudio(URL,"Rain",owner);
        var player=mock(ServerPlayer.class);when(player.getUUID()).thenReturn(UUID.fromString(owner));
        try(var mod=mockStatic(SimplySpeakers.class)) {
            mod.when(SimplySpeakers::getAudioFileManager).thenReturn(manager);
            var track=SpeakerPacketSecurity.resolveAuthorizedTrack(player,a.getUuid());
            assertNotNull(track);assertEquals(URL,track.audioId());assertEquals("Rain",track.filename());
            Config.allowRemoteStreams=false;
            assertNull(SpeakerPacketSecurity.resolveAuthorizedTrack(player,a.getUuid()));
            Config.allowRemoteStreams=true;when(player.getUUID()).thenReturn(UUID.fromString(other));
            assertNull(SpeakerPacketSecurity.resolveAuthorizedTrack(player,a.getUuid()));
        }
    }

    @Test void urlLimitIsPerOwnerAndExistingEntriesRemainIdempotent() throws Exception {
        var entries=new java.util.HashMap<String,AudioFileMetadata>();
        for(int index=0;index<256;index++) {
            String id=UUID.randomUUID().toString();
            entries.put(id,new AudioFileMetadata(id,"Clip "+index,owner)
                    .withRemoteUrl("https://example.invalid/"+index+".wav"));
        }
        var dir=Files.createDirectories(world.resolve("simply_speakers_audios"));
        Files.writeString(dir.resolve("audio_manifest.json"),new com.google.gson.Gson().toJson(entries));
        var manager=new AudioFileManager(world);
        assertThrows(IllegalArgumentException.class,()->manager.addRemoteAudio(URL,"New",owner));
        assertNotNull(manager.addRemoteAudio("https://example.invalid/0.wav","Same",owner));
        assertNotNull(manager.addRemoteAudio(URL,"Other",other));
        assertEquals(256,manager.getAudioListForPlayer(owner).size());
    }

    @Test void failedManifestWriteDoesNotPublishAnEntry() {
        var manager=new AudioFileManager(world) {
            @Override void persistManifest(Map<String,AudioFileMetadata> snapshot) throws java.io.IOException {
                throw new java.io.IOException("disk unavailable");
            }
        };
        assertThrows(java.io.IOException.class,()->manager.addRemoteAudio(URL,"Rain",owner));
        assertTrue(manager.getAudioListForPlayer(owner).isEmpty());
    }

    @Test void mixedMetadataFramingPreservesRemoteSourceAndCopies() {
        var remote=new AudioFileMetadata("id","Rain",owner).withRemoteUrl(URL).withDisplayName("Evening").withDuration(2);
        var local=new AudioFileMetadata("file","clip.wav",owner,3);
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            remote.encode(buffer);local.encode(buffer);
            var a=AudioFileMetadata.decode(buffer);var b=AudioFileMetadata.decode(buffer);
            assertEquals(URL,a.getRemoteUrl());assertEquals("Evening",a.effectiveDisplayName());
            assertFalse(b.isRemote());assertEquals("file",b.getUuid());assertEquals(0,buffer.readableBytes());
        } finally {buffer.release();}
    }
}
