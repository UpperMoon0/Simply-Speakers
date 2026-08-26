package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the walk-away/walk-back playback continuity fix:
 * - transient client-side block entity absence (chunk load/unload churn) must not
 *   instantly tear down audio streams; culling requires sustained absence, and
 * - server-side listener scans must apply exit hysteresis so players at the range
 *   boundary are not force-stopped and then never re-sent a play packet.
 */
class PlaybackContinuityWiringTest {
    private static final List<String> VERSION_MODULES =
            List.of("common-1.20.1", "common-1.21.1", "neoforge-26.1.2");
    private static final String GRACE_CULL_CONDITION =
            "++data.missingBlockEntityTicks >= MISSING_BLOCK_ENTITY_GRACE_TICKS";

    @Test
    void everyVersionGracesTransientMissingBlockEntities() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String code = readClientPlayer(root, module);
            assertTrue(code.contains("MISSING_BLOCK_ENTITY_GRACE_TICKS = 40"),
                    module + " must declare a 40-tick missing block entity grace period");
            assertTrue(code.contains("int missingBlockEntityTicks"),
                    module + " emitter cache must track consecutive missing ticks");
            assertTrue(code.contains(GRACE_CULL_CONDITION),
                    module + " must only cull an emitter after sustained block entity absence");
        }
    }

    @Test
    void foundBlockEntityRefreshResetsGraceCounter() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String code = readClientPlayer(root, module);
            long resets = code.lines().filter(line -> line.contains("data.missingBlockEntityTicks = 0;")).count();
            assertTrue(resets >= 2,
                    module + " must reset the grace counter for both speaker and proxy emitters (found " + resets + ")");
        }
    }

    @Test
    void cullingStillRemovesMembershipAndCacheAfterGraceExpires() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String code = readClientPlayer(root, module);
            int cullCondition = code.indexOf(GRACE_CULL_CONDITION);
            int evictionLoop = code.indexOf("for (BlockPos dead : deadPositions)");
            assertTrue(cullCondition >= 0 && evictionLoop > cullCondition,
                    module + " must still evict culled positions after the grace period expires");
            for (String marker : new String[]{"positions.remove(dead);", "cachedEmitters.remove(dead);"}) {
                assertTrue(code.contains(marker),
                        module + " must clean up " + marker + " when an emitter is finally culled");
            }
        }
    }

    @Test
    void everyListenerScanAppliesExitHysteresis() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            for (String blockEntity : new String[]{"SpeakerBlockEntity.java", "ProxySpeakerBlockEntity.java"}) {
                String code = readBlockEntity(root, module, blockEntity);
                assertTrue(code.contains("LISTENER_EXIT_HYSTERESIS = 2.0"),
                        module + "/" + blockEntity + " must declare a 2-block listener exit hysteresis");
                String compact = code.replaceAll("\\s+", "");
                assertTrue(compact.contains(
                        "listeningPlayers.contains(player.getUUID())?effectiveRange+LISTENER_EXIT_HYSTERESIS"),
                        module + "/" + blockEntity + " must extend the range only for existing listeners "
                                + "(entry stays at effectiveRange, exit gets the hysteresis margin)");
                assertFalse(code.contains("effectiveRange + 2.0"),
                        module + "/" + blockEntity + " must use the named hysteresis constant, not a magic number");
            }
        }
    }

    private static String readClientPlayer(Path root, String module) throws IOException {
        return Files.readString(root.resolve(module).resolve(
                "src/main/java/com/nstut/simplyspeakers/client/ClientAudioPlayer.java"));
    }

    private static String readBlockEntity(Path root, String module, String blockEntity) throws IOException {
        return Files.readString(root.resolve(module).resolve(
                "src/main/java/com/nstut/simplyspeakers/blocks/entities/" + blockEntity));
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
