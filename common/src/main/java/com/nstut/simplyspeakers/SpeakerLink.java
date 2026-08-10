package com.nstut.simplyspeakers;

/**
 * Shared validation rules for links between main and proxy speakers.
 */
public final class SpeakerLink {
    private SpeakerLink() {
    }

    /**
     * An empty or whitespace-only ID represents an unlinked speaker.
     */
    public static boolean isLinkableId(String speakerId) {
        return speakerId != null && !speakerId.trim().isEmpty();
    }
}
