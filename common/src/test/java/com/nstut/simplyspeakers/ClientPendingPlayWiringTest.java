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
    void everyVersionPreservesNetworkKeyAndCancelsPendingPlaysOnStop() throws IOException {
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
            int unpoweredBlockStart = speakerCode.indexOf("if (!isPowered)");
            int unpoweredBlockEnd = speakerCode.indexOf("return;", unpoweredBlockStart);
            String unpoweredSection = speakerCode.substring(unpoweredBlockStart, unpoweredBlockEnd);
            assertFalse(unpoweredSection.contains("serverLevel.getPlayers"),
                    module + " speaker must not broadcast stop packets every tick when unpowered");
            assertTrue(unpoweredSection.contains("for (UUID playerId : listeningPlayers)"),
                    module + " speaker must only send stops to listeningPlayers when unpowered");

            String proxyCode = read(root, module, "blocks/entities/ProxySpeakerBlockEntity.java");
            int proxyUnpoweredStart = proxyCode.indexOf("if (!isProxyPlaying)");
            int proxyUnpoweredEnd = proxyCode.indexOf("return;", proxyUnpoweredStart);
            String proxyUnpoweredSection = proxyCode.substring(proxyUnpoweredStart, proxyUnpoweredEnd);
            assertFalse(proxyUnpoweredSection.contains("serverLevel.getPlayers"),
                    module + " proxy must not broadcast stop packets every tick when unpowered");
            assertTrue(proxyUnpoweredSection.contains("for (UUID playerId : listeningPlayers)"),
                    module + " proxy must only send stops to listeningPlayers when unpowered");
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
