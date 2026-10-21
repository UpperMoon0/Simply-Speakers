"""Safety/coverage contracts for the separate opt-in 1.16 integrated acceptance lane.

These source checks do not assert native acceptance. The harness must separately
require both live logical-server and client completion markers on Java 8.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "common-1.16.5/src/main/java/com/nstut/simplyspeakers"
SERVER = (SOURCES / "testing/IntegratedAudioServerProbe.java").read_text()
CLIENT = (SOURCES / "client/IntegratedAudioClientProbe.java").read_text()


class IntegratedAudioProbeContractTest(unittest.TestCase):
    def test_opt_in_inertness_precedes_game_access(self):
        self.assertIn('Boolean.getBoolean("simplyspeakers.integratedAcceptance")', SERVER)
        server_tick = SERVER.split("public static void tick(MinecraftServer server) {", 1)[1]
        self.assertLess(server_tick.index("if (!enabled()) return;"), server_tick.index("server.isDedicatedServer()"))
        client_tick = CLIENT.split("public static void tick(Minecraft client) {", 1)[1]
        self.assertLess(client_tick.index("if (!IntegratedAudioServerProbe.enabled() || finished) return;"),
                        client_tick.index("client.player"))

    def test_neither_probe_can_enter_world_or_change_native_access(self):
        for source in (SERVER, CLIENT):
            for forbidden in ("allowsMultiplayer", "setAllowsMultiplayer", "createLevel(", "loadLevel(",
                              "doLoadLevel(", "joinWorld(", "ConnectScreen", "UserApiService", "setUser(",
                              "setSingleplayer", "setPublished", "registerReceiver("):
                self.assertNotIn(forbidden, source)
        self.assertIn("client.getSingleplayerServer() != null && client.isLocalServer()", CLIENT)
        self.assertIn("!client.getSingleplayerServer().isPublished()", CLIENT)
        self.assertIn('"SSIClient".equals', SERVER)
        self.assertIn('"SSIClient".equals', CLIENT)

    def test_integration_does_not_reuse_dedicated_success_markers(self):
        for source in (SERVER, CLIENT):
            markers = re.findall(r'SIMPLYSPEAKERS_[A-Z_]+', source)
            self.assertTrue(markers)
            self.assertTrue(all(marker.startswith("SIMPLYSPEAKERS_INTEGRATED_") for marker in markers))
            self.assertNotIn("LivePlaybackServerProbe.tick", source)
            self.assertNotIn("LivePlaybackClientProbe.tick", source)
        self.assertIn('"SIMPLYSPEAKERS_INTEGRATED_SERVER_PASS', SERVER)
        self.assertIn('"SIMPLYSPEAKERS_INTEGRATED_CLIENT_PASS', CLIENT)
        self.assertIn('"1.8".equals(System.getProperty("java.specification.version"))', CLIENT)
        self.assertIn('"SIMPLYSPEAKERS_INTEGRATED_RUNTIME_PASS javaMajor=8"', CLIENT)

    def test_small_fixture_never_replaces_terrain_or_creates_playback(self):
        self.assertIn("WaveFixture.tone(30)", SERVER)
        self.assertIn("level.hasChunkAt(candidate) && level.isEmptyBlock(candidate)", SERVER)
        self.assertIn("radius <= 4", SERVER)
        self.assertIn("y <= 4", SERVER)
        self.assertIn("player.distanceToSqr", SERVER)
        self.assertNotIn("ServerSpeakerControlService", SERVER)
        self.assertNotIn(".transportAction(", SERVER)
        self.assertNotIn(".startPlaybackAt(", SERVER)
        self.assertNotIn(".setPlaying(true)", SERVER)

    def test_transport_and_acknowledgement_cross_real_handler_paths(self):
        self.assertIn("PacketRegistries.CHANNEL.sendToServer(new TransportControlPacketC2S", CLIENT)
        self.assertIn("client.getConnection().send(new ServerboundChatPacket", CLIENT)
        self.assertIn("context.getSource().getPlayerOrException()", SERVER)
        self.assertIn("sender == participant && sender.connection == participantConnection", SERVER)
        self.assertIn("sender.getLevel() == level", SERVER)
        self.assertIn("audioId.equals(observedAudio)", SERVER)
        self.assertIn("PHASES[phase].equals(observed)", SERVER)
        self.assertIn("currentServer.isSameThread()", SERVER)
        self.assertIn("ClientSpeakerRegistry.getState(KEY)", CLIENT)
        self.assertNotIn("ServerSpeakerRegistry", CLIENT)
        self.assertNotIn("ServerSpeakerControlService", CLIENT)

    def test_reload_requires_decoding_native_progress_and_epoch_cleanup(self):
        for required in ("snapshot.decodedBytes() > 0", "AL11.AL_SEC_OFFSET", "progressResource != current",
                         "progressTicks >= 10", "client.reloadResourcePacks()", "reloadFuture.join()",
                         "epoch <= reloadOldEpoch", "current == reloadResource", "!cleaned(reloadResource)",
                         'field(resource, "cleanupComplete")', "!worker.isAlive()", "gate().call(reloadOldEpoch",
                         "paused playback resurrected", "audibleOutputVerified=false"):
            self.assertIn(required, CLIENT)

    def test_ui_uses_native_inputs_without_seeding_preview_state(self):
        for required in ("new SpeakerScreen(position)", "screen.mouseClicked(", "screen.mouseReleased(",
                         "screen.charTyped(", "screen.keyPressed(GLFW.GLFW_KEY_ESCAPE", "GLFW.glfwSetWindowSize",
                         "screen.width < wideWidth", "server-backed settings did not survive close/reopen",
                         'helper("keyed"', 'helper("findTabs"', 'helper("findButton"'):
            self.assertIn(required, CLIENT)
        self.assertNotIn(".setAccessible(true);field.set(", CLIENT)
        self.assertNotIn("updateAudioList(", CLIENT)
        self.assertNotIn("updateAccessModel(", CLIENT)
        self.assertNotIn("SpeakerUiPreviewProbe.tick(", CLIENT)
        self.assertIn("IntegratedAudioServerProbe.SETTINGS_NAME.equals(state.getNetworkName())", CLIENT)
        self.assertIn("SETTINGS_NAME.equals(state.getNetworkName())", SERVER)


if __name__ == "__main__":
    unittest.main()
