package com.nstut.simplyspeakers;

import com.nstut.simplyspeakers.math.AudioMath;
import com.nstut.simplyspeakers.playlist.Playlist;
import com.nstut.simplyspeakers.playlist.RepeatMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents the state of a speaker network. Holds all information needed to
 * manage playback, playlists, redstone automation, ownership, and directionality.
 */
public class SpeakerState {
    private String audioId = "";
    private String audioFilename = "";
    private boolean isPlaying = false;
    private boolean isLooping = false;
    private long playbackStartTick = -1;
    private float maxVolume = 1.0f;
    private int maxRange = 16;
    private float audioDropoff = 1.0f;

    // --- Transport (0.8.x) ---
    /** True when playback is suspended mid-track rather than stopped. */
    private boolean paused = false;
    /** Playback position preserved across pause/resume, used as play offset. */
    private float pauseOffsetSeconds = 0.0f;

    // --- Playlist / queue (0.8.x) ---
    private Playlist playlist;

    // --- Network identity (0.8.x) ---
    private String networkName = "";

    // --- Redstone automation (0.8.x) ---
    private RedstoneMode redstoneMode;

    // --- Ownership / permissions (0.8.x) ---
    private UUID ownerUuid;
    private SpeakerAccess accessMode;
    private Set<UUID> trustedPlayers;

    // --- Directional audio (0.8.x) ---
    /** 0 = omnidirectional, 1 = fully directional cone. */
    private float directionality = 0.0f;
    /** Full horizontal cone angle in degrees inside which audio is unattenuated. */
    private int coneAngleDegrees = 90;
    /** How much energy is lost behind the speaker (0-1) when directional. */
    private float rearAttenuation = 0.9f;

    public SpeakerState() {
        this.redstoneMode = RedstoneMode.DEFAULT;
        this.accessMode = SpeakerAccess.DEFAULT;
    }

    public SpeakerState(String audioId, String audioFilename, boolean isPlaying, boolean isLooping, long playbackStartTick) {
        this.audioId = audioId != null ? audioId : "";
        this.audioFilename = audioFilename != null ? audioFilename : "";
        this.isPlaying = isPlaying;
        this.isLooping = isLooping;
        this.playbackStartTick = playbackStartTick;
        this.maxVolume = 1.0f;
        this.maxRange = 16;
        this.audioDropoff = 1.0f;
        this.redstoneMode = RedstoneMode.DEFAULT;
        this.accessMode = SpeakerAccess.DEFAULT;
    }

    public SpeakerState(String audioId, String audioFilename, boolean isPlaying, boolean isLooping,
                        long playbackStartTick, float maxVolume, int maxRange, float audioDropoff) {
        this(audioId, audioFilename, isPlaying, isLooping, playbackStartTick);
        this.maxVolume = AudioMath.sanitizeFloat(maxVolume, 0.0f, 1.0f, 1.0f);
        this.maxRange = Math.max(1, Math.min(Config.speakerRange, maxRange));
        this.audioDropoff = AudioMath.sanitizeFloat(audioDropoff, 0.0f, 1.0f, 1.0f);
    }

    // Getters and setters
    public String getAudioId() {
        return audioId;
    }

    public void setAudioId(String audioId) {
        this.audioId = audioId != null ? audioId : "";
    }

    public String getAudioFilename() {
        return audioFilename;
    }

    public void setAudioFilename(String audioFilename) {
        this.audioFilename = audioFilename != null ? audioFilename : "";
    }

