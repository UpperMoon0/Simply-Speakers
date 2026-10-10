"""Keep device recipes namespaced and equivalent across Minecraft recipe formats."""
import hashlib
import json
from pathlib import Path
import struct
import unittest

ROOT = Path(__file__).resolve().parents[1]
COMMON = ROOT / "common/src/main/resources"
NEO = ROOT / "neoforge-26.1.2/src/main/resources"
COMPONENT = "simplyspeakers:audio_circuit"
DEVICES = ("speaker", "proxy_speaker", "redstone_controller", "guide_book", "portable_speaker")


def recipe(base, name):
    return json.loads((base / f"data/simplyspeakers/recipe/{name}.json").read_text(encoding="utf-8"))


def material(value):
    if isinstance(value, str):
        return value
    return value.get("item", "#" + value.get("tag", ""))


class CraftingResourcesTest(unittest.TestCase):
    def test_every_finished_recipe_consumes_the_mod_component(self):
        for base in (COMMON, NEO):
            for name in DEVICES:
                with self.subTest(base=base, recipe=name):
                    data = recipe(base, name)
                    used = set("".join(data["pattern"])) - {" "}
                    self.assertEqual(used, set(data["key"]))
                    self.assertIn(COMPONENT, [material(data["key"][key]) for key in used])

    def test_recipe_formats_have_identical_materials_layouts_and_outputs(self):
        for name in (*DEVICES, "audio_circuit"):
            with self.subTest(recipe=name):
                common, neo = recipe(COMMON, name), recipe(NEO, name)
                self.assertEqual(common["pattern"], neo["pattern"])
                self.assertEqual(common["result"], neo["result"])
                self.assertEqual({k: material(v) for k, v in common["key"].items()},
                                 {k: material(v) for k, v in neo["key"].items()})
                self.assertTrue(all(1 <= len(row) <= 3 for row in common["pattern"]))
                self.assertEqual(1, len({len(row) for row in common["pattern"]}))

    def test_portable_speaker_preserves_supplied_sprite_and_model_formats(self):
        asset = COMMON / "assets/simplyspeakers"
        png = (asset / "textures/item/portable_speaker.png").read_bytes()
        self.assertEqual("2884cd85f6ab39d2ebe7a9f64f2425cf08fb4b21a9ed856eff84cd81729641ea",
                         hashlib.sha256(png).hexdigest())
        self.assertEqual((16, 16), struct.unpack(">II", png[16:24]))
        self.assertEqual(6, png[25])
        model = json.loads((asset / "models/item/portable_speaker.json").read_text())
        self.assertEqual("minecraft:item/generated", model["parent"])
        self.assertEqual("simplyspeakers:item/portable_speaker", model["textures"]["layer0"])
        item = json.loads((asset / "items/portable_speaker.json").read_text())
        self.assertEqual("simplyspeakers:item/portable_speaker", item["model"]["model"])

    def test_component_is_obtainable_without_any_mod_item(self):
        for base in (COMMON, NEO):
            data = recipe(base, "audio_circuit")
            self.assertEqual(COMPONENT, data["result"]["id"])
            self.assertGreater(data["result"]["count"], 0)
            self.assertTrue(all(material(v).startswith("minecraft:") for v in data["key"].values()))

    def test_english_guide_and_labels_have_no_encoding_damage(self):
        base = COMMON / 'assets/simplyspeakers'
        paths = [base / 'lang/en_us.json', *sorted((base / 'patchouli_books/guide/en_us/entries').glob('*.json'))]
        for path in paths:
            text = path.read_text(encoding='utf-8')
            for damaged in ('\u00c3', '\u00c2', '\u00e2\u20ac', '\u00e2\u201a', '\ufffd'):
                self.assertNotIn(damaged, text, str(path))

    def test_generated_assets_preserve_resolution_and_controller_casing(self):
        for name in ('guide_book', 'audio_circuit'):
            png = (COMMON / f'assets/simplyspeakers/textures/item/{name}.png').read_bytes()
            self.assertEqual((64, 64), struct.unpack('>II', png[16:24]))
            self.assertEqual(6, png[25])
        casing = (COMMON / 'assets/simplyspeakers/textures/block/redstone_controller_casing.png').read_bytes()
        self.assertEqual((16, 16), struct.unpack('>II', casing[16:24]))
        model = json.loads((COMMON / 'assets/simplyspeakers/models/block/redstone_controller.json').read_text())
        for face in ('top', 'side'):
            self.assertEqual('simplyspeakers:block/redstone_controller_casing', model['textures'][face])

    def test_item_has_a_small_transparent_sprite_and_both_model_formats(self):
        png = (COMMON / "assets/simplyspeakers/textures/item/audio_circuit.png").read_bytes()
        self.assertEqual(b"\x89PNG\r\n\x1a\n", png[:8])
        self.assertEqual((64, 64), struct.unpack(">II", png[16:24]))
        self.assertEqual(6, png[25])  # RGBA, preserves the generated transparency.
        model = json.loads((COMMON / "assets/simplyspeakers/models/item/audio_circuit.json").read_text())
        self.assertEqual("simplyspeakers:item/audio_circuit", model["textures"]["layer0"])
        client_item = json.loads((COMMON / "assets/simplyspeakers/items/audio_circuit.json").read_text())
        self.assertEqual("simplyspeakers:item/audio_circuit", client_item["model"]["model"])


if __name__ == "__main__":
    unittest.main()
