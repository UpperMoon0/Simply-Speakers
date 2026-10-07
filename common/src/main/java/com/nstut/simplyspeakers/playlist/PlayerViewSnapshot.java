package com.nstut.simplyspeakers.playlist;

import com.nstut.simplyspeakers.SpeakerState;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/** Shared, bounded wire representation for the player and its temporary queue. */
public record PlayerViewSnapshot(List<String> queue, List<Integer> upcoming, String audioId,
                                 String filename, boolean playing, boolean queuedTrack,
                                 float positionSeconds, float durationSeconds) {
    public static final int MAX_BYTES = 400_000;
    public static final PlayerViewSnapshot EMPTY = new PlayerViewSnapshot(List.of(), List.of(), "", "", false, false, 0, 0);

    public PlayerViewSnapshot {
        queue = List.copyOf(queue);
        upcoming = List.copyOf(upcoming);
        if (queue.size() > Playlist.MAX_ENTRIES || upcoming.size() > Playlist.MAX_ENTRIES)
            throw new IllegalArgumentException("Player snapshot exceeds track limit");
        positionSeconds = Float.isFinite(positionSeconds) ? Math.max(0, positionSeconds) : 0;
        durationSeconds = Float.isFinite(durationSeconds) ? Math.max(0, durationSeconds) : 0;
    }

    public static PlayerViewSnapshot capture(SpeakerState state, long tick, float duration) {
        Playlist playlist = state.getPlaylist();
        return new PlayerViewSnapshot(playlist.getQueue(), state.isPlaylistSourceActive() ? playlist.upcomingIndices() : List.of(), state.getAudioId(),
                state.getAudioFilename(), state.isPlaying(), playlist.isQueuedTrackActive(),
                state.getPlaybackPositionSeconds(tick), duration);
    }

    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(queue.size());
            for (String id : queue) writeString(out, id);
            out.writeInt(upcoming.size());
            for (int index : upcoming) out.writeInt(index);
            writeString(out, audioId);
            writeString(out, filename);
            out.writeBoolean(playing);
            out.writeBoolean(queuedTrack);
            out.writeFloat(positionSeconds);
            out.writeFloat(durationSeconds);
            return bytes.toByteArray();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    public static PlayerViewSnapshot decode(byte[] bytes) {
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Player snapshot too large");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            List<String> queue = new ArrayList<>();
            for (int n = count(in); n > 0; n--) queue.add(readString(in));
            List<Integer> upcoming = new ArrayList<>();
            for (int n = count(in); n > 0; n--) {
                int index = in.readInt();
                if (index < 0 || index >= Playlist.MAX_ENTRIES) throw new IOException("Invalid track index");
                upcoming.add(index);
            }
            PlayerViewSnapshot snapshot = new PlayerViewSnapshot(queue, upcoming, readString(in), readString(in),
                    in.readBoolean(), in.readBoolean(), in.readFloat(), in.readFloat());
            if (in.available() != 0) throw new IOException("Trailing snapshot data");
            return snapshot;
        } catch (IOException e) { throw new IllegalArgumentException("Invalid player snapshot", e); }
    }

    private static int count(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > Playlist.MAX_ENTRIES) throw new IOException("Invalid track count");
        return count;
    }
    private static void writeString(DataOutputStream out, String value) throws IOException {
        String text = value == null ? "" : value;
        if (text.length() > 256) throw new IOException("Track identifier too long");
        out.writeUTF(text);
    }
    private static String readString(DataInputStream in) throws IOException {
        String text = in.readUTF();
        if (text.length() > 256) throw new IOException("Track identifier too long");
        return text;
    }
}
