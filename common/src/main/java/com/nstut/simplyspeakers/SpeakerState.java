package com.nstut.simplyspeakers;

/**
 * Represents the state of a speaker network.
 * This class holds all the information needed to manage speaker playback.
 */
public class SpeakerState {
    private String audioId = "";
    private String audioFilename = "";
    private boolean isPlaying = false;
    private boolean isLooping = false;
    private long playbackStartTick = -1; // Tick when playback started, -1 if not playing
    
    /**
     * Default constructor for creating an empty speaker state.
     */
    public SpeakerState() {
    }
    
    /**
     * Constructor for creating a speaker state with specific values.
     *
     * @param audioId The ID of the audio file
     * @param audioFilename The filename of the audio file
     * @param isPlaying Whether the speaker is currently playing
     * @param isLooping Whether the audio should loop
     * @param playbackStartTick The tick when playback started
     */
    public SpeakerState(String audioId, String audioFilename, boolean isPlaying, boolean isLooping, long playbackStartTick) {
        this.audioId = audioId;
        this.audioFilename = audioFilename;
        this.isPlaying = isPlaying;
        this.isLooping = isLooping;
        this.playbackStartTick = playbackStartTick;
    }
    
    // Getters and setters
    public String getAudioId() {
        return audioId;
    }
    
    public void setAudioId(String audioId) {
        this.audioId = audioId;
    }
    
    public String getAudioFilename() {
        return audioFilename;
    }
    
    public void setAudioFilename(String audioFilename) {
        this.audioFilename = audioFilename;
    }
    
    public boolean isPlaying() {
        return isPlaying;
    }
    
    public void setPlaying(boolean playing) {
        isPlaying = playing;
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
     * Calculates the current playback position in seconds.
     *
     * @param currentTick The current game tick
     * @return The playback position in seconds
     */
    public float getPlaybackPositionSeconds(long currentTick) {
        if (playbackStartTick >= 0) {
            long ticksElapsed = currentTick - playbackStartTick;
            return ticksElapsed / 20.0f; // 20 ticks per second
        }
        return 0.0f;
    }
    
    /**
     * Creates a copy of this speaker state.
     *
     * @return A new SpeakerState object with the same values
     */
    public SpeakerState copy() {
        return new SpeakerState(audioId, audioFilename, isPlaying, isLooping, playbackStartTick);
    }
    
    @Override
    public String toString() {
        return "SpeakerState{" +
                "audioId='" + audioId + '\'' +
                ", audioFilename='" + audioFilename + '\'' +
                ", isPlaying=" + isPlaying +
                ", isLooping=" + isLooping +
                ", playbackStartTick=" + playbackStartTick +
                '}';
    }
}