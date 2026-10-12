import unittest
from pathlib import Path
from release_plan import plan


class ReleasePlanTest(unittest.TestCase):
    def test_new_version_publishes(self):
        self.assertEqual(('0.9.1', True), plan('mod_version = 0.9.1\r\n', 'mod_version = 0.9.0', ['v0.9.0']))

    def test_same_version_never_republishes(self):
        self.assertEqual(('0.9.1', False), plan('mod_version = 0.9.1', 'mod_version = 0.9.1', ['v0.9.1']))

    def test_explicit_prepublication_recovery_is_source_and_version_bound(self):
        recovery = {'version': '0.9.1', 'before_sha': 'a' * 40, 'failed_run_id': 38301609532}
        source = 'mod_version = 0.9.1'
        self.assertEqual(('0.9.1', True), plan(source, source, ['v0.9.0'], before='a' * 40, recovery=recovery))
        for before, declaration, tags in (
                ('b' * 40, recovery, []), ('a' * 40, None, []),
                ('a' * 40, recovery, ['v0.9.1']),
                ('a' * 40, {**recovery, 'version': '0.9.2'}, []),
                ('a' * 40, {**recovery, 'failed_run_id': None}, [])):
            self.assertEqual(('0.9.1', False), plan(source, source, tags, before=before, recovery=declaration))

    def test_untagged_unchanged_version_does_not_automatically_republish(self):
        self.assertEqual(('0.9.1', False), plan('mod_version = 0.9.1', 'mod_version = 0.9.1', []))

    def test_existing_version_rejected(self):
        with self.assertRaisesRegex(ValueError, 'already exists'):
            plan('mod_version = 0.9.1', 'mod_version = 0.9.0', ['v0.9.1'])

    def test_initial_push_checks_tag(self):
        with self.assertRaisesRegex(ValueError, 'already exists'):
            plan('mod_version = 0.9.1', None, ['v0.9.1'])

    def test_invalid_version_rejected(self):
        with self.assertRaises(ValueError):
            plan('mod_version = bad/version', None, [])

    def test_verification_pins_own_checkouts_to_exact_head(self):
        text = (Path(__file__).resolve().parents[1] / '.github/workflows/live-join-test.yml').read_text()
        count = text.count('      - uses: actions/checkout@v4')
        self.assertEqual(count, text.count('ref: ${{ github.event.pull_request.head.sha || github.sha }}'))
        self.assertIn('ref: 0d6e10dad6a5ba8615a5798c4369bc4cd6e73300', text)

    def test_release_checkout_initializes_dependencies(self):
        text = (Path(__file__).resolve().parents[1] / '.github/workflows/release.yml').read_text()
        build = text.split('\n  build:', 1)[1].split('\n  publish-curseforge:', 1)[0]
        self.assertIn('submodules: recursive', build)
        self.assertIn('tools/prepare_packetweave.py --target', build)
        self.assertIn('overwrite_files: false', text)
        self.assertIn('target_commitish: ${{ github.sha }}', text)
        self.assertIn('needs: [plan, test-shared, build, publish-curseforge]', text)


if __name__ == '__main__':
    unittest.main()
