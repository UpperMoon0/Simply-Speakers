import json
import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
class PacketWeaveIntegrationTest(unittest.TestCase):
    def test_dependency_is_required_on_both_sides(self):
        for target in ('fabric-1.20.1', 'fabric-1.21.1'):
            data = json.loads((ROOT/target/'src/main/resources/fabric.mod.json').read_text())
            self.assertEqual(data['depends']['packetweave'], '>=0.1.2')
        for target, resource in (
            ('forge-1.20.1', 'src/main/resources/META-INF/mods.toml'),
            ('neoforge-1.21.1', 'src/main/resources/META-INF/neoforge.mods.toml'),
            ('neoforge-26.1.2', 'src/main/templates/META-INF/neoforge.mods.toml')):
            block = (ROOT/target/resource).read_text().split('modId = "packetweave"')[1]
            self.assertIn('side = "BOTH"', block)
            self.assertIn('versionRange = "[0.1.2,)"', block)
    def test_no_api_is_embedded(self):
        for target in ('fabric-1.20.1', 'forge-1.20.1', 'fabric-1.21.1', 'neoforge-1.21.1', 'neoforge-26.1.2'):
            build = (ROOT/target/'build.gradle').read_text()
            self.assertNotIn("shadowBundle project(':packetweave-api')", build)
            self.assertNotIn("sourceSets.main.java.srcDir('../vendor/packetweave", build)
    def test_writer_and_sender_duplication_removed(self):
        self.assertFalse((ROOT/'common/src/main/java/com/nstut/simplyspeakers/audio/ChunkedFileTransfer.java').exists())
        for target in ('common-1.20.1', 'common-1.21.1', 'neoforge-26.1.2'):
            audio = (ROOT/target/'src/main/java/com/nstut/simplyspeakers/audio/AudioFileManager.java').read_text()
            self.assertNotIn('appendChunk', audio)
            self.assertNotIn('Files.newOutputStream', audio)
            self.assertIn('BoundedFileReceiver', audio)
            packet = (ROOT/target/'src/main/java/com/nstut/simplyspeakers/network/UploadAudioDataPacketC2S.java').read_text()
            self.assertIn('readByteArray(32000)', packet)
            self.assertIn('readVarInt()', packet)
if __name__ == '__main__': unittest.main()
