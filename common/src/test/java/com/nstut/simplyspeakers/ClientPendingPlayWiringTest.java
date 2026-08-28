package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPendingPlayWiringTest {
    private static final List<String> VERSION_MODULES =
            List.of("common-1.20.1", "common-1.21.1", "neoforge-26.1.2");

    @Test
    void everyVersionCentralizesUnpoweredStopHandling() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String playerCode = read(root, module, "client/ClientAudioPlayer.java");
            assertTrue(playerCode.contains("Map<String, List<PlayRequest>> pendingPlays"),
                    module + " must store pending plays in a list per audio ID");
            assertTrue(playerCode.contains("req.pos.equals(pos)"),
                    module + " must remove pending play requests on stop(pos)");
            assertTrue(playerCode.contains("req.networkKey != null && req.networkKey.equals(currentNetworkKey)"),
                    module + " must validate network key on download complete");
            assertFalse(playerCode.contains("resolveNetworkKey(pendingPlay.pos)"),
                    module + " must not re-resolve network key from client block entity after download");

            String speakerCode = read(root, module, "blocks/entities/SpeakerBlockEntity.java");
            assertFalse(speakerCode.contains("listeningPlayers"),
                    module + " speaker must delegate listener tracking to ServerPlaybackManager");
            assertFalse(speakerCode.contains("serverLevel.getPlayers"),
                    module + " speaker must not broadcast stop packets every tick when unpowered");

            String proxyCode = read(root, module, "blocks/entities/ProxySpeakerBlockEntity.java");
            assertFalse(proxyCode.contains("listeningPlayers"),
                    module + " proxy must delegate listener tracking to ServerPlaybackManager");
            assertFalse(proxyCode.contains("serverLevel.getPlayers"),
                    module + " proxy must not broadcast stop packets every tick when unpowered");
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
