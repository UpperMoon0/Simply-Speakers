#!/usr/bin/env python3
"""Build pinned native PacketWeave dependencies and make them available to dev launches."""
import argparse
import os
from pathlib import Path
import subprocess
import re
ROOT = Path(__file__).resolve().parents[1]
TARGETS = ('fabric-1.20.1', 'forge-1.20.1', 'fabric-1.21.1', 'neoforge-1.21.1', 'neoforge-26.1.2')
def prepare(target):
    if target not in TARGETS: raise ValueError(target)
    native = ROOT / 'vendor/packetweave/targets' / target
    if not (native / 'build.gradle').is_file():
        raise RuntimeError('Initialize dependencies with git submodule update --init --recursive')
    wrapper = native / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    command = ([str(wrapper)] if os.name == 'nt' else ['bash', str(wrapper)])
    version = re.search(r'^mod_version\s*=\s*(\S+)', (ROOT / 'vendor/packetweave/gradle.properties').read_text(), re.M)[1]
    subprocess.run([*command, 'build', f'-Pmod_version={version}', '--no-daemon', '--max-workers=2'], cwd=native, check=True)
    jars = [p for p in (native / 'build/libs').glob('*.jar')
            if p.name == f'packetweave-{target}-{version}.jar']
    if len(jars) != 1: raise RuntimeError(f'Expected one native PacketWeave artifact: {jars}')
    print(f'PacketWeave {target}: {jars[0]}', flush=True)
    return jars[0]
if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', choices=TARGETS, action='append')
    args = parser.parse_args()
    for target in args.target or TARGETS: prepare(target)
