#!/usr/bin/env python3
"""Fail closed on incomplete publication bundles and native dependency relations."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import tomllib
import urllib.request
import os
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TARGETS = ('forge-1.12.2', 'fabric-1.16.5', 'forge-1.16.5',
           'fabric-1.20.1', 'forge-1.20.1', 'fabric-1.21.1', 'neoforge-1.21.1',
           'neoforge-26.1.2', 'fabric-26.2', 'neoforge-26.2')
BUILTINS = {'minecraft', 'java', 'fabricloader', 'forge', 'neoforge'}
SLUGS = {'architectury': 'architectury-api', 'fabric-api': 'fabric-api', 'fabric': 'fabric-api',
         'openui_mc': 'openui-mc', 'packetweave': 'packetweave', 'patchouli': 'patchouli'}


def version(root=ROOT):
    match = re.search(r'^mod_version\s*=\s*(\S+)', (root / 'gradle.properties').read_text(), re.M)
    if not match or not re.fullmatch(r'[0-9A-Za-z][0-9A-Za-z._+-]*', match[1]):
        raise ValueError('Invalid mod_version')
    return match[1]


def metadata_path(target, root=ROOT):
    if target not in TARGETS:
        raise ValueError(f'Unknown release target: {target}')
    base = root / target / 'src/main'
    if target.startswith('fabric-'):
        return base / 'resources/fabric.mod.json'
    if target == 'forge-1.12.2':
        return base / 'resources/mcmod.info'
    name = 'neoforge.mods.toml' if target.startswith('neoforge-') else 'mods.toml'
    paths = [base / folder / 'META-INF' / name for folder in ('resources', 'templates')]
    return next(path for path in paths if path.is_file())


def relations(target, root=ROOT):
    path = metadata_path(target, root)
    text = path.read_text()
    optional = set()
    if path.name == 'mcmod.info':
        required = set(json.loads(text)[0]['dependencies'])
    elif path.suffix == '.json':
        data = json.loads(text)
        required = set(data.get('depends', {})) - BUILTINS
        optional = set(data.get('suggests', {}))
    else:
        required = set()
        for block in re.split(r'\[\[dependencies\.[^\]]+\]\]', text)[1:]:
            match = re.search(r'modId\s*=\s*"([^"\n]+)"', block)
            if not match or match[1] in BUILTINS:
                continue
            (required if re.search(r'(?:mandatory\s*=\s*true|type\s*=\s*"required")', block)
             else optional).add(match[1])
    # OpenUI is client-only at loader level but required in a client modpack.
    required.add('openui_mc')
    if 'patchouli' in required:
        raise ValueError(f'Patchouli must remain optional: {target}')
    unknown = required - SLUGS.keys() - BUILTINS
    if unknown:
        raise ValueError(f'Unmapped native dependencies: {sorted(unknown)}')
    projects = [{'slug': SLUGS[key], 'type': 'requiredDependency'} for key in sorted(required)]
    if 'patchouli' in optional:
        projects.append({'slug': 'patchouli', 'type': 'optionalDependency'})
    return {'projects': projects}


def jar_name(target, release_version):
    if target not in TARGETS:
        raise ValueError(f'Unknown release target: {target}')
    return f'simplyspeakers-{target}-{release_version}.jar'


def verify_jar(path, target, release_version):
    if path.is_symlink() or path.name != jar_name(target, release_version):
        raise ValueError(f'Wrong or symlinked artifact: {path}')
    loader, minecraft = target.split('-', 1)
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise ValueError(f'Corrupt artifact: {path}')
        names = archive.namelist()
        if len(set(names)) != len(names):
            raise ValueError(f'Duplicate ZIP entries: {path}')
        if any(name.startswith(('com/nstut/openui/', 'com/nstut/packetweave/')) for name in names):
            raise ValueError(f'Bundled dependency classes: {path}')
        if 'com/nstut/simplyspeakers/SpeakerState.class' not in names:
            raise ValueError(f'Missing runtime: {path}')
        max_major = 52 if minecraft in ('1.12.2', '1.16.5') else 61 if minecraft == '1.20.1' else 65 if minecraft == '1.21.1' else 69
        for name in names:
            if name.endswith('.class') and not name.startswith('META-INF/'):
                data = archive.read(name)
                if len(data) < 8 or data[:4] != b'\xca\xfe\xba\xbe' or struct.unpack('>H', data[6:8])[0] > max_major:
                    raise ValueError(f'Invalid or incompatible bytecode: {name}')
        if loader == 'fabric':
            data = json.loads(archive.read('fabric.mod.json'))
            identity = data.get('id'), data.get('version')
            mc_constraint = str(data.get('depends', {}).get('minecraft', ''))
        elif target == 'forge-1.12.2':
            data, = json.loads(archive.read('mcmod.info'))
            identity = data.get('modid'), data.get('version')
            mc_constraint = str(data.get('mcversion', ''))
        else:
            name = 'META-INF/neoforge.mods.toml' if loader == 'neoforge' else 'META-INF/mods.toml'
            data = tomllib.loads(archive.read(name).decode())
            mod, = [mod for mod in data.get('mods', []) if mod.get('modId') == 'simplyspeakers']
            identity = mod['modId'], mod.get('version')
            deps = data.get('dependencies', {}).get('simplyspeakers', [])
            mc_constraint = str(next(dep['versionRange'] for dep in deps if dep.get('modId') == 'minecraft'))
        if identity != ('simplyspeakers', release_version):
            raise ValueError(f'Wrong native identity/version in {path}: {identity}')
        if minecraft not in mc_constraint:
            raise ValueError(f'Wrong Minecraft metadata in {path}: {mc_constraint}')


def bundle(directory, root=ROOT):
    release_version = version(root)
    expected = {jar_name(target, release_version): target for target in TARGETS}
    actual = {path.name: path for path in directory.iterdir() if path.suffix == '.jar'}
    if set(actual) != set(expected):
        raise ValueError(f'Incomplete/unexpected release artifacts: missing={sorted(set(expected)-set(actual))}, extra={sorted(set(actual)-set(expected))}')
    entries = []
    for name, target in sorted(expected.items()):
        path = actual[name]
        verify_jar(path, target, release_version)
        entries.append((name, hashlib.sha256(path.read_bytes()).hexdigest()))
    (directory / 'SHA256SUMS').write_text(''.join(f'{digest}  {name}\n' for name, digest in entries))
    return entries


def read_api(path):
    headers = {'Accept': 'application/vnd.github+json', 'User-Agent': 'SimplySpeakers-release-verifier'}
    if os.environ.get('GH_TOKEN'):
        headers['Authorization'] = 'Bearer ' + os.environ['GH_TOKEN']
    with urllib.request.urlopen(urllib.request.Request('https://api.github.com/repos/' + path, headers=headers), timeout=30) as response:
        return json.load(response)


def verify_dependency_releases(root=ROOT, fetch=read_api):
    pins = json.loads((root / 'docs/requested-port-dependencies.json').read_text())
    for key, prefix in (('openui', 'openui-mc'), ('packetweave', 'packetweave')):
        pin = pins[key]
        release = fetch(f'{pin["repository"]}/releases/tags/v{pin["version"]}')
        if release.get('draft') or release.get('prerelease') or not release.get('published_at'):
            raise ValueError(f'{key} dependency release is not published')
        assets = release.get('assets', [])
        for target in pins['targets']:
            name = f'{prefix}-{target}-{pin["version"]}.jar'
            matches = [a for a in assets if a.get('name') == name and a.get('size', 0) > 0 and a.get('state') == 'uploaded']
            if len(matches) != 1:
                raise ValueError(f'Missing/ambiguous published dependency: {name}')
        # A merge commit is fine only when its tree equals the reviewed source pin.
        tagged = fetch(f'{pin["repository"]}/commits/v{pin["version"]}')
        pinned = fetch(f'{pin["repository"]}/commits/{pin["commit"]}')
        if tagged['commit']['tree']['sha'] != pinned['commit']['tree']['sha']:
            raise ValueError(f'{key} released source differs from reviewed pin')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest='command', required=True)
    select = sub.add_parser('relations'); select.add_argument('target', choices=TARGETS)
    package = sub.add_parser('bundle'); package.add_argument('directory', type=Path)
    sub.add_parser('verify-dependencies')
    args = parser.parse_args()
    if args.command == 'relations': print(json.dumps(relations(args.target), separators=(',', ':')))
    elif args.command == 'bundle': bundle(args.directory)
    else: verify_dependency_releases()
