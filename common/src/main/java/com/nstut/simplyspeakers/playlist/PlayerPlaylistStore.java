package com.nstut.simplyspeakers.playlist;

import com.google.gson.Gson;
import com.nstut.simplyspeakers.SpeakerState;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** World-scoped personal libraries. Playback cursors and temporary queues belong to speakers. */
public final class PlayerPlaylistStore {
    private static final Gson JSON=new Gson();
    private static Map<UUID,SpeakerState> libraries=new HashMap<>();
    private static Map<String,String> imports=new HashMap<>();
    private static Path file;
    private static boolean dirty;
    private record Stored(Map<UUID,SpeakerState> libraries,Map<String,String> imports) {}
    private PlayerPlaylistStore() {}
    public static synchronized void reset() { libraries=new HashMap<>();imports=new HashMap<>();file=null;dirty=false; }
    public static synchronized void init(Path world) {
        reset();file=world.resolve("player_playlists.json");
        if(Files.exists(file)) try {
            Stored loaded=JSON.fromJson(Files.readString(file),Stored.class);
            if(loaded==null || loaded.libraries()==null || loaded.imports()==null)throw new IOException("Invalid personal playlist data");
            libraries=new HashMap<>(loaded.libraries());imports=new HashMap<>(loaded.imports());
        } catch(IOException | RuntimeException e) { throw new IllegalStateException("Cannot load personal playlists; refusing to overwrite saved data",e); }
    }
    public static synchronized SpeakerState library(UUID player) { return libraries.computeIfAbsent(Objects.requireNonNull(player),id -> SpeakerState.emptyPlaylistLibrary()); }
    public static synchronized void changed() { dirty=true; }
    public static synchronized boolean save() {
        if(!dirty || file==null)return true;
        Path tmp=file.resolveSibling(file.getFileName()+".tmp");
        try {
            Files.createDirectories(file.getParent());Files.writeString(tmp,JSON.toJson(new Stored(libraries,imports)));
            try { Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); }
            dirty=false;return true;
        } catch(IOException e) { return false; }
    }
    public static synchronized PlaylistLibrarySnapshot snapshot(UUID player,SpeakerState speaker) {
        SpeakerState catalog=library(player);
        String active=player.equals(speaker.getPlaylistOwnerUuid()) && catalog.findSavedPlaylist(speaker.getActivePlaylistId())!=null?speaker.getActivePlaylistId():"";
        return new PlaylistLibrarySnapshot(active,PlaylistLibrarySnapshot.capture(catalog).entries());
    }
    /** Import each legacy network list once into its owner's library without losing its speaker queue. */
    public static synchronized void migrate(String key,SpeakerState speaker) {
        UUID owner=speaker.getOwnerUuid();if(owner==null || speaker.isPersonalPlaylistSource())return;
        SpeakerState catalog=library(owner);String active=speaker.getActivePlaylistId(),replacement=null;boolean complete=true;
        var legacy=speaker.getSavedPlaylists();
        if(legacy.size()==1 && legacy.get(0).getId().equals("default") && legacy.get(0).getPlaylist().isEmpty()) {
            speaker.usePlayerPlaylist(owner,"",speaker.getPlaylist());return;
        }
        for(var old:legacy) {
            String marker=owner+"/"+key+"/"+old.getId();String id=imports.get(marker);
            if(id==null) {
                if(catalog.savedTrackCount()+old.getPlaylist().size()>SpeakerState.MAX_SAVED_TRACKS){complete=false;continue;}
                String name=old.getName();int suffix=2;
                while(hasName(catalog,name)) { String tail=" ("+suffix+++ ")";name=old.getName().substring(0,Math.min(old.getName().length(),64-tail.length()))+tail; }
                id=catalog.createSavedPlaylist(name);if(id==null){complete=false;continue;}
                Playlist copy=new Playlist();copy.setTracks(SpeakerState.copyTracks(old.getPlaylist()));
                copy.setRepeatMode(old.getPlaylist().getRepeatMode());copy.setShuffle(old.getPlaylist().isShuffle(),old.getPlaylist().getShuffleSeed());
                catalog.findSavedPlaylist(id).setPlaylist(copy);imports.put(marker,id);dirty=true;
            }
            if(old.getId().equals(active))replacement=id;
        }
        if(complete && replacement!=null) speaker.usePlayerPlaylist(owner,replacement,speaker.getPlaylist());
    }
    private static boolean hasName(SpeakerState catalog,String name) { return catalog.getSavedPlaylists().stream().anyMatch(p -> p.getName().equalsIgnoreCase(name)); }
}
