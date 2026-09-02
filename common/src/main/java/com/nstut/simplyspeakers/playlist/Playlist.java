package com.nstut.simplyspeakers.playlist;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Playlist playback engine: an ordered track list with repeat modes, seeded
 * shuffle, and a temporary play-next queue. Deterministic given the same seed.
 */
public class Playlist {
    /** Hard cap shared with the S2C playlist sync packet framing. */
    public static final int MAX_ENTRIES = 256;

    private List<PlaylistTrack> tracks = new ArrayList<>();
    private int currentIndex = -1;
    private boolean shuffle = false;
    private RepeatMode repeatMode = RepeatMode.DEFAULT;
    private long shuffleSeed = new Random().nextLong();

    private List<Integer> shuffleOrder = new ArrayList<>();
    private int shuffleOrderSize = -1;

    /** Tracks queued to play once before normal ordering resumes. */
    private List<String> queue = new ArrayList<>();

    /** Canonical walk position to restore once the one-shot queue drains. */
    private int resumeIndex = -1;

    public Playlist() {
    }

    public Playlist(List<PlaylistTrack> tracks) {
        if (tracks != null) appendAll(tracks);
    }

    private void appendAll(List<PlaylistTrack> newTracks) {
        int room = Math.max(0, MAX_ENTRIES - tracks.size());
        for (int i = 0; i < newTracks.size() && room > 0; i++, room--) {
            tracks.add(newTracks.get(i));
        }
    }

    public int size() {
        return tracks.size();
    }

    public boolean isEmpty() {
        return tracks.isEmpty();
    }

    public List<PlaylistTrack> getTracks() {
        return tracks;
    }

    /** Replaces the whole track list; keeps the current selection if still present. */
    public void setTracks(List<PlaylistTrack> newTracks) {
        String currentKey = currentKey();
        tracks = new ArrayList<>();
        if (newTracks != null) appendAll(newTracks);
        currentIndex = indexOfKey(currentKey);
        clampIndex();
    }

    public void add(String audioId, String filename) {
        if (tracks.size() >= MAX_ENTRIES) return;
        tracks.add(PlaylistTrack.of(audioId, filename));
    }

    /**
     * Removes every track matching the audio id. Returns true when something
     * was removed; adjusts the current index to stay consistent.
     */
    public boolean removeByAudioId(String audioId) {
        boolean removedAny = false;
        for (int i = tracks.size() - 1; i >= 0; i--) {
            if (tracks.get(i).sameAudio(audioId)) {
                tracks.remove(i);
                removedAny = true;
                if (i < currentIndex) currentIndex--;
                else if (i == currentIndex) currentIndex = -1;
            }
        }
        clampIndex();
        return removedAny;
    }

    public boolean moveUp(int index) {
        return swap(index, index - 1);
    }

    public boolean moveDown(int index) {
        return swap(index, index + 1);
    }

    private boolean swap(int a, int b) {
        if (a < 0 || b < 0 || a >= tracks.size() || b >= tracks.size()) return false;
        PlaylistTrack tmp = tracks.get(a);
        tracks.set(a, tracks.get(b));
        tracks.set(b, tmp);
        if (currentIndex == a) currentIndex = b;
        else if (currentIndex == b) currentIndex = a;
        return true;
    }

    public void clear() {
        tracks.clear();
        queue.clear();
        currentIndex = -1;
        resumeIndex = -1;
    }

    public PlaylistTrack current() {
        if (currentIndex < 0 || currentIndex >= tracks.size()) return null;
        return tracks.get(currentIndex);
    }

    public String currentKey() {
        PlaylistTrack t = current();
        return t != null ? t.key() : null;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int index) {
        currentIndex = Math.max(-1, Math.min(tracks.size() - 1, index));
    }

    /** Selects by list index. Returns the selected track or {@code null}. */
    public PlaylistTrack selectIndex(int index) {
        if (index < 0 || index >= tracks.size()) return null;
        currentIndex = index;
        return current();
    }

