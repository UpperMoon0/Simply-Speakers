import copy
import json
from pathlib import Path
import tempfile
import unittest
import requested_1122_ci_gate as gate


def pull_event():
    repo = {'full_name': gate.REPOSITORY, 'fork': False}
    return {'action': 'synchronize', 'number': 26, 'repository': repo,
            'pull_request': {'number': 26, 'state': 'open', 'merged': False,
                'base': {'ref': 'main', 'repo': copy.deepcopy(repo)},
                'head': {'ref': gate.BRANCH, 'repo': copy.deepcopy(repo)}}}


class Requested1122CiGateTest(unittest.TestCase):
    def permit_pull(self, event, name='pull_request', repository=gate.REPOSITORY, ref='refs/pull/26/merge'):
        return gate.allowed(name, event, repository, ref)

    def test_only_owned_open_port_pull_is_approved(self):
        for action in ('opened', 'synchronize', 'reopened'):
            event = pull_event(); event['action'] = action
            self.assertTrue(self.permit_pull(event), action)

    def test_every_other_event_is_rejected(self):
        for name in ('push', 'release', 'pull_request_target', 'workflow_run', 'repository_dispatch', 'schedule', '', None):
            with self.subTest(event=name): self.assertFalse(self.permit_pull(pull_event(), name=name))

    def test_other_pull_action_number_state_ref_or_repository_is_rejected(self):
        paths = [(('action',), 'closed'), (('action',), 'labeled'), (('number',), 39),
                 (('pull_request', 'number'), 39), (('pull_request', 'state'), 'closed'),
                 (('pull_request', 'merged'), True), (('pull_request', 'base', 'ref'), 'other'),
                 (('pull_request', 'head', 'ref'), 'main'),
                 (('pull_request', 'head', 'ref'), gate.BRANCH + '-other')]
        for location in (('repository',), ('pull_request', 'base', 'repo'), ('pull_request', 'head', 'repo')):
            paths.extend([(location + ('full_name',), 'someone/Simply-Speakers'), (location + ('fork',), True)])
        for path, replacement in paths:
            event = pull_event(); cursor = event
            for part in path[:-1]: cursor = cursor[part]
            cursor[path[-1]] = replacement
            with self.subTest(path=path): self.assertFalse(self.permit_pull(event))
        for ref in ('refs/heads/main', 'refs/pull/39/merge', 'refs/heads/' + gate.BRANCH):
            self.assertFalse(self.permit_pull(pull_event(), ref=ref))
        self.assertFalse(self.permit_pull(pull_event(), repository='other/repository'))

    def test_missing_or_malformed_fields_fail_closed(self):
        for event in ({}, None, [], 'not an event'):
            self.assertFalse(self.permit_pull(event))
        for path in [('repository',), ('pull_request',), ('action',), ('number',),
                     ('pull_request', 'head'), ('pull_request', 'base'),
                     ('pull_request', 'head', 'repo'), ('pull_request', 'base', 'repo'),
                     ('pull_request', 'state'), ('pull_request', 'merged'),
                     ('pull_request', 'head', 'repo', 'fork')]:
            for replacement in (None, [], {}, ''):
                event = pull_event(); cursor = event
                for part in path[:-1]: cursor = cursor[part]
                cursor[path[-1]] = replacement
                with self.subTest(path=path, value=replacement): self.assertFalse(self.permit_pull(event))

    def test_manual_dispatch_requires_explicit_true_on_same_owned_branch(self):
        event = {'repository': pull_event()['repository'], 'inputs': {}}
        def permit(ref='refs/heads/' + gate.BRANCH, repository=gate.REPOSITORY):
            return gate.allowed('workflow_dispatch', event, repository, ref)
        for value in (True, 'true'):
            event['inputs']['minecraft_eula_accepted'] = value
            self.assertTrue(permit())
        for value in (False, 'false', None, '', 'TRUE', 1, [], {}):
            event['inputs']['minecraft_eula_accepted'] = value
            with self.subTest(value=value): self.assertFalse(permit())
        event['inputs'] = {}; self.assertFalse(permit())
        event['inputs'] = {'minecraft_eula_accepted': True}
        for ref in ('refs/heads/main', 'refs/tags/v1', 'refs/heads/other', ''):
            self.assertFalse(permit(ref=ref))
        self.assertFalse(permit(repository='other/repo'))
        event['repository']['fork'] = True; self.assertFalse(permit())

    def test_cli_writes_only_fixed_decision_and_bad_json_is_false(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); source = root/'event.json'; output = root/'output'
            args = ['--event-name', 'pull_request', '--event-path', str(source), '--output', str(output),
                    '--repository', gate.REPOSITORY, '--ref', 'refs/pull/26/merge']
            source.write_text(json.dumps(pull_event())); gate.main(args)
            source.write_text('malformed'); gate.main(args)
            source.unlink(); gate.main(args)
            self.assertEqual('accepted=true\naccepted=false\naccepted=false\n', output.read_text())


if __name__ == '__main__': unittest.main()
