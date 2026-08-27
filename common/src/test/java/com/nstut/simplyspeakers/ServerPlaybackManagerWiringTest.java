package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerPlaybackManagerWiringTest {

    private static final List<String> VERSION_MODULES =
            List.of("common-1.20.1", "common-1.21.1", "neoforge-26.1.2");
    private static final List<String> LOADER_MODULES =
            List.of("fabric-1.20.1", "forge-1.20.1", "fabric-1.21.1", "neoforge-1.21.1", "neoforge-26.1.2");

    @Test
    void everyVersionHasCentralManagerWithSubscriptionIndexes() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String code = read(root, module, "speakers/ServerPlaybackManager.java");
            assertTrue(code.contains("Map<UUID, Set<SpeakerLocation>> playerToEmitters"),
                    module + " ServerPlaybackManager must keep a player-to-emitter index");
            assertTrue(code.contains("Map<SpeakerLocation, Set<UUID>> emitterToPlayers"),
                    module + " ServerPlaybackManager must keep an emitter-to-player index");
            assertTrue(code.contains("handlePlayerQuit"),
                    module + " ServerPlaybackManager must handle player disconnects");
            assertTrue(code.contains("serverTick"),
                    module + " ServerPlaybackManager must expose a server-tick entry point");
            assertTrue(code.contains("stopEmitter"),
                    module + " ServerPlaybackManager must support explicit emitter stops");
            assertTrue(code.contains("ListenerRangePolicy") || code.contains("ServerPlaybackPlanner"),
                    module + " ServerPlaybackManager must reuse the shared range policy stack");
        }
    }

    @Test
    void registryRetainsEmitterSnapshotsAcrossAllVersions() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String code = read(root, module, "speakers/ServerSpeakerRegistry.java");
            assertTrue(code.contains("Map<SpeakerLocation, ServerEmitter> emitters"),
                    module + " ServerSpeakerRegistry must retain emitter snapshots");
            assertTrue(code.contains("upsertEmitter"),
                    module + " ServerSpeakerRegistry must expose emitter upserts");
            assertTrue(code.contains("getEmitters"),
                    module + " ServerSpeakerRegistry must expose emitter enumeration");
            assertTrue(code.contains("emitters.clear()"),
                    module + " ServerSpeakerRegistry must clear emitter snapshots on world reset");
            assertFalse(code.contains("private final Set<UUID> listeningPlayers"),
                    module + " registry must never reintroduce per-BE listener sets");
        }
    }

    @Test
    void loaderMainsDriveCentralManagerLifecycle() throws IOException {
        Path root = findProjectRoot();
        for (String module : LOADER_MODULES) {
            String code = readLoader(root, module);
            assertTrue(code.contains("ServerPlaybackManager.serverTick("),
                    module + " loader must tick ServerPlaybackManager on server ticks");
            assertTrue(code.contains("ServerPlaybackManager.resetForWorld()"),
                    module + " loader must reset ServerPlaybackManager when the server stops");
        }
    }

    @Test
    void commonMainsPurgeSubscriptionsOnPlayerQuit() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String code = read(root, module, "SimplySpeakers.java");
            assertTrue(code.contains("PlayerEvent.PLAYER_QUIT.register"),
                    module + " common main must register the Architectury quit event");
            assertTrue(code.contains("ServerPlaybackManager.handlePlayerQuit"),
                    module + " common main must purge playback subscriptions on quit");
        }
    }

    private static String read(Path root, String module, String relativePath) throws IOException {
        return Files.readString(root.resolve(module).resolve(
                "src/main/java/com/nstut/simplyspeakers/" + relativePath));
    }

    private static String readLoader(Path root, String module) throws IOException {
        String relative;
        switch (module) {
            case "fabric-1.20.1" -> relative = "com/nstut/simplyspeakers/fabric/SimplySpeakersFabric.java";
            case "fabric-1.21.1" -> relative = "com/nstut/fabric/simplyspeakers/SimplySpeakersFabric.java";
            case "forge-1.20.1" -> relative = "com/nstut/simplyspeakers/forge/SimplySpeakersForge.java";
            default -> relative = "com/nstut/neoforge/simplyspeakers/SimplySpeakersForge.java";
        }
        return Files.readString(root.resolve(module).resolve("src/main/java/" + relative));
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