    public boolean hasAudio() {
        return audioId != null && !audioId.isEmpty();
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void setPlaying(boolean playing) {
        isPlaying = playing;
        if (!playing) {
            paused = false;
            pauseOffsetSeconds = 0.0f;
            playbackStartTick = -1;
        }
    }

    public boolean isLooping() {
        return isLooping;
    }

    public void setLooping(boolean looping) {
        isLooping = looping;
    }

    public long getPlaybackStartTick() {
        return playbackStartTick;
    }

    public void setPlaybackStartTick(long playbackStartTick) {
        this.playbackStartTick = playbackStartTick;
    }

    /**
     * Calculates the current playback position in seconds. When paused, reports
     * the frozen position; when stopped, any pending seek target.
     */
    public float getPlaybackPositionSeconds(long currentTick) {
        if (isPlaying && !paused) {
            long ticksElapsed = currentTick - playbackStartTick;
            float elapsed = Math.max(0.0f, ticksElapsed / 20.0f);
            return pauseOffsetSeconds + elapsed;
        }
        return Math.max(0.0f, pauseOffsetSeconds);
    }

    public SpeakerState copy() {
        SpeakerState copy = new SpeakerState(audioId, audioFilename, isPlaying, isLooping,
                playbackStartTick, maxVolume, maxRange, audioDropoff);
        copy.paused = paused;
        copy.pauseOffsetSeconds = pauseOffsetSeconds;
        copy.playlist = playlist != null ? deepCopy(playlist) : null;
        copy.networkName = networkName;
        copy.redstoneMode = redstoneMode != null ? redstoneMode : RedstoneMode.DEFAULT;
        copy.ownerUuid = ownerUuid;
        copy.accessMode = accessMode != null ? accessMode : SpeakerAccess.DEFAULT;
        copy.trustedPlayers = trustedPlayers != null ? new HashSet<>(trustedPlayers) : null;
        copy.directionality = directionality;
        copy.coneAngleDegrees = coneAngleDegrees;
        copy.rearAttenuation = rearAttenuation;
        return copy;
    }

    private static Playlist deepCopy(Playlist source) {
        Playlist copy = new Playlist();
        copy.setTracks(new ArrayList<>(source.getTracks()));
        copy.setCurrentIndex(source.getCurrentIndex());
        copy.setRepeatMode(source.getRepeatMode());
        copy.setShuffle(source.isShuffle(), source.getShuffleSeed());
        for (String queued : source.getQueue()) {
            copy.queueNext(queued);
        }
        return copy;
    }

    @Override
    public String toString() {
        return "SpeakerState{audioId='" + audioId + "', isPlaying=" + isPlaying
                + ", paused=" + paused + ", looping=" + isLooping
                + ", startTick=" + playbackStartTick + "}";
    }

    // ------------------------------------------------------------------
    // Transport controls (0.8.x)
    // ------------------------------------------------------------------

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public float getPauseOffsetSeconds() {
        return Math.max(0.0f, pauseOffsetSeconds);
    }

    public void setPauseOffsetSeconds(float offsetSeconds) {
        this.pauseOffsetSeconds = Math.max(0.0f, offsetSeconds);
    }

    /** Starts (or restarts) playback at {@code offsetSeconds} from the given tick. */
    public void startPlaybackAt(long currentTick, float offsetSeconds) {
        this.isPlaying = true;
        this.paused = false;
        this.pauseOffsetSeconds = Math.max(0.0f, offsetSeconds);
        this.playbackStartTick = currentTick;
    }

    /** Suspends playback, preserving the current position for later resume. */
    public void pauseAt(long currentTick) {
        if (!isPlaying || paused) return;
        this.pauseOffsetSeconds = getPlaybackPositionSeconds(currentTick);
        this.paused = true;
    }

    /** Resumes suspended playback from the preserved position. */
    public void resumeAt(long currentTick) {
        if (!paused) return;
        this.playbackStartTick = currentTick;
        this.paused = false;
    }

    /** Stops playback entirely, clearing position and pending seeks. */
    public void stopPlayback() {
        setPlaying(false);
    }

    /** Seeks to an absolute position, clamped to a known duration. */
    public void seekTo(float seconds, long currentTick, float durationSeconds) {
        float target = Math.max(0.0f, seconds);
        if (durationSeconds > 0.0f && Float.isFinite(durationSeconds)) {
            target = Math.min(target, Math.max(0.0f, durationSeconds));
        }
        this.pauseOffsetSeconds = target;
        if (isPlaying && !paused) {
            this.playbackStartTick = currentTick;
        }
    }

    // ------------------------------------------------------------------
    // Playlist (0.8.x)
    // ------------------------------------------------------------------

    public Playlist getPlaylist() {
        if (playlist == null) playlist = new Playlist();
        return playlist;
    }

    public boolean hasPlaylist() {
        return playlist != null && !playlist.isEmpty();
    }

    public void setPlaylist(Playlist playlist) {
        this.playlist = playlist;
    }

    // ------------------------------------------------------------------
    // Network naming (0.8.x)
    // ------------------------------------------------------------------

    public String getNetworkName() {
        return networkName != null ? networkName : "";
    }

    public void setNetworkName(String networkName) {
        this.networkName = networkName != null ? networkName.trim() : "";
    }

    public boolean hasNetworkName() {
        return !getNetworkName().isEmpty();
    }

    // ------------------------------------------------------------------
    // Redstone automation (0.8.x)
    // ------------------------------------------------------------------

    public RedstoneMode getRedstoneMode() {
        return redstoneMode != null ? redstoneMode : RedstoneMode.DEFAULT;
    }

    public void setRedstoneMode(RedstoneMode mode) {
        this.redstoneMode = mode != null ? mode : RedstoneMode.DEFAULT;
    }

    // ------------------------------------------------------------------
    // Ownership / permissions (0.8.x)
    // ------------------------------------------------------------------

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    /**
     * First-come ownership claim: assigns {@code playerUuid} as owner only when the
     * speaker is currently unowned. Returns true when the claim was applied.
     */
    public boolean claimOwnershipIfAbsent(UUID playerUuid) {
        if (playerUuid == null || ownerUuid != null) return false;
        ownerUuid = playerUuid;
        return true;
    }

    public SpeakerAccess getAccessMode() {
        return accessMode != null ? accessMode : SpeakerAccess.DEFAULT;
    }

    public void setAccessMode(SpeakerAccess accessMode) {
        this.accessMode = accessMode != null ? accessMode : SpeakerAccess.DEFAULT;
    }

    public Set<UUID> getTrustedPlayers() {
        if (trustedPlayers == null) trustedPlayers = new HashSet<>();
        return trustedPlayers;
    }

    public boolean isTrusted(UUID playerUuid) {
        return trustedPlayers != null && playerUuid != null && trustedPlayers.contains(playerUuid);
    }

    public void trustPlayer(UUID playerUuid) {
        if (playerUuid != null) getTrustedPlayers().add(playerUuid);
    }

    public void distrustPlayer(UUID playerUuid) {
        if (trustedPlayers != null) trustedPlayers.remove(playerUuid);
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    public float getMaxVolume() {
        return maxVolume;
    }

    public void setMaxVolume(float maxVolume) {
        this.maxVolume = AudioMath.sanitizeFloat(maxVolume, 0.0f, 1.0f, 1.0f);
    }

    public int getMaxRange() {
        return maxRange;
    }

    public void setMaxRange(int maxRange) {
        this.maxRange = Math.max(1, Math.min(Config.speakerRange, maxRange));
    }

    public float getAudioDropoff() {
        return audioDropoff;
    }

    public void setAudioDropoff(float audioDropoff) {
        this.audioDropoff = AudioMath.sanitizeFloat(audioDropoff, 0.0f, 1.0f, 1.0f);
    }

    // ------------------------------------------------------------------
    // Directional audio (0.8.x)
    // ------------------------------------------------------------------

    public float getDirectionality() {
        return AudioMath.sanitizeFloat(directionality, 0.0f, 1.0f, 0.0f);
    }

    public void setDirectionality(float directionality) {
        this.directionality = AudioMath.sanitizeFloat(directionality, 0.0f, 1.0f, 0.0f);
    }

    public int getConeAngleDegrees() {
        return Math.max(5, Math.min(350, coneAngleDegrees));
    }

    public void setConeAngleDegrees(int coneAngleDegrees) {
        this.coneAngleDegrees = Math.max(5, Math.min(350, coneAngleDegrees));
    }

    public float getRearAttenuation() {
        return AudioMath.sanitizeFloat(rearAttenuation, 0.0f, 1.0f, 0.9f);
    }

    public void setRearAttenuation(float rearAttenuation) {
        this.rearAttenuation = AudioMath.sanitizeFloat(rearAttenuation, 0.0f, 1.0f, 0.9f);
    }
}
