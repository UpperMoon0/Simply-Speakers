"""Exercise both Fabric target guards with real Fabric metadata and version parsing."""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
LOADER_VERSION = '0.17.2'

HARNESS = r'''
package PACKAGE;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.impl.metadata.*;

public final class DependencyHarness {
    static LoaderModMetadata metadata(Path path) throws Exception {
        try (var input = Files.newInputStream(path)) {
            return ModMetadataParser.parseMetadata(input, path.toString(), List.of(),
                    new VersionOverrides(), new DependencyOverrides(path.getParent()), false);
        }
    }
    static void rejects(LoaderModMetadata owner, LoaderModMetadata openUi) {
        try {
            OpenUiDependencyCheck.validate(EnvType.CLIENT, owner, openUi);
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("OpenUI MC (openui_mc)")) throw expected;
            return;
        }
        throw new AssertionError("Client accepted a missing/incompatible OpenUI");
    }
    public static void main(String[] args) throws Exception {
        var owner = metadata(Path.of(args[0]));
        // Dedicated servers must never inspect client metadata or load OpenUI classes.
        OpenUiDependencyCheck.validate(EnvType.SERVER, null, null);
        if (owner.getDependencies().stream().anyMatch(d -> d.getModId().equals("openui_mc")
                && d.getKind() == ModDependency.Kind.DEPENDS)) {
            throw new AssertionError("Server still requires OpenUI");
        }
        rejects(owner, null);
        rejects(owner, metadata(Path.of(args[1])));
        OpenUiDependencyCheck.validate(EnvType.CLIENT, owner, metadata(Path.of(args[2])));
        OpenUiDependencyCheck.validate(EnvType.CLIENT, owner, metadata(Path.of(args[3])));
        System.out.println("PASS server without OpenUI; client missing, old, minimum, newer versions");
    }
}
'''


def properties():
    values = {}
    for path in ROOT.glob('**/gradle.properties'):
        if 'build' in path.parts or '.gradle' in path.parts:
            continue
        for key, value in re.findall(r'^([\w.]+)\s*=\s*(.*?)\s*$', path.read_text(encoding='utf-8'), re.M):
            values.setdefault(key, value)
    values.update(version='1.0.0', archives_name='simplyspeakers', mod_id='economy')
    return values


class OpenUiDependencyTest(unittest.TestCase):
    def test_all_forge_dependencies_are_client_only(self):
        paths = list(ROOT.glob('forge-*/src/main/resources/META-INF/mods.toml'))
        paths += list(ROOT.glob('neoforge-*/src/main/resources/META-INF/neoforge.mods.toml'))
        paths += list(ROOT.glob('neoforge-*/src/main/templates/META-INF/neoforge.mods.toml'))
        self.assertGreaterEqual(len(paths), 3)
        for path in paths:
            with self.subTest(target=path.parts[-6]):
                blocks = re.split(r'\[\[dependencies\.[^\]]+\]\]', path.read_text(encoding='utf-8'))[1:]
                block, = [b for b in blocks if re.search(r'modId\s*=\s*"openui_mc"', b)]
                self.assertRegex(block, r'side\s*=\s*"CLIENT"')
                self.assertRegex(block, r'(?:mandatory\s*=\s*true|type\s*=\s*"required")')

    def test_fabric_server_and_client_contract(self):
        # No Minecraft or OpenUI classes on this classpath: the prelaunch check
        # must remain safe before game/client classes can be initialized.
        values = properties()
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            override = os.environ.get('FABRIC_LOADER_JAR')
            loader = Path(override) if override else temporary / 'fabric-loader.jar'
            if not override:
                urllib.request.urlretrieve(
                    f'https://maven.fabricmc.net/net/fabricmc/fabric-loader/{LOADER_VERSION}/fabric-loader-{LOADER_VERSION}.jar', loader)
            java_home = os.environ.get('JAVA_HOME')
            javac = str(Path(java_home) / 'bin/javac') if java_home else shutil.which('javac')
            java = str(Path(java_home) / 'bin/java') if java_home else shutil.which('java')
            self.assertTrue(javac and java, 'A JDK is required')
            for target in ('fabric-1.20.1', 'fabric-1.21.1', 'fabric-1.16.5', 'fabric-26.2'):
                with self.subTest(target=target):
                    data = json.loads((ROOT / target / 'src/main/resources/fabric.mod.json').read_text())
                    self.assertEqual(data.get('environment', '*'), '*')
                    self.assertNotIn('openui_mc', data['depends'])
                    self.assertIn('openui_mc', data['suggests'])
                    entrypoint, = data['entrypoints']['preLaunch']
                    package, _ = entrypoint.rsplit('.', 1)
                    source = ROOT / target / 'src/main/java' / (entrypoint.replace('.', '/') + '.java')
                    rendered = re.sub(r'\$\{(\w+)\}', lambda m: values.get(m[1], '1.0.0'), json.dumps(data))
                    metadata = temporary / 'fabric.mod.json'
                    metadata.write_text(rendered)
                    requirement = json.loads(rendered)['suggests']['openui_mc']
                    minimum = requirement.removeprefix('>=')
                    fixtures = []
                    for version in ('0.0.0', minimum, '999.0.0'):
                        fixture = temporary / (version + '.json')
                        fixture.write_text(json.dumps(dict(schemaVersion=1, id='openui_mc', version=version)))
                        fixtures.append(str(fixture))
                    harness = temporary / 'DependencyHarness.java'
                    harness.write_text(HARNESS.replace('PACKAGE', package))
                    subprocess.run([javac, '--release', '17', '-cp', str(loader), '-d', directory,
                                    str(source), str(harness)], check=True)
                    subprocess.run([java, '-cp', os.pathsep.join((directory, str(loader))),
                                    package + '.DependencyHarness', str(metadata), *fixtures], check=True)


if __name__ == '__main__':
    unittest.main()
