package com.nstut.simplyspeakers;

/**
 * Chooses the state a main speaker should use after its network ID changes.
 */
public final class SpeakerStateRelinker {
    private SpeakerStateRelinker() {
    }

    /**
     * A real main speaker already using the destination ID owns that network's
     * state. Otherwise, move the source speaker's state over any placeholder
     * state that may have been created by a proxy.
     */
    public static SpeakerState stateForNewId(
            SpeakerState sourceState,
            SpeakerState destinationState,
            boolean destinationHasMainSpeaker) {
        SpeakerState selected = destinationHasMainSpeaker ? destinationState : sourceState;
        if (selected == null) {
            selected = destinationState;
        }
        return selected == null ? null : selected.copy();
    }
}
