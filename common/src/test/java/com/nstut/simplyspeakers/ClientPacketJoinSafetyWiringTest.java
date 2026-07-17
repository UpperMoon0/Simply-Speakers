package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPacketJoinSafetyWiringTest {
    private static final List<String> VERSION_MODULES =
            List.of("common-1.20.1", "common-1.21.1", "neoforge-26.1.2");

    @Test
    void everyPlayPacketDefersUntilTheClientWorldExists() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String packet = read(root, module, "network/PlayAudioPacketS2C.java");
            assertFalse(packet.contains("Minecraft.getInstance().level.isClientSide"),
                    module + " must not dereference the level on the network thread");
            assertTrue(packet.contains("PENDING_PLAYS.defer"),
                    module + " must preserve packets received during join");
            assertTrue(packet.contains("processPendingPlays"),
                    module + " must expose deferred playback processing");

            String events = read(root, module, "client/ClientEvents.java");
            assertTrue(events.contains("PlayAudioPacketS2C.processPendingPlays"),
                    module + " must drain play packets after the world is ready");
            assertTrue(events.contains("PlayAudioPacketS2C.clearPendingPlays"),
                    module + " must discard stale packets on disconnect");
        }
    }

    private static String read(Path root, String module, String relativePath) throws IOException {
        return Files.readString(root.resolve(module).resolve(
                "src/main/java/com/nstut/simplyspeakers/" + relativePath));
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
