package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeakerLifecycleWiringTest {
    private static final List<String> VERSION_MODULES =
            List.of("common-1.20.1", "common-1.21.1", "neoforge-26.1.2");
    private static final Pattern SET_REMOVED_METHOD =
            Pattern.compile("@Override\\s+public void setRemoved\\(\\) \\{(?<body>.*?)\\n    \\}", Pattern.DOTALL);

    @Test
    void chunkUnloadLifecycleDoesNotStopOrDeletePlaybackState() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            assertSetRemovedDoesNotStopPlayback(root, module, "SpeakerBlockEntity.java", "unregisterSpeaker");
            assertSetRemovedDoesNotStopPlayback(root, module, "ProxySpeakerBlockEntity.java", "unregisterProxySpeaker");
        }
    }

    @Test
    void actualBlockRemovalStillStopsAndUnregistersSpeakers() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            if (module.equals("neoforge-26.1.2")) {
                assertPreRemoveCleanup(root, module, "SpeakerBlockEntity.java", "unregisterSpeaker");
                assertPreRemoveCleanup(root, module, "ProxySpeakerBlockEntity.java", "unregisterProxySpeaker");
            } else {
                assertBlockRemovalCleanup(root, module, "SpeakerBlock.java", "unregisterSpeaker");
                assertBlockRemovalCleanup(root, module, "ProxySpeakerBlock.java", "unregisterProxySpeaker");
            }
        }
    }

    private static void assertSetRemovedDoesNotStopPlayback(
            Path root,
            String module,
            String blockEntity,
            String unregisterCall) throws IOException {
        Path source = root.resolve(module).resolve(
                "src/main/java/com/nstut/simplyspeakers/blocks/entities/" + blockEntity);
        String code = Files.readString(source);
        Matcher matcher = SET_REMOVED_METHOD.matcher(code);
        assertTrue(matcher.find(), module + "/" + blockEntity + " must declare setRemoved");

        String body = matcher.group("body");
        assertFalse(body.contains("stopAudio()"),
                module + "/" + blockEntity + " setRemoved runs during chunk unload and must not stop playback");
        assertFalse(Pattern.compile("(?<![A-Za-z0-9_])SpeakerRegistry\\." + unregisterCall + "\\(").matcher(body).find(),
                module + "/" + blockEntity + " setRemoved runs during chunk unload and must not delete registry state");
    }

    private static void assertBlockRemovalCleanup(
            Path root,
            String module,
            String block,
            String unregisterCall) throws IOException {
        Path source = root.resolve(module).resolve(
                "src/main/java/com/nstut/simplyspeakers/blocks/" + block);
        String code = Files.readString(source);
        assertTrue(code.contains("onRemove"),
                module + "/" + block + " must clean up during actual block removal");
        assertTrue(code.contains("stopAudio()"),
                module + "/" + block + " must stop audio when the block is actually removed");
        assertTrue(code.contains(unregisterCall),
                module + "/" + block + " must unregister when the block is actually removed");
    }

    private static void assertPreRemoveCleanup(
            Path root,
            String module,
            String blockEntity,
            String unregisterCall) throws IOException {
        Path source = root.resolve(module).resolve(
                "src/main/java/com/nstut/simplyspeakers/blocks/entities/" + blockEntity);
        String code = Files.readString(source);

        assertTrue(code.contains("preRemoveSideEffects"),
                module + "/" + blockEntity + " must use the 26.1.2 pre-removal hook");
        assertTrue(code.contains("stopAudio()"),
                module + "/" + blockEntity + " must stop audio when the block is actually removed");
        assertTrue(code.contains(unregisterCall),
                module + "/" + blockEntity + " must unregister when the block is actually removed");
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
