"""Ensure every serialized redstone mode has readable UI text and hover help."""
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
LANG = json.loads((ROOT / "common/src/main/resources/assets/simplyspeakers/lang/en_us.json").read_text(encoding="utf-8"))


class SettingsLocalizationTest(unittest.TestCase):
    def test_all_redstone_modes_have_distinct_readable_labels_and_explanations(self):
        enum = (ROOT / "common/src/main/java/com/nstut/simplyspeakers/control/ControllerAction.java").read_text()
        modes = [m.lower() for m in re.search(r"enum ControllerAction\s*\{([^;]+);", enum, re.S).group(1).replace("\n", "").replace(" ", "").split(",")]
        self.assertTrue(modes)
        labels = []
        for mode in modes:
            with self.subTest(mode=mode):
                key = "gui.simplyspeakers.controller.action." + mode
                label, help_text = LANG[key], LANG["gui.simplyspeakers.controller.help." + mode]
                self.assertNotEqual(mode, label)
                self.assertNotIn("_", label)
                self.assertNotIn("%s", label)
                self.assertGreater(len(help_text), 30)
                labels.append(label)
        self.assertEqual(len(labels), len(set(labels)))

    def test_nonobvious_speaker_and_proxy_settings_have_explanatory_text(self):
        settings = ("speaker_id", "proxy_speaker_id", "network_name",
                    "max_volume", "max_range", "proxy_max_volume", "proxy_max_range", "audio_dropoff",
                    "directionality", "cone_angle", "rear_attenuation")
        for setting in settings:
            with self.subTest(setting=setting):
                self.assertGreater(len(LANG["gui.simplyspeakers." + setting + ".tooltip"]), 20)

    def test_transport_tooltips_describe_actions_in_words(self):
        for action in ('play', 'pause', 'stop', 'next', 'previous', 'restart', 'back30', 'fwd30'):
            self.assertGreaterEqual(len(LANG['gui.simplyspeakers.transport.' + action]), 4)
            self.assertRegex(LANG['gui.simplyspeakers.transport.' + action], r'[A-Za-z]{4}')
        self.assertEqual('Speaker Controller', LANG['block.simplyspeakers.redstone_controller'])
        self.assertNotIn('cannot be deleted', LANG['gui.simplyspeakers.player.delete_playlist_confirm'])

    def test_access_and_stream_dialog_labels_resolve(self):
        for module in ("common-1.20.1", "common-1.21.1", "neoforge-26.1.2"):
            ui = (ROOT / module / "src/main/java/com/nstut/simplyspeakers/client/screens/SpeakerScreen.java").read_text(encoding="utf-8")
            # Include common dialog actions, not just the feature-prefixed strings.
            keys = re.findall(r'Component.translatable\("(gui\.simplyspeakers\.[^"]+)"', ui)
            for key in keys:
                if not key.endswith("."):
                    self.assertIn(key, LANG, (module, key))
            for prefix in ("access", "stream"):
                for suffix in re.findall(prefix + r'Text\("([^"]+)"\)', ui):
                    if not suffix.endswith("."):
                        self.assertIn("gui.simplyspeakers." + prefix + "." + suffix, LANG)
        self.assertEqual("Close", LANG["gui.simplyspeakers.close"])

    def test_cone_slider_range_matches_the_persisted_setting_limits(self):
        state = (ROOT / "common/src/main/java/com/nstut/simplyspeakers/SpeakerState.java").read_text()
        getter = state.split("public int getConeAngleDegrees()", 1)[1].split("}", 1)[0]
        lower, upper = map(int, re.search(r"Math.max\((\d+), Math.min\((\d+),", getter).groups())
        for module in ("common-1.20.1", "common-1.21.1", "neoforge-26.1.2"):
            ui = (ROOT / module / "src/main/java/com/nstut/simplyspeakers/client/screens/SpeakerScreen.java").read_text(encoding="utf-8")
            bounds = tuple(map(float, re.search(r"coneAngle, ([0-9.]+), ([0-9.]+),", ui).groups()))
            self.assertEqual((lower, upper), bounds, module)


if __name__ == "__main__":
    unittest.main()
