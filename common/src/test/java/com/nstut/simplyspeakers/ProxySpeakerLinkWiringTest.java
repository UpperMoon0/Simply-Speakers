package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxySpeakerLinkWiringTest {
    private static final List<String> VERSION_MODULES =
            List.of("common-1.20.1", "common-1.21.1", "neoforge-26.1.2");

    @Test
    void everyProxyLinkPathRejectsEmptyIds() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            assertUsesLinkValidation(root, module, "SpeakerRegistry.java");
            assertUsesLinkValidation(root, module, "client/ClientSpeakerRegistry.java");
            assertUsesLinkValidation(root, module, "blocks/entities/ProxySpeakerBlockEntity.java");
            assertUsesLinkValidation(root, module, "network/SpeakerStateUpdatePacketS2C.java");
        }
    }

    private static void assertUsesLinkValidation(Path root, String module, String relativeSource)
            throws IOException {
        Path source = root.resolve(module).resolve("src/main/java/com/nstut/simplyspeakers")
                .resolve(relativeSource);
        String code = Files.readString(source);
        assertTrue(code.contains("SpeakerLink.isLinkableId"),
                module + "/" + relativeSource + " must reject empty proxy link IDs");
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
