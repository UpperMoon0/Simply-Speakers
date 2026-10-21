import unittest
from pathlib import Path
import requested_live_join as port
import live_join_test as live
class RequestedLiveJoinTest(unittest.TestCase):
    def test_only_unsupported_optional_integrations_are_omitted(self):
        for target in port.TARGETS:
            client,server=port.required_markers(target)
            self.assertIn('SIMPLYSPEAKERS_OPTIONAL_GUIDE_NOT_INCLUDED',client)
            self.assertNotIn('SIMPLYSPEAKERS_GUIDE_PASS',client)
            self.assertNotIn('SIMPLYSPEAKERS_PERIPHERAL_PASS',server)
            for case in live.RELOAD_CASES:
                self.assertIn('SIMPLYSPEAKERS_AUDIO_RELOAD_PASS '+case,client)
                self.assertIn('SIMPLYSPEAKERS_SERVER_AUDIO_RELOAD_PASS '+case,server)
            for phase in live.PHASES:self.assertIn('SIMPLYSPEAKERS_CLIENT_PHASE_PASS '+phase,client)
            self.assertIn('SIMPLYSPEAKERS_CONTINUOUS_DRAG_PASS',client)
            self.assertIn('SIMPLYSPEAKERS_CLIENT_UPLOAD_PASS',client)
    def test_native_tasks_use_own_wrapper_and_bounded_jvm(self):
        root=Path('/fixture')
        for target in port.TARGETS:
            command=port.command(root,f':{target}:runLiveJoinTestClient')
            self.assertIn(str(root/target/'gradlew'),command)
            self.assertIn('--max-workers=2',command)
            self.assertIn('-Dorg.gradle.jvmargs=-Xmx768m -XX:ActiveProcessorCount=2',command)
            self.assertTrue(any(arg.startswith('-PopenuiJar=') for arg in command))
        with self.assertRaises(ValueError):port.command(root,':unrecognized:runClient')
    def test_existing_supported_targets_are_not_reconfigured_on_import(self):
        self.assertIn('fabric-1.20.1',live.TARGETS)
        self.assertNotIn('fabric-26.2',live.TARGETS)
if __name__=='__main__':unittest.main()
