import json
import tempfile
import unittest
from pathlib import Path
import legacy_speaker_sources as legacy
import forge_1165_sources as forge
import port_resources
ROOT=Path(__file__).resolve().parents[1]
class RequestedGenerationTest(unittest.TestCase):
    def test_full_runtime_is_generated_without_stale_files(self):
        with tempfile.TemporaryDirectory() as directory:
            target=Path(directory);(target/'Stale.java').write_text('obsolete')
            legacy.generate(target)
            self.assertFalse((target/'Stale.java').exists())
            expected=list((ROOT/'common-1.20.1/src/main/java').rglob('*.java'))
            for source in expected:
                generated=target/source.relative_to(ROOT/'common-1.20.1/src/main/java')
                self.assertTrue(generated.is_file(),source)
            self.assertGreaterEqual(len(list(target.rglob('*.java'))),140)
    def test_native_legacy_block_entities_tick_and_preserve_portable_no_io(self):
        source=(ROOT/'common-1.20.1/src/main/java/com/nstut/simplyspeakers/blocks/entities/SpeakerBlockEntity.java').read_text()
        generated=legacy.convert(source,'SpeakerBlockEntity.java')
        self.assertIn('void tick()',generated);self.assertIn('serverTick(level, getBlockPos()',generated)
        portable=(ROOT/'common-1.20.1/src/main/java/com/nstut/simplyspeakers/portable/PortableSpeakerEndpoint.java').read_text()
        self.assertIn('usesWorldBlockState() { return false; }',legacy.convert(portable,'PortableSpeakerEndpoint.java'))
        adapter=(ROOT/'common-1.16.5/src/main/java/com/nstut/simplyspeakers/compat/LegacyBlockEntity.java').read_text()
        self.assertIn('cachedState : super.getBlockState()',adapter)
    def test_legacy_controller_retirement_does_not_load_chunks_or_replay_edges(self):
        source=next((ROOT/'common-1.20.1/src/main/java').rglob('ControllerCoordinator.java')).read_text()
        generated=legacy.convert(source,'ControllerCoordinator.java')
        self.assertIn('LegacyControllerLifecycle.isCurrent(c)',generated)
        self.assertIn('c.retiredFromWorld();return true',generated)
        self.assertIn('!controllers.contains(c)',generated)
        source=next((ROOT/'common-1.20.1/src/main/java').rglob('RedstoneControllerBlockEntity.java')).read_text()
        self.assertIn('retiredFromWorld() { awaitingFirstObservation = true; }',legacy.convert(source,'RedstoneControllerBlockEntity.java'))
    def test_forge_local_artifacts_are_external_module_dependencies(self):
        text=(ROOT/'forge-1.16.5/build.gradle').read_text()
        self.assertNotIn('fg.deobf(files(',text)
        self.assertIn('includeGroup groupName',text)
        self.assertIn('fg.deobf(openuiModule)',text)
    def test_native_authority_and_upload_bounds_are_retained(self):
        for name in ['SpeakerPacketSecurity.java','UploadAudioDataPacketC2S.java']:
            source=next((ROOT/'common-1.20.1/src/main/java').rglob(name)).read_text()
            generated=legacy.convert(source,name)
            for check in (['hasChunkAt','mayInteract','distanceToSqr'] if name.startswith('SpeakerPacket') else ['readByteArray(32000)','readVarInt()']):
                self.assertIn(check,generated)
    def test_record_identity_is_not_confused_with_native_class_names(self):
        text='import net.minecraft.world.entity.player.Player; class Example { Player player; AccessViewSnapshot.Player view; }'
        mapped=forge.convert(text)
        self.assertIn('PlayerEntity player',mapped);self.assertIn('AccessViewSnapshot.Player view',mapped)
    def test_java8_lowering_preserves_record_value_identity(self):
        mapped=legacy.java8('public record Point(String name, int x) {}')
        for member in ['final class Point','String name()','int x()','boolean equals','int hashCode','String toString']:
            self.assertIn(member,mapped)
        self.assertNotIn('record ',mapped)
    def test_unicode_and_java8_apis_are_lowered(self):
        mapped=legacy.java8('"界".repeat(3); stream.readAllBytes(); List.of("x"); text.isBlank(); Path.of("x");')
        for helper in ['repeat("界",3)','readAllBytes(stream)','list("x")','isBlank(text)','java.nio.file.Paths.get("x")']:
            self.assertIn(helper,mapped)
    def test_comparison_in_draw_string_does_not_look_like_generics(self):
        mapped=legacy.convert('g.drawString(font, Component.literal(x < 2 ? "a" : "b"), 0, 0, x > 1 ? 4 : 3);','DirectionalPreview.java')
        self.assertTrue(mapped.endswith(', true);'))
    def test_legacy_resources_keep_native_recipes_and_models(self):
        with tempfile.TemporaryDirectory() as directory:
            target=Path(directory);port_resources.generate('1.16.5','fabric',target)
            self.assertTrue((target/'assets/simplyspeakers/models/block/speaker.json').is_file())
            recipes=list((target/'data/simplyspeakers/recipes').glob('*.json'));self.assertTrue(recipes)
            for recipe in recipes:
                content=json.loads(recipe.read_text());self.assertNotIn('id',content.get('result',{}));self.assertNotIn('category',content)
            self.assertFalse((target/'assets/simplyspeakers/items').exists())
            circuit=json.loads((target/'data/simplyspeakers/recipes/audio_circuit.json').read_text())
            self.assertEqual(circuit['key']['C']['item'],'minecraft:gold_ingot')
            self.assertEqual(circuit['key']['A']['item'],'minecraft:quartz')
    def test_modern_fabric_optional_recipe_uses_fabric_condition(self):
        with tempfile.TemporaryDirectory() as directory:
            target=Path(directory);port_resources.generate('26.2','fabric',target)
            recipe=json.loads((target/'data/simplyspeakers/recipe/guide_book.json').read_text())
            self.assertNotIn('neoforge:conditions',recipe)
            self.assertEqual(recipe['fabric:load_conditions'][0]['values'],['patchouli'])
    def test_dependency_workflow_pins_match_the_lockfile(self):
        pins=json.loads((ROOT/'docs/requested-port-dependencies.json').read_text())
        workflow=(ROOT/'.github/workflows/requested-generations.yml').read_text()
        for name in ['openui','packetweave']:
            self.assertIn('ref: '+pins[name]['commit'],workflow)
        self.assertNotIn('publishToMavenLocal',workflow)
        self.assertNotIn('contents: write',workflow)
    def test_modern_client_uses_gui_owner_without_raw_opengl(self):
        for source in (ROOT/'common-26.2/src/main/java').rglob('*.java'):
            text=source.read_text();self.assertNotIn('org.lwjgl.opengl',text);self.assertNotIn('Minecraft.getInstance().screen',text)
    def test_requested_metadata_has_exact_mc_and_java_floor(self):
        for target,mc,java in [('fabric-1.16.5','1.16.5','>=8'),('fabric-26.2','26.2','>=25')]:
            meta=json.loads((ROOT/target/'src/main/resources/fabric.mod.json').read_text())
            self.assertEqual(meta['depends']['minecraft'],mc);self.assertEqual(meta['depends']['java'],java)
            self.assertEqual(meta['depends']['packetweave'],'>=0.1.4')
if __name__=='__main__':unittest.main()
