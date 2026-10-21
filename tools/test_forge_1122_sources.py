import json
import tempfile
import unittest
from pathlib import Path
import forge_1122_sources as port

class Forge1122GenerationTests(unittest.TestCase):
    def test_wire_registry_preserves_every_packet(self):
        source=port.ROOT/'common-1.20.1/src/main/java/com/nstut/simplyspeakers/network/PacketRegistries.java'
        before=source.read_text();after=port.convert(before,source.name)
        self.assertEqual(before.count('CHANNEL.register('),after.count('CHANNEL.register('))
        self.assertGreaterEqual(after.count('CHANNEL.register('),32)
        self.assertIn('LegacyChannel.create',after)
        self.assertNotIn('architectury',after)
    def test_security_gates_survive_native_translation(self):
        source=port.ROOT/'common-1.20.1/src/main/java/com/nstut/simplyspeakers/network/SpeakerPacketSecurity.java'
        text=port.convert(source.read_text(),source.name)
        for marker in ['MAX_INTERACT_DISTANCE_SQR','isBlockLoaded(pos)','isBlockModifiable(player, pos)','SpeakerPermissions.canControl','SpeakerPermissions.canManage','AudioOwnership.isOwnedBy','StreamTracks.isRemoteStreamUrlAllowed','PortableSpeakerManager.resolve']:
            self.assertIn(marker,text)
        self.assertNotIn('player.level()',text)
        self.assertNotIn('dev.architectury',text)
    def test_controller_keeps_permission_rechecks(self):
        source=port.ROOT/'common-1.20.1/src/main/java/com/nstut/simplyspeakers/blocks/entities/RedstoneControllerBlockEntity.java'
        text=port.convert(source.read_text(),source.name)
        self.assertIn('SpeakerPermissions.canControl(state, actor, false)',text)
        self.assertIn('public void update()',text)
        self.assertIn('awaitingFirstObservation',text)
        self.assertIn('tag.setString("Owner", owner.toString())',text)
    def test_portable_item_preserves_identity_and_native_use(self):
        source=port.ROOT/'common-1.20.1/src/main/java/com/nstut/simplyspeakers/items/PortableSpeakerItem.java'
        text=port.convert(source.read_text(),source.name)
        self.assertIn('onItemRightClick',text)
        self.assertIn('setMaxStackSize(1)',text)
        self.assertIn('tag.removeTag(LINK)',text)
        self.assertIn('PortableSpeakerManager.open',text)
    def test_native_lifecycle_does_not_resume_on_early_sound_event(self):
        root=port.ROOT/'forge-1.12.2/src/main/java/com/nstut/simplyspeakers'
        client=(root/'client/LegacyClient.java').read_text()
        transformer=(root/'forge/AudioTransformer.java').read_text()
        self.assertNotIn('SoundLoadEvent',client)
        self.assertIn('contextReady(boolean ready)',client)
        self.assertIn('LegacySoundContext.ready(ready',client)
        self.assertIn('field_148617_f',transformer)
        self.assertIn('func_148613_b',transformer)
        self.assertIn('!patched[0]||!patched[1]',transformer)
    def test_native_controller_unload_and_identity_guards(self):
        root=port.ROOT/'common-1.20.1/src/main/java/com/nstut/simplyspeakers/blocks/entities'
        tile=port.convert((root/'RedstoneControllerBlockEntity.java').read_text(),'RedstoneControllerBlockEntity.java')
        self.assertIn('void onChunkUnload()',tile)
        self.assertIn('void retiredFromWorld() { awaitingFirstObservation = true; pulseSignal = 0; }',tile)
        hook=tile[tile.index('void onChunkUnload()'):tile.index('boolean coordinatorEligible()')]
        self.assertIn('ControllerCoordinator.remove(this)',hook)
        self.assertIn('super.onChunkUnload()',hook)
        self.assertNotIn('invalidate()',hook)
        coordinator=port.convert((root/'ControllerCoordinator.java').read_text(),'ControllerCoordinator.java')
        self.assertIn('LegacyControllerLifecycle.isCurrent(c)',coordinator)
        self.assertIn('pulses.values().forEach(list -> list.removeIf(c -> !controllers.contains(c)))',coordinator)
    def test_audio_gate_starts_closed_and_checks_real_context(self):
        root=port.ROOT/'forge-1.12.2/src/main/java/com/nstut/simplyspeakers'
        helper=(root/'compat/LegacySoundContext.java').read_text()
        self.assertIn('AL::isCreated',helper)
        self.assertIn('AL::getContext',helper)
        self.assertIn('ALC10::alcGetCurrentContext',helper)
        self.assertIn('owned != null && owned == current.get()',helper)
        source=port.ROOT/'common-1.20.1/src/main/java/com/nstut/simplyspeakers/client/ClientAudioPlayer.java'
        self.assertIn('LegacySoundContext.newSuspendedGate()',port.convert(source.read_text(),source.name))
    def test_gradle_5_inputs_and_exact_native_module_dependencies(self):
        text=(port.ROOT/'forge-1.12.2/build.gradle').read_text()
        self.assertNotIn('inputs.dirs',text)
        self.assertNotIn('fg.deobf(files(',text)
        self.assertIn('fg.deobf(openuiModule)',text)
        self.assertIn('fg.deobf(packetweaveModule)',text)
        self.assertIn('includeGroup groupName',text)
        self.assertIn('localGroups.each { excludeGroup it }',text)
        self.assertIn("moduleName+'-pinned.jar'",text)
    def test_native_resources_only_available_ingredients(self):
        with tempfile.TemporaryDirectory() as tmp:
            port.resources(Path(tmp))
            root=Path(tmp)/'assets/simplyspeakers'
            self.assertFalse((Path(tmp)/'data').exists())
            self.assertFalse((root/'items').exists())
            values={path.stem:json.loads(path.read_text()) for path in (root/'recipes').glob('*.json')}
            self.assertEqual(values['audio_circuit']['key']['C']['item'],'minecraft:gold_ingot')
            self.assertEqual(values['audio_circuit']['key']['A']['item'],'minecraft:quartz')
            self.assertEqual(values['proxy_speaker']['key']['P'],{'item':'minecraft:planks','data':32767})
            self.assertEqual(values['portable_speaker']['key']['N']['item'],'minecraft:noteblock')
            self.assertNotIn('guide_book',values)
            self.assertTrue((root/'lang/en_us.lang').exists())
            self.assertIn('tile.simplyspeakers.speaker.name=',(root/'lang/en_us.lang').read_text())
    def test_tile_registration_waits_until_position_is_attached(self):
        root=port.ROOT/'forge-1.12.2/src/main/java/com/nstut/simplyspeakers'
        base=(root/'compat/LegacyBlockEntity.java').read_text()
        self.assertNotIn('void setWorld(',base)
        self.assertIn('super.writeToNBT(tag)',base)
        for name in ['Speaker','ProxySpeaker','RedstoneController']:
            path=port.ROOT/f'common-1.20.1/src/main/java/com/nstut/simplyspeakers/blocks/entities/{name}BlockEntity.java'
            text=port.convert(path.read_text(),path.name)
            self.assertIn('public void update()',text)
            self.assertIn('super.saveAdditional(tag)',text)
            self.assertNotIn('void setWorld(',text)
    def test_escape_routes_through_controller_save_after_modal_handling(self):
        source=port.ROOT/'common-1.20.1/src/main/java/com/nstut/simplyspeakers/client/ui/SimplySpeakersUiScreen.java'
        text=port.convert(source.read_text(),source.name)
        self.assertIn('protected void keyTyped(char character, int scanCode)',text)
        self.assertIn('Keyboard.KEY_ESCAPE',text)
        self.assertLess(text.index('uiRuntime().keyPressed(256'),text.index('onClose();'))
        controller=port.ROOT/'common-1.20.1/src/main/java/com/nstut/simplyspeakers/client/screens/RedstoneControllerScreen.java'
        self.assertIn('if (fetched && !saveConfiguration()) return;',port.convert(controller.read_text(),controller.name))
    def test_mapping_keeps_owned_record_methods_and_gui_packages(self):
        text=port.convert('import com.nstut.simplyspeakers.client.screens.SpeakerScreen; target.level(); pose.position(); thread.isAlive(); snapshot.dimension(); client.screen;', 'Fixture.java')
        self.assertIn('client.screens.SpeakerScreen',text)
        self.assertIn('target.level()',text)
        self.assertIn('pose.position()',text)
        self.assertIn('thread.isAlive()',text)
        self.assertIn('snapshot.dimension()',text)
        self.assertIn('client.currentScreen',text)
    def test_native_channel_checks_direction_and_bounds(self):
        text=(port.ROOT/'forge-1.12.2/src/main/java/com/nstut/simplyspeakers/compat/LegacyChannel.java').read_text()
        self.assertIn('SimpleNetworkWrapper',text)
        self.assertIn('codec.c2s!=c2s',text)
        self.assertIn('MAX_BYTES',text)
        self.assertIn('Trailing packet data',text)
        self.assertIn('getServerWorld().addScheduledTask',text)

if __name__=='__main__':unittest.main()
