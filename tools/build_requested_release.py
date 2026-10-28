#!/usr/bin/env python3
"""Build the pinned standalone ports identically in native CI and release CI."""
import argparse
import json
import os
from pathlib import Path
import subprocess

from verify_port_dependencies import verify
from verify_requested_port_jar import verify as verify_jar

ROOT = Path(__file__).resolve().parents[1]


def build(target, root=ROOT):
    pins = json.loads((root / 'docs/requested-port-dependencies.json').read_text())
    if target not in pins['targets']:
        raise ValueError(f'Unknown requested port: {target}')
    verify(root)
    java = '8' if target == 'forge-1.12.2' else '17' if target == 'forge-1.16.5' else '25'
    env = dict(os.environ)
    env['JAVA_HOME'] = os.environ[f'JAVA_HOME_{java}_X64']
    env['JAVA17_HOME'] = os.environ['JAVA_HOME_17_X64']
    env['PATH'] = str(Path(env['JAVA_HOME']) / 'bin') + os.pathsep + env['PATH']
    options = ['build', '--no-daemon', '--no-parallel', '--max-workers=2']
    if target == 'forge-1.16.5':
        options += ['--init-script', str(root / 'tools/forge_1165_repositories.gradle')]
    for project in (root / '.port-deps/OpenUI-MC' / target,
                    root / '.port-deps/PacketWeave/targets' / target):
        subprocess.run(['bash', 'gradlew', *options], cwd=project, env=env, check=True)
    subprocess.run(['bash', 'gradlew', *options,
        f'-PopenuiJar={root}/.port-deps/OpenUI-MC/{target}/build/libs/openui-mc-{target}-{pins["openui"]["version"]}.jar',
        f'-PpacketweaveJar={root}/.port-deps/PacketWeave/targets/{target}/build/libs/packetweave-{target}-{pins["packetweave"]["version"]}.jar',
        f'-PpacketweaveSource={root}/.port-deps/PacketWeave/core/src/main/java'],
        cwd=root / target, env=env, check=True)
    verify_jar(target)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('target')
    build(parser.parse_args().target)
