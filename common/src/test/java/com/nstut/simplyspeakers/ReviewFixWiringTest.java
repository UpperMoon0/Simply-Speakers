package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cheap cross-tree regression coverage for security and synchronization rules
 * that live in Minecraft-version source sets and are expensive to boot repeatedly.
 */
class ReviewFixWiringTest {

    private static final List<String> VERSION_MODULES =
            List.of("common-1.20.1", "common-1.21.1", "neoforge-26.1.2");

    @Test
    void remoteEofIdentityComesFromPersistentSpeakerOccurrences() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String manager = read(root, module, "speakers/ServerPlaybackManager.java");
            assertFalse(manager.contains("playbackGenerations"), module + " must not keep a second generation map");
            assertFalse(manager.contains("generationAudioId"), module + " must not infer sessions from audio ids");
            assertFalse(manager.contains("currentPlaybackGeneration("), module + " must not mint generations while building packets");
            assertTrue(manager.contains("state.ensurePlaybackSessionGeneration()"),
                    module + " EOF validation/play packets must use SpeakerState occurrence identity");
            assertTrue(manager.contains("beginNewPlaybackSession(emitter.fullStateKey());")
                            && manager.contains("state.startPlaybackAt(level.getGameTime(), 0.0f);"),
                    module + " natural playlist advancement must start a fresh occurrence");
        }
    }

    @Test
    void emptyPlaylistsAreBroadcastAndRequestable() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String service = read(root, module, "speakers/ServerSpeakerControlService.java");
            String manager = read(root, module, "speakers/ServerPlaybackManager.java");
            String request = read(root, module, "network/RequestPlaylistPacketC2S.java");

            assertFalse(service.contains("state == null || !state.hasPlaylist()"),
                    module + " service must broadcast zero-entry playlist snapshots");
            assertFalse(manager.contains("state == null || !state.hasPlaylist()"),
                    module + " playback manager must broadcast zero-entry playlist snapshots");
            assertTrue(request.contains("new PlaylistSyncPacketS2C("),
                    module + " explicit requests must return an authoritative snapshot even when empty");
        }
    }

    @Test
    void playlistSnapshotRequestsUseFullSpeakerAuthorization() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String request = read(root, module, "network/RequestPlaylistPacketC2S.java");
            assertTrue(request.contains("SpeakerPacketSecurity.canControlSpeaker(player,"),
                    module + " playlist reads must enforce distance, interaction and network access policy");
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