    /** Selects the first track matching the audio id. */
    public PlaylistTrack selectAudioId(String audioId) {
        if (audioId == null) return null;
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i).sameAudio(audioId)) {
                return selectIndex(i);
            }
        }
        return null;
    }

    private void clampIndex() {
        if (currentIndex >= tracks.size()) {
            currentIndex = tracks.isEmpty() ? -1 : tracks.size() - 1;
        }
    }

    private int indexOfKey(String key) {
        if (key == null) return -1;
        for (int i = 0; i < tracks.size(); i++) {
            if (key.equals(tracks.get(i).key())) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // Queue
    // ------------------------------------------------------------------

    /** Queues a track to be played next (one-shot). */
    public void queueNext(String audioId) {
        if (audioId != null && !audioId.isEmpty()) queue.add(audioId);
    }

    public void clearQueue() {
        queue.clear();
    }

    public boolean hasQueuedTracks() {
        return !queue.isEmpty();
    }

    public List<String> getQueue() {
        return queue;
    }

    /**
     * Canonical walk position to restore once the one-shot queue drains. Persisted so a
     * server restart while a queued track is active does not lose the resume location.
     */
    public int getResumeIndex() {
        return resumeIndex;
    }

    public void setResumeIndex(int index) {
        this.resumeIndex = index;
    }

    // ------------------------------------------------------------------
    // Modes
    // ------------------------------------------------------------------

    public boolean isShuffle() {
        return shuffle;
    }

    public void setShuffle(boolean shuffle) {
        if (this.shuffle != shuffle) {
            this.shuffle = shuffle;
            this.shuffleSeed = new Random().nextLong();
            this.shuffleOrderSize = -1;
        }
    }

    public void setShuffle(boolean shuffle, long seed) {
        this.shuffle = shuffle;
        this.shuffleSeed = seed;
        this.shuffleOrderSize = -1;
    }

    public long getShuffleSeed() {
        return shuffleSeed;
    }

    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(RepeatMode mode) {
        this.repeatMode = mode != null ? mode : RepeatMode.DEFAULT;
    }

    // ------------------------------------------------------------------
    // Advancement
    // ------------------------------------------------------------------

    public enum AdvanceResult { ADVANCED, WRAPPED, EXHAUSTED }

    public record Advance(AdvanceResult result, PlaylistTrack track) {
        public boolean hasTrack() {
            return track != null;
        }
    }

    /** Advances honouring the one-shot queue, shuffle order, and repeat mode. */
    public Advance next() {
        if (tracks.isEmpty()) return new Advance(AdvanceResult.EXHAUSTED, null);

        int canonical = resumeIndex >= 0 ? resumeIndex : currentIndex;
        while (!queue.isEmpty()) {
            String queuedId = queue.remove(0);
            PlaylistTrack track = selectAudioId(queuedId);
            if (track != null) {
                resumeIndex = canonical;
                return new Advance(AdvanceResult.ADVANCED, track);
            }
        }

        if (resumeIndex >= 0) {
            selectIndex(resumeIndex);
            resumeIndex = -1;
        }
        if (repeatMode == RepeatMode.TRACK && current() != null) {
            return new Advance(AdvanceResult.ADVANCED, current());
        }

        int n = tracks.size();
        int pos = walkPosition(currentIndex);
        int nextPos = pos + 1;
        if (currentIndex < 0) nextPos = 0;
        if (nextPos >= n) {
            if (repeatMode == RepeatMode.PLAYLIST) {
                return new Advance(AdvanceResult.WRAPPED, selectIndex(walkEntry(0)));
            }
            return new Advance(AdvanceResult.EXHAUSTED, null);
        }
        return new Advance(AdvanceResult.ADVANCED, selectIndex(walkEntry(nextPos)));
    }

    /** Moves backwards; wraps under PLAYLIST repeat, restarts at the start otherwise. */
    public Advance previous() {
        if (tracks.isEmpty()) return new Advance(AdvanceResult.EXHAUSTED, null);

        int n = tracks.size();
        int pos = walkPosition(currentIndex);
        int prevPos = pos - 1;
        if (prevPos < 0) {
            if (repeatMode == RepeatMode.PLAYLIST) {
                return new Advance(AdvanceResult.WRAPPED, selectIndex(walkEntry(n - 1)));
            }
            if (pos == 0) {
                return new Advance(AdvanceResult.ADVANCED, current());
            }
            prevPos = 0;
        }
        return new Advance(AdvanceResult.ADVANCED, selectIndex(walkEntry(prevPos)));
    }

    // ------------------------------------------------------------------
    // Shuffle order (seeded Fisher-Yates permutation)
    // ------------------------------------------------------------------

    private void ensureShuffleOrder() {
        int n = tracks.size();
        if (shuffleOrderSize == n && shuffleOrder.size() == n) return;
        shuffleOrderSize = n;
        shuffleOrder = new ArrayList<>(n);
        for (int i = 0; i < n; i++) shuffleOrder.add(i);
        if (n <= 1) return;
        Random random = new Random(shuffleSeed);
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = shuffleOrder.get(i);
            shuffleOrder.set(i, shuffleOrder.get(j));
            shuffleOrder.set(j, tmp);
        }
    }

    private int walkPosition(int index) {
        if (index < 0) return -1;
        if (!shuffle) return index;
        ensureShuffleOrder();
        int pos = shuffleOrder.indexOf(index);
        return pos >= 0 ? pos : index;
    }

    private int walkEntry(int pos) {
        int n = tracks.size();
        if (n == 0) return -1;
        int clamped = Math.max(0, Math.min(n - 1, pos));
        if (!shuffle) return clamped;
        ensureShuffleOrder();
        return shuffleOrder.get(clamped);
    }

    @Override
    public String toString() {
        return "Playlist{tracks=" + tracks.size() + ", index=" + currentIndex
                + ", shuffle=" + shuffle + ", repeat=" + repeatMode + ", queued=" + queue.size() + "}";
    }
}
