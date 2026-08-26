package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackContinuityWiringTest {

    private static final List<String> VERSION_MODULES =
            List.of("common-1.20.1", "common-1.21.1", "neoforge-26.1.2");

    @Test
    void clientAudioPlayerDoesNotCullOnMissingBlockEntityOrTimeout() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String playerCode = Files.readString(root.resolve(module).resolve(
                    "src/main/java/com/nstut/simplyspeakers/client/ClientAudioPlayer.java"));

            assertFalse(playerCode.contains("missingBlockEntityTicks"),
                    module + " ClientAudioPlayer must not maintain a missing block entity tick counter");
            assertFalse(playerCode.contains("MISSING_BLOCK_ENTITY_GRACE_TICKS"),
                    module + " ClientAudioPlayer must not enforce a grace ticks threshold");
            assertFalse(playerCode.contains("deadPositions"),
                    module + " ClientAudioPlayer must not cull emitters based on transient BE absence");
            assertTrue(playerCode.contains("public static void stop("),
                    module + " ClientAudioPlayer must retain explicit server-driven stop(pos)");
        }
    }

    @Test
    void serverBlockEntitiesUseListenerExitHysteresisAcrossAllVersions() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String speakerCode = Files.readString(root.resolve(module).resolve(
                    "src/main/java/com/nstut/simplyspeakers/blocks/entities/SpeakerBlockEntity.java"));
            String proxyCode = Files.readString(root.resolve(module).resolve(
                    "src/main/java/com/nstut/simplyspeakers/blocks/entities/ProxySpeakerBlockEntity.java"));

            assertTrue(speakerCode.contains("LISTENER_EXIT_HYSTERESIS"),
                    module + " SpeakerBlockEntity must use LISTENER_EXIT_HYSTERESIS for range checks");
            assertTrue(proxyCode.contains("LISTENER_EXIT_HYSTERESIS"),
                    module + " ProxySpeakerBlockEntity must use LISTENER_EXIT_HYSTERESIS for range checks");
        }
    }

    private static Path findProjectRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.exists(candidate.resolve("settings.gradle"))
                    && Files.isDirectory(candidate.resolve("common-1.20.1"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate Simply-Speakers project root");
    }
}
