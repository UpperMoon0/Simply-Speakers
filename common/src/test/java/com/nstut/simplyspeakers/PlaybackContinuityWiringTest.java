package com.nstut.simplyspeakers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackContinuityWiringTest {

    private static final List<String> VERSION_MODULES =
            List.of("common-1.20.1", "common-1.21.1", "neoforge-26.1.2");

    @Test
    void clientAudioPlayerDoesNotCullOnMissingBlockEntityOrTimeout() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String playerCode = Files.readString(root.resolve(module).resolve(
                    "src/main/java/com/nstut/simplyspeakers/client/ClientAudioPlayer.java"));

            assertFalse(playerCode.contains("missingBlockEntityTicks"),
                    module + " ClientAudioPlayer must not maintain a missing block entity tick counter");
            assertFalse(playerCode.contains("MISSING_BLOCK_ENTITY_GRACE_TICKS"),
                    module + " ClientAudioPlayer must not enforce a grace ticks threshold");
            assertFalse(playerCode.contains("deadPositions"),
                    module + " ClientAudioPlayer must not cull emitters based on transient BE absence");
            assertTrue(playerCode.contains("public static void stop("),
                    module + " ClientAudioPlayer must retain explicit server-driven stop(pos)");
        }
    }

    @Test
    void serverBlockEntitiesUseListenerRangePolicyAcrossAllVersions() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String speakerCode = Files.readString(root.resolve(module).resolve(
                    "src/main/java/com/nstut/simplyspeakers/blocks/entities/SpeakerBlockEntity.java"));
            String proxyCode = Files.readString(root.resolve(module).resolve(
                    "src/main/java/com/nstut/simplyspeakers/blocks/entities/ProxySpeakerBlockEntity.java"));

            assertTrue(speakerCode.contains("ListenerRangePolicy"),
                    module + " SpeakerBlockEntity must use ListenerRangePolicy for range checks");
            assertTrue(proxyCode.contains("ListenerRangePolicy"),
                    module + " ProxySpeakerBlockEntity must use ListenerRangePolicy for range checks");
        }
    }

    @Test
    void clientAudioPlayerUsesSharedPlaybackMembershipAcrossAllVersions() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String playerCode = Files.readString(root.resolve(module).resolve(
                    "src/main/java/com/nstut/simplyspeakers/client/ClientAudioPlayer.java"));

            assertTrue(playerCode.contains("PlaybackMembership"),
                    module + " ClientAudioPlayer must use shared PlaybackMembership for emitter tracking");
        }
    }

    @Test
    void proxySpeakerStopAudioBroadcastsToAllPlayersAcrossAllVersions() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String proxyCode = Files.readString(root.resolve(module).resolve(
                    "src/main/java/com/nstut/simplyspeakers/blocks/entities/ProxySpeakerBlockEntity.java"));

            int stopMethodStart = proxyCode.indexOf("public void stopAudio()");
            int stopMethodEnd = proxyCode.indexOf("private void tick(", stopMethodStart);
            String stopMethod = proxyCode.substring(stopMethodStart, stopMethodEnd);

            assertTrue(stopMethod.contains("serverLevel.players()"),
                    module + " ProxySpeakerBlockEntity.stopAudio must broadcast stop packets to all players in serverLevel");
        }
    }

    @Test
    void clientEventsRegistersRespawnWithDimensionCheckAcrossAllVersions() throws IOException {
        Path root = findProjectRoot();
        for (String module : VERSION_MODULES) {
            String eventsCode = Files.readString(root.resolve(module).resolve(
                    "src/main/java/com/nstut/simplyspeakers/client/ClientEvents.java"));

            assertTrue(eventsCode.contains("CLIENT_PLAYER_RESPAWN.register"),
                    module + " ClientEvents must register CLIENT_PLAYER_RESPAWN");
            assertTrue(eventsCode.contains("onPlayerRespawn"),
                    module + " ClientEvents must define onPlayerRespawn handler");
            assertTrue(eventsCode.contains("dimension()"),
                    module + " onPlayerRespawn must check level dimension");
            assertTrue(eventsCode.contains("ClientAudioPlayer.stopAll()"),
                    module + " onPlayerRespawn must clear playback on dimension change");
        }
    }

    @Test
    void modMetadataTemplatesUseOpenUiVersionProperty() throws IOException {
        Path root = findProjectRoot();
        List<Path> metadataTemplates = List.of(
                root.resolve("forge-1.20.1/src/main/resources/META-INF/mods.toml"),
                root.resolve("neoforge-1.21.1/src/main/resources/META-INF/neoforge.mods.toml"),
                root.resolve("neoforge-26.1.2/src/main/templates/META-INF/neoforge.mods.toml"),
                root.resolve("fabric-1.20.1/src/main/resources/fabric.mod.json"),
                root.resolve("fabric-1.21.1/src/main/resources/fabric.mod.json")
        );

        for (Path template : metadataTemplates) {
            String content = Files.readString(template);
            assertTrue(content.contains("${openui_version}"),
                    template + " must use dynamic ${openui_version} property");
            assertFalse(content.contains("0.0.6"),
                    template + " must not contain hardcoded legacy 0.0.6 version");
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
}
