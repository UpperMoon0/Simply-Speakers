import copy
import json
import os
import re
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import build_requested_release as build
import release_artifacts as release


def make_jar(directory, target, version=None, **changes):
    version = version or release.version()
    path = directory / release.jar_name(target, version)
    loader, mc = target.split('-', 1)
    with zipfile.ZipFile(path, 'w') as archive:
        major = changes.get('major', 52)
        archive.writestr('com/nstut/simplyspeakers/SpeakerState.class', b'\xca\xfe\xba\xbe' + struct.pack('>HH', 0, major))
        if changes.get('shaded'):
            archive.writestr('com/nstut/openui/Bad.class', b'bad')
        metadata_version = changes.get('metadata_version', version)
        mc = changes.get('minecraft', mc)
        if loader == 'fabric':
            archive.writestr('fabric.mod.json', json.dumps(dict(id='simplyspeakers', version=metadata_version, depends={'minecraft': mc})))
        elif target == 'forge-1.12.2':
            archive.writestr('mcmod.info', json.dumps([dict(modid='simplyspeakers', version=metadata_version, mcversion=mc)]))
        else:
            name = 'META-INF/neoforge.mods.toml' if loader == 'neoforge' else 'META-INF/mods.toml'
            archive.writestr(name, f'[[mods]]\nmodId="simplyspeakers"\nversion="{metadata_version}"\n[[dependencies.simplyspeakers]]\nmodId="minecraft"\nversionRange="[{mc}]"\n')
    return path


