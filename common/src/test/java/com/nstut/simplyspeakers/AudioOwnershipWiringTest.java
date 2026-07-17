package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioOwnershipWiringTest {
    private static final List<String> VERSION_MODULES =
            List.of("common-1.20.1", "common-1.21.1", "neoforge-26.1.2");

    @Test
    void everyVersionPersistsAndTransfersAudioOwners() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String metadata = read(root, module, "audio/AudioFileMetadata.java");
            assertContains(metadata, "private final String ownerUUID", module + " must persist an owner");
            assertContains(metadata, "getOwnerUUID()", module + " must expose its owner");
            assertContains(metadata, "writeBoolean(ownerUUID != null)", module + " must encode its owner");

            String manager = read(root, module, "audio/AudioFileManager.java");
            assertContains(manager, "player.getUUID().toString()", module + " must capture the uploader");
            assertContains(manager, "state.ownerUUID", module + " must save the uploader");
            assertContains(manager, "AudioOwnership.ownedBy", module + " must use shared list privacy");
            assertContains(manager, "AudioOwnership.isOwnedBy", module + " must use shared delete privacy");
        }
    }

    @Test
    void everyDeleteResponseUsesThePrivatePlayerList() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String packet = read(root, module, "network/DeleteAudioPacketC2S.java");
            assertContains(packet, "getAudioListForPlayer", module + " must not return the global list after delete");
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

    private static void assertContains(String source, String expected, String message) {
        assertTrue(source.contains(expected), message);
    }
}
