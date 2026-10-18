package com.nstut.simplyspeakers.compat.sable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the shipped dependency declarations with the loaders' real range parsers. */
class SableCompatibilityMetadataTest {
    private static final Path ROOT = Path.of(System.getProperty("sable.compat.root"));
    private static final String[] ACCEPTED = {"2.0.5", "2.0.6", "2.0.7", "2.1.0", "2.99.0"};
    private static final String[] REJECTED = {"1.6.0", "2.0.4", "3.0.0", "3.1.0"};

    @Test
    void fabricKeepsSableOptionalAndAcceptsStableAndLater2x() throws Exception {
        JsonObject metadata = JsonParser.parseString(Files.readString(ROOT.resolve(
                "fabric-1.21.1/src/main/resources/fabric.mod.json"))).getAsJsonObject();
        assertFalse(metadata.getAsJsonObject("depends").has("sable"));
        VersionPredicate range = VersionPredicate.parse(metadata.getAsJsonObject("suggests")
                .get("sable").getAsString());
        for (String version : ACCEPTED) assertTrue(range.test(Version.parse(version)), version);
        for (String version : REJECTED) assertFalse(range.test(Version.parse(version)), version);
    }

    @Test
    void neoForgeKeepsSableOptionalOnBothSidesAndAcceptsStableAndLater2x() throws Exception {
        String metadata = Files.readString(ROOT.resolve(
                "neoforge-1.21.1/src/main/resources/META-INF/neoforge.mods.toml"));
        String sable = null;
        for (String block : metadata.split("\\[\\[")) {
            if (block.contains("modId = \"sable\"")) {
                assertNull(sable, "Duplicate Sable dependency");
                sable = block;
            }
        }
        assertNotNull(sable, "Missing optional Sable dependency");
        assertTrue(sable.contains("type = \"optional\""));
        assertTrue(sable.contains("side = \"BOTH\""));
        var match = Pattern.compile("versionRange\\s*=\\s*\"([^\"]+)\"").matcher(sable);
        assertTrue(match.find(), "Missing Sable version range");
        VersionRange range = VersionRange.createFromVersionSpec(match.group(1));
        for (String version : ACCEPTED)
            assertTrue(range.containsVersion(new DefaultArtifactVersion(version)), version);
        for (String version : REJECTED)
            assertFalse(range.containsVersion(new DefaultArtifactVersion(version)), version);
    }
}
