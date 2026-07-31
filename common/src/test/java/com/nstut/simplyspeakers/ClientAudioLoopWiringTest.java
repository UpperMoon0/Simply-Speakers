package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientAudioLoopWiringTest {
    private static final List<String> VERSION_MODULES =
            List.of("common-1.20.1", "common-1.21.1", "neoforge-26.1.2");

    @Test
    void everyVersionDrainsQueuedAudioBeforeRestartingLoop() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String code = readPlayer(root, module);
            assertTrue(code.contains("endOfStream && queuedBuffers == 0"),
                    module + " must wait for queued audio to finish before restarting");
            assertTrue(code.contains("Draining queued audio before restart"),
                    module + " must enter the EOF drain state");
        }
    }

    @Test
    void everyVersionResetsLoopBeforeQueuingNextCycle() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String code = readPlayer(root, module);
            int loopRestart = code.indexOf("Audio track finished for {}. Looping enabled, restarting.");
            int nextCycle = code.indexOf("// The outer while loop will re-initialize", loopRestart);
            String resetBlock = code.substring(loopRestart, nextCycle);

            assertFalse(resetBlock.contains("Minecraft.getInstance().tell"),
                    module + " must not defer loop reset");
            assertFalse(resetBlock.contains("Minecraft.getInstance().execute"),
                    module + " must not defer loop reset");
        }
    }

    private static String readPlayer(Path root, String module) throws IOException {
        return Files.readString(root.resolve(module).resolve(
                "src/main/java/com/nstut/simplyspeakers/client/ClientAudioPlayer.java"));
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
