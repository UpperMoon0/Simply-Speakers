package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeakerLinkTest {
    @Test
    void emptyIdsAreUnlinked() {
        assertFalse(SpeakerLink.isLinkableId(null));
        assertFalse(SpeakerLink.isLinkableId(""));
        assertFalse(SpeakerLink.isLinkableId("   "));
    }

    @Test
    void nonEmptyIdsCanLink() {
        assertTrue(SpeakerLink.isLinkableId("stage-left"));
    }
}
