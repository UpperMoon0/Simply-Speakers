package com.nstut.simplyspeakers.playlist;

/** A persistent saved list belonging to one player (legacy worlds stored these on networks). */
public final class NamedPlaylist {
    private String id;
    private String name;
    private Playlist playlist;
    public NamedPlaylist(String id,String name,Playlist playlist) { this.id=id;this.name=name;this.playlist=playlist; }
    public String getId() { return id; }
    public String getName() { return name; }
    public Playlist getPlaylist() { if(playlist==null) playlist=new Playlist();return playlist; }
    public void setName(String name) { this.name=name; }
    public void setPlaylist(Playlist value) { playlist=value; }
}
