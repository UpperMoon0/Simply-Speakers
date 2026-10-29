import json
import unittest
import tempfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
class PacketWeaveIntegrationTest(unittest.TestCase):
    def test_dependency_is_required_on_both_sides(self):
        for target in ('fabric-1.20.1', 'fabric-1.21.1'):
            data = json.loads((ROOT/target/'src/main/resources/fabric.mod.json').read_text())
            self.assertEqual(data['depends']['packetweave'], '>=0.1.5')
        for target, resource in (
            ('forge-1.20.1', 'src/main/resources/META-INF/mods.toml'),
            ('neoforge-1.21.1', 'src/main/resources/META-INF/neoforge.mods.toml'),
            ('neoforge-26.1.2', 'src/main/templates/META-INF/neoforge.mods.toml')):
            block = (ROOT/target/resource).read_text().split('modId = "packetweave"')[1]
            self.assertIn('side = "BOTH"', block)
            self.assertIn('versionRange = "[0.1.5,)"', block)
    def test_pinned_packetweave_matches_vendor_gitlink(self):
        import subprocess
        pin = json.loads((ROOT/'docs/requested-port-dependencies.json').read_text())['packetweave']['commit']
        if not (ROOT/'.git').exists():
            self.skipTest('Git checkout required to inspect submodule gitlink')
        gitlink = subprocess.check_output(
            ['git', 'ls-files', '--stage', '--', 'vendor/packetweave'], cwd=ROOT, text=True
        ).strip().split()
        self.assertEqual(gitlink[0], '160000')
        self.assertEqual(gitlink[1], pin, 'Pinned PacketWeave source and vendor gitlink must match')

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
    def test_generated_targets_preserve_receipt_codec_and_shared_sender(self):
        import legacy_speaker_sources, forge_1165_sources, forge_1122_sources, modern_speaker_sources
        for generator in (legacy_speaker_sources.generate, forge_1165_sources.generate,
                          forge_1122_sources.generate, modern_speaker_sources.generate):
            with self.subTest(generator=generator.__module__), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                generator(root)
                network = root/'com/nstut/simplyspeakers/network'
                receipt = (network/'AudioDownloadReceiptPacketC2S.java').read_text(encoding='utf-8')
                self.assertIn('transferId', receipt)
                self.assertIn('readLong()', receipt)
                self.assertIn('OutboundTransfers.shared().acknowledge(player,', receipt)
                self.assertIn('AudioDownloadReceiptPacketC2S', (network/'PacketRegistries.java').read_text(encoding='utf-8'))
                chunk = (network/'SendAudioFilePacketS2C.java').read_text(encoding='utf-8')
                self.assertIn('transferId', chunk)
                self.assertIn('readByteArray(com.nstut.packetweave.api.OutboundTransfers.CHUNK_BYTES)', chunk)
                client = (root/'com/nstut/simplyspeakers/client/ClientAudioPlayer.java').read_text(encoding='utf-8')
                self.assertIn('OutboundTransfers.shared().start(peer, transactionId,', client)
                self.assertIn('Audio download cancelled before publication', client)
if __name__ == '__main__': unittest.main()
