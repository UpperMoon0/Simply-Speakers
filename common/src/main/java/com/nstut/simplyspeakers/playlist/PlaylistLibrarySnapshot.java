package com.nstut.simplyspeakers.playlist;

import com.nstut.simplyspeakers.SpeakerState;
import java.io.*;
import java.util.*;

/** Bounded immutable catalog; temporary queue/playback are synchronized separately. */
public record PlaylistLibrarySnapshot(String activeId,List<Entry> entries) {
    public static final int MAX_BYTES=900_000;
    public record Entry(String id,String name,List<String> audioIds,List<String> filenames) {
        public Entry { audioIds=List.copyOf(audioIds);filenames=List.copyOf(filenames); }
    }
    public PlaylistLibrarySnapshot { entries=List.copyOf(entries); }
    public Entry find(String id) { return entries.stream().filter(e -> e.id().equals(id)).findFirst().orElse(null); }
    public static PlaylistLibrarySnapshot single(List<String> ids,List<String> names) {
        return new PlaylistLibrarySnapshot("default",List.of(new Entry("default","Default",ids,names)));
    }
    public static PlaylistLibrarySnapshot capture(SpeakerState state) {
        return new PlaylistLibrarySnapshot(state.getActivePlaylistId(),state.getSavedPlaylists().stream().map(saved ->
            new Entry(saved.getId(),saved.getName(),saved.getPlaylist().getTracks().stream().map(PlaylistTrack::getAudioId).toList(),
                saved.getPlaylist().getTracks().stream().map(PlaylistTrack::getFilename).toList())).toList());
    }
    public byte[] encode() {
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            write(out,activeId,64);out.writeInt(entries.size());int total=0;
            if(entries.size()>SpeakerState.MAX_SAVED_PLAYLISTS) throw new IOException("Playlist count");
            for(var e:entries) {
                write(out,e.id(),64);write(out,e.name(),64);out.writeInt(e.audioIds().size());
                total+=e.audioIds().size();if(e.audioIds().size()>Playlist.MAX_ENTRIES || total>SpeakerState.MAX_SAVED_TRACKS) throw new IOException("Track count");
                for(int i=0;i<e.audioIds().size();i++) { write(out,e.audioIds().get(i),256);write(out,i<e.filenames().size()?e.filenames().get(i):"",256); }
            }
            if(bytes.size()>MAX_BYTES) throw new IOException("Catalog size");return bytes.toByteArray();
        } catch(IOException e) { throw new IllegalArgumentException("Invalid playlist catalog",e); }
    }
    public static PlaylistLibrarySnapshot decode(byte[] bytes) {
        if(bytes.length>MAX_BYTES) throw new IllegalArgumentException("Catalog size");
        try {
            var in=new DataInputStream(new ByteArrayInputStream(bytes));String active=read(in,64);int count=in.readInt();
            if(count<0 || count>SpeakerState.MAX_SAVED_PLAYLISTS) throw new IOException("Playlist count");
            var entries=new ArrayList<Entry>();var keys=new HashSet<String>();int total=0;
            for(int n=0;n<count;n++) {
                String id=read(in,64),name=read(in,64);if(id.isBlank() || !keys.add(id) || !SpeakerState.validPlaylistName(name))throw new IOException("Playlist identity");
                int tracks=in.readInt();total+=tracks;if(tracks<0 || tracks>Playlist.MAX_ENTRIES || total>SpeakerState.MAX_SAVED_TRACKS)throw new IOException("Track count");
                var ids=new ArrayList<String>();var names=new ArrayList<String>();
                for(int i=0;i<tracks;i++) { ids.add(read(in,256));names.add(read(in,256)); }
                entries.add(new Entry(id,name,ids,names));
            }
            if(in.available()!=0 || (!active.isEmpty() && !keys.contains(active))) throw new IOException("Catalog framing");
            return new PlaylistLibrarySnapshot(active,entries);
        }catch(IOException e){throw new IllegalArgumentException("Invalid playlist catalog",e);}
    }
    private static void write(DataOutputStream out,String s,int max)throws IOException { if(s==null || s.length()>max)throw new IOException("String length");out.writeUTF(s); }
    private static String read(DataInputStream in,int max)throws IOException {String s=in.readUTF();if(s.length()>max)throw new IOException("String length");return s;}
}
