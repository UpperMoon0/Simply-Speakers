package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wiring regression tests for the final review round: EOF quorum re-evaluation
 * on listener churn, playback-generation bumps on semantic restarts, seek
 * hardening, and the CC:Tweaked untrusted-automation ownership model.
 */
class PlaybackEofChurnWiringTest {

    private static final List<String> VERSION_MODULES =
            List.of("common-1.20.1", "common-1.21.1", "neoforge-26.1.2");
    private static final List<String> CC_MODULES =
            List.of(
                    "fabric-1.20.1/src/main/java/com/nstut/simplyspeakers/fabric/compat/computercraft/SimplySpeakersPeripheral.java",
                    "forge-1.20.1/src/main/java/com/nstut/simplyspeakers/forge/compat/computercraft/SimplySpeakersPeripheral.java",
                    "fabric-1.21.1/src/main/java/com/nstut/fabric/simplyspeakers/compat/computercraft/SimplySpeakersPeripheral.java",
                    "neoforge-1.21.1/src/main/java/com/nstut/neoforge/simplyspeakers/compat/computercraft/SimplySpeakersPeripheral.java");

    @Test
    void eofQuorumIsReevaluatedOnEveryListenerChurnPath() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String code = read(root, module, "speakers/ServerPlaybackManager.java");
            assertTrue(code.contains("RemoteEofQuorumEvaluator.shouldAdvance"),
                    module + " must advance EOF through the shared quorum predicate");
            assertTrue(code.contains("reevaluateRemoteEofQuorum(server, emitter.fullStateKey())"),
                    module + " scanEmitter must re-evaluate EOF quorum after range exits");
            assertTrue(code.contains("subscriptions.removePlayer(playerId);\n        reevaluateAllPendingRemoteEof(server);")
                            || code.contains("subscriptions.removePlayer(playerId);\r\n        reevaluateAllPendingRemoteEof(server);"),
                    module + " player quit/dimension change must re-evaluate EOF quorum");
            assertTrue(code.contains("reevaluateAllPendingRemoteEof(server);\n    }\n\n    /**\n     * Unregisters an emitter snapshot")
                            || code.contains("reevaluateAllPendingRemoteEof(server);\r\n    }\r\n\r\n    /**\r\n     * Unregisters an emitter snapshot"),
                    module + " stopEmitter must re-evaluate EOF quorum after emitter teardown");
        }
    }

    @Test
    void loaderMainsPassServerToChurnHandlers() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String code = read(root, module, "SimplySpeakers.java");
            assertTrue(code.contains("ServerPlaybackManager.handlePlayerQuit(player.getServer(), player.getUUID())")
                            || code.contains("ServerPlaybackManager.handlePlayerQuit(player.level().getServer(), player.getUUID())"),
                    module + " main must give handlePlayerQuit the server for EOF quorum re-evaluation");
            assertTrue(code.contains("ServerPlaybackManager.handlePlayerDimensionChange(player.getServer(), player.getUUID())")
                            || code.contains("ServerPlaybackManager.handlePlayerDimensionChange(player.level().getServer(), player.getUUID())"),
                    module + " main must give handlePlayerDimensionChange the server for EOF quorum re-evaluation");
        }
    }

    @Test
    void semanticRestartsBumpThePlaybackGeneration() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String manager = read(root, module, "speakers/ServerPlaybackManager.java");
            assertTrue(manager.contains("public static synchronized void beginNewPlaybackSession(String fullStateKey)"),
                    module + " ServerPlaybackManager must expose beginNewPlaybackSession");
            String service = read(root, module, "speakers/ServerSpeakerControlService.java");
            assertTrue(service.contains("ServerPlaybackManager.beginNewPlaybackSession(fullStateKey);"),
                    module + " control service must mark new playback sessions");
            // Explicit restart, track selection on a playing network, and autoplay
            // of a selected playlist slot are the three restart boundaries.
            assertTrue(countOccurrences(service, "beginNewPlaybackSession(fullStateKey)") >= 3,
                    module + " restart/selectAudio/OP_SELECT_INDEX must each begin a new session");
        }
    }

    @Test
    void seeksAreRejectedWhenStoppedOrNonFinite() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String service = read(root, module, "speakers/ServerSpeakerControlService.java");
            assertTrue(service.contains("if (!Float.isFinite(seconds)) return false;"),
                    module + " seek must reject non-finite values");
            assertTrue(service.contains("if (!state.isPlaying()) return false;\n        // Reject NaN/Infinity")
                            || service.contains("if (!state.isPlaying()) return false;\r\n        // Reject NaN/Infinity"),
                    module + " seek must require a live session");
        }
    }

    @Test
    void speakerStateSeekToIsDefensivelyFinite() throws IOException {
        Path root = findProjectRoot();
        String code = Files.readString(root.resolve("common/src/main/java/com/nstut/simplyspeakers/SpeakerState.java"));
        assertTrue(code.contains("Float.isFinite(seconds) ? seconds : 0.0f"),
                "SpeakerState.seekTo must sanitize non-finite input itself");
    }

    @Test
    void ccSetTrackTreatsAutomationAsUntrusted() throws IOException {
        Path root = findProjectRoot();
        for (String relative : CC_MODULES) {
            String code = Files.readString(root.resolve(relative));
            assertTrue(code.contains("AudioOwnership.isOwnedBy(meta.getOwnerUUID(), ownerUuid)"),
                    relative + " setTrack must enforce library ownership via the network owner");
            assertTrue(code.contains("state.getOwnerUuid()"),
                    relative + " setTrack must resolve ownership from the speaker state owner");
            assertFalse(code.contains("system actor"),
                    relative + " must not claim computers are system actors");
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
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
