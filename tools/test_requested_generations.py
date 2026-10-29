import json
import tempfile
import unittest
from pathlib import Path
import legacy_speaker_sources as legacy
import forge_1165_sources as forge
import port_resources
import modern_speaker_sources as modern
ROOT=Path(__file__).resolve().parents[1]
class RequestedGenerationTest(unittest.TestCase):
    def test_native_block_entity_sync_uses_loader_callbacks_and_shared_identity_checks(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);legacy.generate(root/'fabric');forge.generate(root/'forge')
            for name in ('Speaker','ProxySpeaker','RedstoneController'):
                fabric=(root/'fabric/com/nstut/simplyspeakers/blocks/entities'/f'{name}BlockEntity.java').read_text()
                self.assertIn('compat.FabricBlockEntity',fabric)
                self.assertNotIn('getUpdatePacket()',fabric)
                if name=='RedstoneController':
                    self.assertNotIn('private void sync()',fabric)
                    self.assertIn('private void syncControllerState()',fabric)
                native=(root/'forge/com/nstut/simplyspeakers/blocks/entities'/f'{name}BlockEntity.java').read_text()
                self.assertIn('compat.LegacyBlockEntity',native)
                self.assertIn('getUpdatePacket()',native)
            native=(root/'forge/com/nstut/simplyspeakers/compat/LegacyBlockEntity.java').read_text()
            self.assertIn('packet.getType() == 0 && getBlockPos().equals(packet.getPos())',native)
            self.assertIn('applyClientUpdate(packet.getTag())',native)
        source=(ROOT/'fabric-1.16.5/src/main/java/com/nstut/simplyspeakers/compat/FabricBlockEntity.java').read_text()
        self.assertIn('implements BlockEntityClientSerializable',source)
        self.assertIn('fromClientTag(CompoundTag tag) { applyClientUpdate(tag); }',source)
        self.assertIn('tag.merge(getUpdateTag())',source)
        shared=(ROOT/'common-1.16.5/src/main/java/com/nstut/simplyspeakers/compat/LegacyBlockEntity.java').read_text()
        self.assertIn('return save(new CompoundTag())',shared)
        self.assertIn('!level.isClientSide',shared)
        self.assertIn('type.toString().equals(tag.getString("id"))',shared)
    def test_prejoin_diagnostics_are_opt_in_and_do_not_mutate_ui(self):
        source=next((ROOT/'common-1.20.1/src/main/java').rglob('ClientEvents.java')).read_text()
        generated=legacy.convert(source,'ClientEvents.java')
        diagnostic=generated.split('private static void onClientTick(Minecraft client) {',1)[1].split('ClientPortableSpeakers.tick()',1)[0]
        self.assertIn('Boolean.getBoolean("simplyspeakers.requestedPortRuntime") && LiveJoinTestProtocol.isEnabled()',diagnostic)
        self.assertIn('client.getOverlay()',diagnostic)
        self.assertIn('client.allowsMultiplayer()',diagnostic)
        self.assertNotIn('setScreen',diagnostic)
        self.assertNotIn('connect',diagnostic)

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
        self.assertIn("apply from:'../gradle/port-local-dependencies.gradle'",text)
        shared=(ROOT/'gradle/port-local-dependencies.gradle').read_text()
        self.assertIn('descriptor.includeGroup(groupName)',shared)
        self.assertIn('MavenArtifactRepository',shared)
        self.assertNotIn('exclusiveContent',shared)
        self.assertIn('fg.deobf(openuiModule)',text)
    def test_legacy_client_handler_isolation_preserves_owned_codecs(self):
        for source in (ROOT/'common-1.20.1/src/main/java/com/nstut/simplyspeakers/network').glob('*PacketS2C.java'):
            generated=legacy.convert(source.read_text(),source.name)
            outer,nested=generated.split('private static final class ClientHandler',1)
            self.assertIn('getEnvironment()!=me.shedaniel.architectury.utils.Env.CLIENT',outer)
            self.assertIn('static void handle(',nested)
            self.assertNotIn('matchesScreen(PlaylistSyncPacketS2C pkt, SpeakerScreen screen)',outer)
            self.assertNotIn('private static final DeferredTaskQueue',outer)
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
    def test_legacy_forge_resource_pack_has_native_format(self):
        with tempfile.TemporaryDirectory() as directory:
            target=Path(directory);port_resources.generate('1.16.5','forge',target)
            metadata=json.loads((target/'pack.mcmeta').read_text())
            self.assertEqual(metadata['pack']['pack_format'],6)
            self.assertTrue(metadata['pack']['description'])

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
    def test_native_file_dialogs_share_client_executor_callback_boundary(self):
        for module in ['fabric-1.16.5','forge-1.16.5','fabric-26.2','shared/loader-neoforge']:
            helper=next((ROOT/module/'src/main/java').rglob('*ClientHelper.java')).read_text()
            self.assertIn('ClientFileSelection.open(',helper)
            self.assertIn('Minecraft.getInstance()::execute',helper)
            self.assertNotIn('callback.accept',helper)
    def test_forge112_game_acceptance_requires_explicit_scope_guard(self):
        workflow=(ROOT/'.github/workflows/requested-generations.yml').read_text()
        self.assertIn('minecraft_eula_accepted:',workflow)
        self.assertIn('https://www.minecraft.net/en-us/eula',workflow)
        self.assertIn('default: false',workflow)
        step=workflow.split('- name: Opt-in native Forge 1.12 client and server acceptance',1)[1].split('- name:',1)[0]
        self.assertIn("matrix.target == 'forge-1.12.2' && steps.legacy-consent.outputs.accepted == 'true'",step)
        self.assertIn('tools/requested_1122_ci_gate.py --event-name "$GITHUB_EVENT_NAME"',workflow)
        self.assertIn('JAVA_HOME="$JAVA_HOME_8_X64"',step)
        self.assertIn('tools/requested_1122_runtime.py',step)
        self.assertIn('sudo apt-get install -y --no-install-recommends x11-xserver-utils',step)
        self.assertLess(step.index('command -v xrandr'),step.index("printf 'eula=true"))
        self.assertLess(step.index('command -v xrandr'),step.index('tools/requested_1122_runtime.py'))
        self.assertIn('run/legacy-runtime/receipts/',workflow)
    def test_fabric116_keeps_java8_classpath_and_external_display_owner(self):
        build=(ROOT/'fabric-1.16.5/build.gradle').read_text()
        self.assertIn('tasks.withType(net.fabricmc.loom.task.AbstractRunTask).configureEach { useXvfb.set(false) }',build)
        runtime=(ROOT/'gradle/requested-live-runtime.gradle').read_text()
        self.assertIn('JavaLanguageVersion.of(8)',runtime)
        harness=(ROOT/'tools/live_join_test.py').read_text()
        self.assertIn('cmd = [xvfb, "-a", *cmd]',harness)
    def test_modern_screenshot_uses_native_game_renderer_target(self):
        import modern_speaker_sources as modern
        source=(ROOT/'shared/runtime-verification/src/main/java/com/nstut/simplyspeakers/client/SpeakerUiPreviewProbe.java').read_text()
        generated=modern.convert(source,'SpeakerUiPreviewProbe.java')
        self.assertIn('Object target = client.gameRenderer.mainRenderTarget();',generated)
        self.assertNotIn('getMethod("getMainRenderTarget")',generated)
        self.assertIn('Class.forName("net.minecraft.client.Screenshot")',generated)

    def test_modern_client_uses_gui_owner_without_raw_opengl(self):
        with tempfile.TemporaryDirectory() as directory:
            target=Path(directory);modern.generate(target)
            sources=list(target.rglob('*.java'));self.assertGreaterEqual(len(sources),87)
            for source in sources:
                text=source.read_text();self.assertNotIn('org.lwjgl.opengl',text);self.assertNotIn('Minecraft.getInstance().screen',text)
                self.assertNotIn('gui.screen()s',text)
    def test_modern_generation_reuses_neutral_behavior_and_removes_stale_files(self):
        with tempfile.TemporaryDirectory() as directory:
            target=Path(directory);(target/'Stale.java').write_text('obsolete');modern.generate(target)
            self.assertFalse((target/'Stale.java').exists())
            first={p.relative_to(target):p.read_bytes() for p in target.rglob('*.java')}
            modern.generate(target)
            self.assertEqual(first,{p.relative_to(target):p.read_bytes() for p in target.rglob('*.java')})
            name='com/nstut/simplyspeakers/network/SpeakerPacketSecurity.java'
            original=(ROOT/'neoforge-26.1.2/src/main/java'/name).read_text()
            self.assertEqual((target/name).read_text(),modern.convert(original,'SpeakerPacketSecurity.java'))
            self.assertFalse((target/'com/nstut/simplyspeakers/SimplySpeakers.java').exists())
    def test_requested_metadata_has_exact_mc_and_java_floor(self):
        for target,mc,java in [('fabric-1.16.5','1.16.5','>=8'),('fabric-26.2','26.2','>=25')]:
            meta=json.loads((ROOT/target/'src/main/resources/fabric.mod.json').read_text())
            self.assertEqual(meta['depends']['minecraft'],mc);self.assertEqual(meta['depends']['java'],java)
            self.assertEqual(meta['depends']['packetweave'],'>=0.1.5')
if __name__=='__main__':unittest.main()
