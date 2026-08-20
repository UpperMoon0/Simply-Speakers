package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeakerRelinkWiringTest {
    @Test
    void minecraft1211MovesStateAndDetachesOldEmitterWhenIdChanges() throws IOException {
        Path root = findProjectRoot();
        String registry = Files.readString(root.resolve(
                "common-1.21.1/src/main/java/com/nstut/simplyspeakers/speakers/ServerSpeakerRegistry.java"));
        String speaker = Files.readString(root.resolve(
                "common-1.21.1/src/main/java/com/nstut/simplyspeakers/blocks/entities/SpeakerBlockEntity.java"));

        assertTrue(registry.contains("SpeakerStateRelinker.stateForNewId"),
                "1.21.1 registry must transfer state when a main speaker ID changes");
        assertTrue(speaker.indexOf("detachEmitterForPowerOff();") < speaker.indexOf("SpeakerRegistry.updateSpeakerId"),
                "old physical emitter must detach without force-stopping the shared network");
        assertTrue(speaker.contains("if (physicallyPowered && !oldKey.equals(newKey))"),
                "only a physically powered speaker may attach and play under its new ID");
    }

    @Test
    void minecraft1211StopDoesNotTrustPossiblyStalePlayingFlag() throws IOException {
        Path root = findProjectRoot();
        String speaker = Files.readString(root.resolve(
                "common-1.21.1/src/main/java/com/nstut/simplyspeakers/blocks/entities/SpeakerBlockEntity.java"));
        String proxy = Files.readString(root.resolve(
                "common-1.21.1/src/main/java/com/nstut/simplyspeakers/blocks/entities/ProxySpeakerBlockEntity.java"));

        assertFalse(speaker.contains("if (!state.isPlaying()) {\n            return;"),
                "speaker stop must still send a packet when registry state is stale");
        assertFalse(proxy.contains("stopAudio exit: Could not get speaker state"),
                "proxy stop must still send a packet when no linked state exists");
    }

    private static Path findProjectRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.exists(candidate.resolve("settings.gradle"))
                    && Files.isDirectory(candidate.resolve("common-1.21.1"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate Simply-Speakers project root");
    }
}
