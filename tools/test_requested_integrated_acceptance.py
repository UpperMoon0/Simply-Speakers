import contextlib
import copy
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch
import requested_integrated_acceptance as probe


def refusal(target='fabric-1.16.5', commit='fixture-commit'):
    return {'target':target,'source_commit':commit,'mode':'dedicated-two-client',
            'result':'blocked-native-multiplayer','runtime_qualified':False,'native_multiplayer_allowed':False}


class IntegratedAcceptanceTest(unittest.TestCase):
    def test_coverage_requires_real_native_or_matching_integrated_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); target='fabric-1.16.5'; commit='fixture-commit'
            native=root/target/'build/runtime-evidence/dedicated-qualification.json'
            native.parent.mkdir(parents=True)
            native.write_text(json.dumps({'target':target,'source_commit':commit,
                'mode':'dedicated-two-client','result':'passed','runtime_qualified':True}))
            self.assertEqual('dedicated-two-client',probe.ci_coverage(root,target,commit))
            native.write_text(json.dumps(refusal()))
            self.assertIsNone(probe.ci_coverage(root,target,commit))
            result=root/target/'run/integrated-acceptance/run-1/receipt.json'
            result.parent.mkdir(parents=True)
            valid={'target':target,'source_commit':commit,
                'mode':'integrated-singleplayer-development','result':'passed',
                'dedicated_qualified':False,'two_client_qualified':False,
                'physical_audibility_tested':False}
            for key,bad in [('result','not-completed'),('source_commit','old'),
                            ('mode','dedicated-two-client'),('dedicated_qualified',True),
                            ('two_client_qualified',True),('physical_audibility_tested',True)]:
                wrong=valid.copy();wrong[key]=bad;result.write_text(json.dumps(wrong))
                self.assertIsNone(probe.ci_coverage(root,target,commit))
            result.write_text('invalid-json')
            self.assertIsNone(probe.ci_coverage(root,target,commit))
            result.write_text(json.dumps(valid))
            self.assertEqual('integrated-only-native-blocked',probe.ci_coverage(root,target,commit))
            native.write_text(json.dumps({**refusal(),'result':'failed'}))
            self.assertIsNone(probe.ci_coverage(root,target,commit))
            native.write_text(json.dumps({**refusal(),'source_commit':'old'}))
            self.assertIsNone(probe.ci_coverage(root,target,commit))


    def test_only_exact_current_native_refusal_allows_alternative(self):
        self.assertTrue(probe.allowed_boundary(refusal(),'fabric-1.16.5','fixture-commit'))
        for key, replacement in [('target','forge-1.16.5'),('source_commit','older'),('mode','integrated'),
                                 ('result','passed'),('result','not-completed'),('runtime_qualified',True),
                                 ('native_multiplayer_allowed',True),('native_multiplayer_allowed','false')]:
            data=refusal();data[key]=replacement
            with self.subTest(key=key): self.assertFalse(probe.allowed_boundary(data,'fabric-1.16.5','fixture-commit'))
        for key in refusal():
            data=refusal();data.pop(key)
            self.assertFalse(probe.allowed_boundary(data,'fabric-1.16.5','fixture-commit'))
        for data in (None,[],{},'false'):
            self.assertFalse(probe.allowed_boundary(data,'fabric-1.16.5','fixture-commit'))
        self.assertFalse(probe.allowed_boundary(refusal('fabric-26.2'),'fabric-26.2','fixture-commit'))

    def test_missing_malformed_or_unrelated_boundary_never_launches(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            with patch.object(probe.live,'popen') as popen:
                with self.assertRaisesRegex(RuntimeError,'No current exact'):
                    probe.run(root,'fabric-1.16.5',1,'fixture-commit')
                popen.assert_not_called()
                self.assertFalse((root/'fabric-1.16.5/run').exists())
            file=root/'fabric-1.16.5/build/runtime-evidence/dedicated-qualification.json'
            file.parent.mkdir(parents=True);file.write_text('bad json')
            self.assertFalse(probe.boundary(root,'fabric-1.16.5','fixture-commit'))

    def test_command_has_no_remote_target_or_credentials_and_cannot_escape(self):
        root=Path('/fixture')
        directory=root/'fabric-1.16.5/run/integrated-acceptance/unique'
        command=probe.command(root,'fabric-1.16.5','runIntegratedAcceptance',directory)
        self.assertIn('--max-workers=2',command)
        self.assertIn('-PintegratedRunDir='+str(directory),command)
        for forbidden in ('--server','--port','--accessToken','--disableMultiplayer'):
            self.assertNotIn(forbidden,command)
        for target,task,path in [('fabric-26.2','runIntegratedAcceptance',directory),('fabric-1.16.5','publish',directory),
                                 ('fabric-1.16.5','classes',Path('/outside')),('fabric-1.16.5','classes',directory.parent)]:
            with self.assertRaises(ValueError): probe.command(root,target,task,path)

    def test_native_markers_cannot_be_satisfied_by_dedicated_lane(self):
        for marker in probe.MARKERS: self.assertTrue(marker.startswith('SIMPLYSPEAKERS_INTEGRATED_'))
        self.assertTrue(any('javaMajor=8' in x for x in probe.MARKERS))
        for phase in ('playing','paused'):
            self.assertIn('SIMPLYSPEAKERS_INTEGRATED_AUDIO_RELOAD_PASS '+phase,probe.MARKERS)
        self.assertTrue(any('dedicated=false twoClient=false' in x for x in probe.MARKERS))
        for failure in ('SIMPLYSPEAKERS_INTEGRATED_FAIL disabled control','SIMPLYSPEAKERS_VERIFY_FAIL fault'):
            with self.assertRaises(RuntimeError): probe.reject_failure('fixture/integrated',failure)

    def test_display_owner_is_shared_and_missing_display_fails_closed(self):
        with patch.dict(os.environ,{'DISPLAY':':fixture'}): self.assertEqual(['java'],probe.client_command(['java']))
        with patch.dict(os.environ,{'DISPLAY':''}),patch.object(probe.shutil,'which',return_value=None):
            with self.assertRaises(RuntimeError): probe.client_command(['java'])
        with patch.dict(os.environ,{'DISPLAY':''}),patch.object(probe.shutil,'which',return_value='/xvfb-run'):
            self.assertEqual(['/xvfb-run','-a','java'],probe.client_command(['java']))

    def supervision_fixture(self, cleanup_failure=False, missing_evidence=False, undrained_reader=False):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);target='fabric-1.16.5'
            file=root/target/'build/runtime-evidence/dedicated-qualification.json';file.parent.mkdir(parents=True)
            file.write_text(json.dumps(refusal()))
            process=Mock();process.poll.return_value=None
            output=Mock();output.process=process;output.history=[];output.thread.is_alive.return_value=undrained_reader
            if missing_evidence: output.wait_for_all.side_effect=RuntimeError('Missing genuine integrated markers')
            original=probe.live.reject_failure
            with patch.object(probe,'run_preparation') as prepare,patch.object(probe,'client_command',side_effect=lambda x:x),\
                    patch.object(probe.live,'popen',return_value=process),patch.object(probe.live,'OutputPump',return_value=output),\
                    patch.object(probe.live,'stop_tree',side_effect=RuntimeError('cleanup failed') if cleanup_failure else None) as stop,\
                    contextlib.redirect_stdout(io.StringIO()):
                if cleanup_failure or missing_evidence or undrained_reader:
                    with self.assertRaises(RuntimeError):probe.run(root,target,1,'fixture-commit')
                else:probe.run(root,target,1,'fixture-commit')
                self.assertEqual(2,prepare.call_count)
                stop.assert_called_once_with(process)
            self.assertIs(original,probe.live.reject_failure)
            receipts=list((root/target/'run/integrated-acceptance').glob('*/receipt.json'));self.assertEqual(1,len(receipts))
            result=json.loads(receipts[0].read_text())
            self.assertEqual('not-completed' if cleanup_failure or missing_evidence or undrained_reader else 'passed',result['result'])
            self.assertFalse(result['dedicated_qualified']);self.assertFalse(result['two_client_qualified'])
            self.assertFalse(result['physical_audibility_tested'])
            self.assertFalse(list(root.rglob('eula.txt')))
            self.assertEqual('blocked-native-multiplayer',json.loads(file.read_text())['result'])

    def test_supervision_preserves_dedicated_refusal_and_separate_receipt(self):self.supervision_fixture()
    def test_cleanup_failure_cannot_leave_a_pass_receipt(self):self.supervision_fixture(cleanup_failure=True)
    def test_missing_native_evidence_stays_unqualified_and_cleans_up(self):self.supervision_fixture(missing_evidence=True)
    def test_undrained_output_stays_unqualified_and_restores_supervision(self):self.supervision_fixture(undrained_reader=True)

    def test_driver_only_uses_real_enabled_controls_and_normal_pointer_dispatch(self):
        root=probe.ROOT
        source=(root/'common-1.16.5/src/main/java/com/nstut/simplyspeakers/client/testing/VanillaSingleplayerDriver.java').read_text()
        for expected in ['control.active, control.visible','screen.mouseClicked','screen.mouseReleased','client.isLocalServer()',
                         '!client.getSingleplayerServer().isDedicatedServer()','menu.singleplayer','selectWorld.create']:
            self.assertIn(expected,source)
        for forbidden in ['.setScreen(','.createLevel(','.setLevel(','.connect(','.active =','serversAllowed','disableMultiplayer']:
            self.assertNotIn(forbidden,source)
        build=(root/'gradle/integrated-acceptance.gradle').read_text()
        self.assertIn('JavaLanguageVersion.of(8)',build)
        self.assertIn("programArgs '--username','SSIClient'",build)
        self.assertNotIn('--server',build);self.assertNotIn('--port',build)
        script=(root/'tools/prepare_integrated_acceptance.gradle').read_text()
        self.assertIn('tasks.configureEach',script);self.assertNotIn('tasks.all',script)
        workflow=(root/'.github/workflows/requested-generations.yml').read_text()
        self.assertIn("steps.integrated-boundary.outputs.accepted == 'true'",workflow)
        self.assertIn('dedicated-qualification.json',(root/'tools/requested_live_join.py').read_text())
        self.assertNotIn('continue-on-error',workflow)

if __name__=='__main__':unittest.main()
