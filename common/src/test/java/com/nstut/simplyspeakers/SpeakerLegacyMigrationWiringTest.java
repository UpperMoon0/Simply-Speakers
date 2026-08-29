package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the legacy-world upgrade path on the speaker block entity: the internal state
 * id must be restored from every historical encoding (UUID-tag, legacy string, absent),
 * a freshly generated id must mark the entity changed so it persists, and standalone
 * speakers without an id must be migrated onto the shared legacy template.
 */
class SpeakerLegacyMigrationWiringTest {
    private static final List<String> VERSION_MODULES =
            List.of("common-1.20.1", "common-1.21.1", "neoforge-26.1.2");

    @Test
    void loadHandlesLegacyStringInternalId() throws IOException {
        for (String module : VERSION_MODULES) {
            String code = speakerBlockEntitySource(module);
            assertTrue(code.contains("UUID.fromString("),
                    module + " load must parse the legacy string-encoded internal id");
            assertTrue(code.contains("catch (Exception e)"),
                    module + " load must fall back to a generated id for corrupt encodings");
        }
    }

    @Test
    void loadGeneratesAndPersistsMissingInternalId() throws IOException {
        for (String module : VERSION_MODULES) {
            String code = speakerBlockEntitySource(module);
            assertTrue(code.contains("boolean migratedInternalId"),
                    module + " load must flag speakers that had no internal id");
            assertTrue(code.contains("internalStateId = UUID.randomUUID();"),
                    module + " load must generate an id when none is stored");
            assertTrue(code.contains("setChanged();"),
                    module + " load must mark the entity changed so the generated id persists");
        }
    }

    @Test
    void loadAppliesLegacyStandaloneTemplateForMigratedSpeakers() throws IOException {
        for (String module : VERSION_MODULES) {
            String code = speakerBlockEntitySource(module);
            assertTrue(
                    code.contains("if (migratedInternalId) ServerSpeakerRegistry.applyLegacyStandaloneTemplate(level, getStateKey());"),
                    module + " load must migrate id-less standalone speakers to the shared template");
            assertTrue(code.contains("ServerSpeakerRegistry.getOrCreateSpeakerState(level, getStateKey())"),
                    module + " load must create the migrated state before applying persisted settings");
        }
    }

    @Test
    void legacyTemplateApplierExistsInEveryRegistry() throws IOException {
        for (String module : VERSION_MODULES) {
            Path registry = moduleRoot(module).resolve(
                    "src/main/java/com/nstut/simplyspeakers/speakers/ServerSpeakerRegistry.java");
            String code = Files.readString(registry);
            assertTrue(code.contains("applyLegacyStandaloneTemplate"),
                    module + " registry must provide the legacy standalone template migration");
        }
    }

    @Test
    void comparatorProgressIsPushedToNeighbours() throws IOException {
        for (String module : VERSION_MODULES) {
            String code = speakerBlockEntitySource(module);
            assertTrue(code.contains("lastComparatorLevel"),
                    module + " must cache the last comparator level");
            assertTrue(code.contains("COMPARATOR_UPDATE_INTERVAL_TICKS"),
                    module + " must throttle comparator recalculation");
            assertTrue(code.contains("level.updateNeighbourForOutputSignal("),
                    module + " must notify comparator neighbours when the level changes");
            assertTrue(code.contains("blockEntity.updateComparatorOutput();"),
                    module + " server tick must refresh the comparator output");
            int stopSection = code.indexOf("public void stopAudio()");
            assertTrue(stopSection >= 0);
            assertTrue(code.indexOf("updateComparatorOutput();", stopSection) > 0,
                    module + " stopAudio must immediately settle the comparator level");
        }
    }

    @Test
    void comparatorCacheDoesNotAffectQueriedOutput() throws IOException {
        for (String module : VERSION_MODULES) {
            String code = speakerBlockEntitySource(module);
            assertTrue(code.contains("public int getComparatorOutput()"),
                    module + " must keep the live comparator query");
            assertFalse(code.contains("return lastComparatorLevel;"),
                    module + " comparator query must compute live progress, not the cache");
        }
    }

    private static String speakerBlockEntitySource(String module) throws IOException {
        return Files.readString(moduleRoot(module).resolve(
                "src/main/java/com/nstut/simplyspeakers/blocks/entities/SpeakerBlockEntity.java"));
    }

    private static Path moduleRoot(String module) throws IOException {
        return findProjectRoot().resolve(module);
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
