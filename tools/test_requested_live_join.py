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
    def test_confirmed_native_eligibility_boundary_is_failure_not_join(self):
        for target, title in [('fabric-1.16.5', 'net.minecraft.client.gui.screens.TitleScreen'),
                              ('forge-1.16.5', 'net.minecraft.client.gui.screen.MainMenuScreen')]:
            for role in ('carrier', 'observer'):
                line = '[Render thread] SIMPLYSPEAKERS_PREJOIN_WAIT screen=' + title + ', overlay=none, multiplayerAllowed=false'
                with self.assertRaisesRegex(RuntimeError, 'native Minecraft multiplayer eligibility is false'):
                    port.reject_failure(target + '/' + role, line)

    def test_eligibility_classifier_is_exact_and_does_not_rewrite_other_states(self):
        line = 'SIMPLYSPEAKERS_PREJOIN_WAIT screen=net.minecraft.client.gui.screens.TitleScreen, overlay=none, multiplayerAllowed=false'
        for prefix in ('fabric-26.2/carrier', 'neoforge-26.2/observer', 'fabric-1.20.1/carrier', 'fabric-1.16.5/server', 'other'):
            port.reject_failure(prefix, line)
        for other in (line.replace('overlay=none', 'overlay=LoadingOverlay'),
                      line.replace('multiplayerAllowed=false', 'multiplayerAllowed=true'),
                      line.replace('TitleScreen', 'ConnectScreen'), line + ' trailing text',
                      line.replace('SIMPLYSPEAKERS_PREJOIN_WAIT', 'OTHER_MARKER')):
            port.reject_failure('fabric-1.16.5/carrier', other)

    def test_existing_native_failure_markers_remain_fatal(self):
        with self.assertRaises(RuntimeError):
            port.reject_failure('fabric-26.2/carrier', 'SIMPLYSPEAKERS_VERIFY_FAIL native fault')

    def test_legacy_fabric_asset_preflight_retries_only_real_loom_download_failure(self):
        from unittest.mock import Mock, patch
        root=Path('/fixture')
        failure=Mock(returncode=1,stdout='> Task :downloadAssets FAILED\\nnet.fabricmc.loom.util.download.DownloadException: Failed to download',stderr='')
        success=Mock(returncode=0,stdout='BUILD SUCCESSFUL',stderr='')
        with patch.object(port.subprocess,'run',side_effect=[failure,success]) as run, \
             patch.object(port.time,'sleep') as sleep, \
             patch.object(port,'ORIGINAL_PREPARE_CLIENT_RUNTIMES') as original:
            port.prepare_client_runtimes(root,'fabric-1.16.5',90)
        self.assertEqual(2,run.call_count)
        self.assertIn('downloadAssets',run.call_args_list[0].args[0])
        self.assertIn('--stacktrace',run.call_args_list[0].args[0])
        self.assertEqual([unittest.mock.call(5)],sleep.call_args_list)
        original.assert_called_once_with(root,'fabric-1.16.5',90)

    def test_asset_preflight_fails_closed_on_non_network_failure_or_exhaustion(self):
        from unittest.mock import Mock, patch
        root=Path('/fixture')
        invalid=Mock(returncode=1,stdout='> Task :compileJava FAILED',stderr='bad java')
        network=Mock(returncode=1,stdout='> Task :downloadAssets FAILED\\nnet.fabricmc.loom.util.download.DownloadException: Failed to download',stderr='')
        for outcomes, count in [([invalid],1),([network]*3,3)]:
            with patch.object(port.subprocess,'run',side_effect=outcomes) as run, \
                 patch.object(port.time,'sleep') as sleep, \
                 patch.object(port,'ORIGINAL_PREPARE_CLIENT_RUNTIMES') as original:
                with self.assertRaises(port.subprocess.CalledProcessError):
                    port.prepare_client_runtimes(root,'fabric-1.16.5',90)
                self.assertEqual(count,run.call_count)
                self.assertEqual(count-1,sleep.call_count)
                original.assert_not_called()

    def test_asset_preflight_does_not_change_other_targets(self):
        from unittest.mock import patch
        with patch.object(port.subprocess,'run') as run, \
             patch.object(port,'ORIGINAL_PREPARE_CLIENT_RUNTIMES') as original:
            port.prepare_client_runtimes(Path('/fixture'),'forge-1.16.5',90)
            port.prepare_client_runtimes(Path('/fixture'),'fabric-26.2',90)
        run.assert_not_called()
        self.assertEqual(2,original.call_count)

    def test_ci_refusal_remains_unqualified_and_strict_manual_mode_is_default(self):
        import inspect
        source=inspect.getsource(port.main)
        self.assertIn("args.accept_unqualified_legacy",source)
        self.assertIn("not args.target.endswith('1.16.5')",source)
        self.assertIn("receipt.update(result='blocked-native-multiplayer'",source)
        self.assertIn("return 0",source)
        self.assertIn("raise",source)
        workflow=(port.ROOT/'.github/workflows/requested-generations.yml').read_text()
        self.assertIn('--require-ci-coverage',workflow)
        self.assertIn('--accept-unqualified-legacy',workflow)
        self.assertNotIn('continue-on-error',workflow)

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
