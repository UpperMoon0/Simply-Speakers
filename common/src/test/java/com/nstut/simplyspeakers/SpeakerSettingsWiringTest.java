package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeakerSettingsWiringTest {
    private static final List<String> VERSION_MODULES =
            List.of("common-1.20.1", "common-1.21.1", "neoforge-26.1.2");
    private static final List<String> SETTING_PACKETS = List.of(
            "UpdateMaxVolumePacketC2S",
            "UpdateMaxRangePacketC2S",
            "UpdateAudioDropoffPacketC2S");

    @Test
    void everyRegularAndProxyBlockEntityPersistsAndSyncsSettings() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            for (String blockEntity : List.of("SpeakerBlockEntity.java", "ProxySpeakerBlockEntity.java")) {
                Path source = root.resolve(module).resolve(
                        "src/main/java/com/nstut/simplyspeakers/blocks/entities/" + blockEntity);
                String code = Files.readString(source);
                assertContains(code, "SpeakerSettings.read", module + "/" + blockEntity + " must load settings");
                assertContains(code, ".write(", module + "/" + blockEntity + " must save settings");
                assertContains(code, "getUpdateTag", module + "/" + blockEntity + " must send settings to clients");
            }
        }
    }

    @Test
    void everyUiAndNetworkRegistryWiresAllThreeSettingPackets() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String screen = Files.readString(root.resolve(module).resolve(
                    "src/main/java/com/nstut/simplyspeakers/client/screens/SpeakerScreen.java"));
            String packets = Files.readString(root.resolve(module).resolve(
                    "src/main/java/com/nstut/simplyspeakers/network/PacketRegistries.java"));
            for (String packet : SETTING_PACKETS) {
                assertContains(screen, packet, module + " UI must send " + packet);
                assertContains(packets, packet, module + " network registry must register " + packet);
            }
        }
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
