package com.nstut.simplyspeakers.playlist;

/** A single entry inside a {@link Playlist}. */
public class PlaylistTrack {
    private String audioId;
    private String filename;

    public PlaylistTrack() {
        this("", "");
    }

    public PlaylistTrack(String audioId, String filename) {
        this.audioId = audioId != null ? audioId : "";
        this.filename = filename != null ? filename : "";
    }

    public static PlaylistTrack of(String audioId, String filename) {
        return new PlaylistTrack(audioId, filename);
    }

    public String getAudioId() { return audioId; }
    public void setAudioId(String audioId) { this.audioId = audioId != null ? audioId : ""; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String key() {
        return audioId + "|" + filename;
    }

    public boolean sameAudio(String otherAudioId) {
        return audioId.equals(otherAudioId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof PlaylistTrack that && audioId.equals(that.audioId);
    }

    @Override
    public int hashCode() {
        return audioId.hashCode();
    }

    @Override
    public String toString() {
        return "PlaylistTrack{" + (filename.isEmpty() ? audioId : filename) + "}";
    }
}