class ReleaseArtifactsTest(unittest.TestCase):
    def test_all_ten_native_targets_and_relations(self):
        self.assertEqual(10, len(set(release.TARGETS)))
        for target in release.TARGETS:
            with self.subTest(target=target):
                deps = {d['slug']: d['type'] for d in release.relations(target)['projects']}
                self.assertEqual('requiredDependency', deps['openui-mc'])
                self.assertEqual('requiredDependency', deps['packetweave'])
                self.assertEqual(target != 'forge-1.12.2', 'architectury-api' in deps)
                self.assertEqual(target.startswith('fabric-'), 'fabric-api' in deps)
                if 'patchouli' in deps: self.assertEqual('optionalDependency', deps['patchouli'])
                if target in ('forge-1.12.2', 'forge-1.16.5', 'fabric-1.16.5'):
                    self.assertNotIn('patchouli', deps)

    def test_complete_bundle_with_checksums(self):
        with tempfile.TemporaryDirectory() as raw:
            directory = Path(raw)
            for target in release.TARGETS: make_jar(directory, target)
            entries = release.bundle(directory)
            self.assertEqual(10, len(entries))
            self.assertEqual(10, len((directory / 'SHA256SUMS').read_text().splitlines()))

    def test_missing_wrong_version_and_unexpected_artifacts_rejected(self):
        with tempfile.TemporaryDirectory() as raw:
            directory = Path(raw)
            for target in release.TARGETS: make_jar(directory, target)
            path = directory / release.jar_name('forge-1.12.2', release.version())
            path.unlink()
            with self.assertRaisesRegex(ValueError, 'missing='): release.bundle(directory)
            make_jar(directory, 'forge-1.12.2', '9.9.9')
            with self.assertRaisesRegex(ValueError, 'extra='): release.bundle(directory)
            make_jar(directory, 'forge-1.12.2')
            with self.assertRaisesRegex(ValueError, 'extra='): release.bundle(directory)

    def test_native_artifact_identity_bytecode_and_shading_fail_closed(self):
        for change in ({'metadata_version': '0.10.1'}, {'minecraft': '1.20.1'}, {'major': 61}, {'shaded': True}):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as raw:
                path = make_jar(Path(raw), 'forge-1.12.2', **change)
                with self.assertRaises(ValueError): release.verify_jar(path, 'forge-1.12.2', release.version())

    def test_dependency_release_requires_complete_assets_and_reviewed_tree(self):
        pins = json.loads((release.ROOT / 'docs/requested-port-dependencies.json').read_text())
        def fetch(path):
            if '/commits/' in path: return {'commit': {'tree': {'sha': 'same-reviewed-tree'}}}
            key = 'openui' if path.startswith('UpperMoon0/OpenUI-MC/') else 'packetweave'
            prefix = 'openui-mc' if key == 'openui' else key
            return dict(draft=False, prerelease=False, published_at='2026-10-21T17:00:00Z',
                        assets=[dict(name=f'{prefix}-{target}-{pins[key]["version"]}.jar', size=100, state='uploaded') for target in pins['targets']])
        release.verify_dependency_releases(fetch=fetch)
        for failure in ('missing', 'draft', 'tree'):
            def broken(path):
                data = copy.deepcopy(fetch(path))
                if '/releases/' in path and failure == 'missing': data['assets'].pop()
                if '/releases/' in path and failure == 'draft': data['draft'] = True
                if '/commits/v' in path and failure == 'tree': data['commit']['tree']['sha'] = 'different'
                return data
            with self.subTest(failure=failure), self.assertRaises(ValueError):
                release.verify_dependency_releases(fetch=broken)

    def test_release_workflow_source_pins_match_dependency_lockfile(self):
        """The publish path must use exactly the source verified and released."""
        pins = json.loads((release.ROOT / 'docs/requested-port-dependencies.json').read_text())
        for workflow_name in ('release.yml', 'requested-generations.yml'):
            workflow = (release.ROOT / '.github/workflows' / workflow_name).read_text()
            with self.subTest(workflow=workflow_name):
                for key in ('openui', 'packetweave'):
                    repo = pins[key]['repository']
                    pattern = r'repository:\s*' + re.escape(repo) + r'\s+ref:\s*([0-9a-f]{40})'
                    matches = re.findall(pattern, workflow)
                    self.assertEqual([pins[key]['commit']], matches,
                        f'{workflow_name}: {key} checkout must match release source lockfile')

    def test_native_ci_and_release_share_build_and_verifier(self):
        workflow = (release.ROOT / '.github/workflows/release.yml').read_text()
        def job(name):
            return re.split(r'\n  [a-z][a-z-]*:', workflow.split('\n  ' + name + ':', 1)[1], maxsplit=1)[0]
        # Extract only each target declaration, rather than counting incidental
        # target names elsewhere in comments or download paths.
        requested = set(re.search(r'target: \[([^\]]+)\]', job('build-requested'))[1].split(', '))
        self.assertEqual(set(json.loads((release.ROOT / 'docs/requested-port-dependencies.json').read_text())['targets']), requested)
        old = set(re.findall(r"project: '([^']+)'", job('build')))
        self.assertEqual(set(release.TARGETS), old | requested)
        publication = job('publish-curseforge')
        published = {platform + '-' + version for version, platform in re.findall(r"version: '([^']+)'(?:,|\n)\s+platform: '?([a-z]+)'?", publication)}
        self.assertEqual(set(release.TARGETS), published)
        self.assertIn('needs: [plan, test-shared, build, build-requested]', job('bundle'))
        self.assertIn('needs: [plan, bundle]', publication)
        self.assertIn('needs: [plan, bundle, publish-curseforge]', job('release'))
        for name in ('release.yml', 'requested-generations.yml'):
            self.assertIn('tools/build_requested_release.py', (release.ROOT / '.github/workflows' / name).read_text())
        self.assertIn('verify-dependencies', job('build-requested'))
        self.assertIn('files: |\n            release-bundle/*.jar\n            release-bundle/SHA256SUMS', job('release'))

    def test_build_uses_target_toolchain_and_native_verification(self):
        pins = json.loads((release.ROOT / 'docs/requested-port-dependencies.json').read_text())
        java = {'JAVA_HOME_8_X64': '/jdk8', 'JAVA_HOME_17_X64': '/jdk17', 'JAVA_HOME_25_X64': '/jdk25', 'PATH': '/bin'}
        for target in pins['targets']:
            with self.subTest(target=target), patch.dict(os.environ, java), patch.object(build, 'verify') as verify, patch.object(build, 'verify_jar') as jar, patch.object(build.subprocess, 'run') as run:
                build.build(target)
                self.assertEqual(3, run.call_count)
                verify.assert_called_once_with(build.ROOT)
                jar.assert_called_once_with(target)
                expected = '/jdk8' if target == 'forge-1.12.2' else '/jdk17' if target == 'forge-1.16.5' else '/jdk25'
                for call in run.call_args_list:
                    self.assertEqual(expected, call.kwargs['env']['JAVA_HOME'])
                    self.assertEqual('/jdk17', call.kwargs['env']['JAVA17_HOME'])
                    self.assertTrue(call.kwargs['check'])
                    self.assertIn('build', call.args[0])
                self.assertIn('-PpacketweaveSource=', ' '.join(run.call_args.args[0]))

if __name__ == '__main__': unittest.main()
