#!/usr/bin/env python3
"""Bounded Forge 1.12.2 DEVELOPMENT launch acceptance with real server/client evidence.

Uses genuine source-pinned dependency jars and the native Gradle run tasks.
ALSOFT_DRIVERS=null tests actual context/source lifecycle, not physical audibility.
A production/reobfuscated jar launch is a separate, unimplemented acceptance lane.
This harness never accepts a new Minecraft EULA on the user's behalf.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import live_join_test as live
from verify import checkout_lock

ROOT = Path(__file__).resolve().parents[1]
PHASES = ('started', 'paused', 'resumed', 'stopped')
CLIENT_MARKERS = (
    'SIMPLYSPEAKERS_RUNTIME_JVM role=client major=8 ',
    'SIMPLYSPEAKERS_1122_CLIENT_JOIN_PASS',
    'SIMPLYSPEAKERS_1122_UI_PASS focus=true text=true clipboardRoundtrip=true tab=true navigation=true resize=true close=true',
    'SIMPLYSPEAKERS_1122_AUDIO_RELOAD_PASS paused ',
    'SIMPLYSPEAKERS_1122_AUDIO_RELOAD_PASS playing ',
    'SIMPLYSPEAKERS_1122_AUDIO_UNLOAD_PASS ',
    *(f'SIMPLYSPEAKERS_1122_CLIENT_PHASE_PASS {phase} ' for phase in PHASES),
    'SIMPLYSPEAKERS_1122_CLIENT_DISCONNECT_PASS',
    'SIMPLYSPEAKERS_1122_CLIENT_ACCEPTANCE_PASS backendLifecycleOnly=true',
)
SERVER_MARKERS = (
    'SIMPLYSPEAKERS_RUNTIME_JVM role=server major=8 ',
    'SIMPLYSPEAKERS_1122_SERVER_DISCOVERY_PASS clientClassesRequired=false',
    'SIMPLYSPEAKERS_1122_SERVER_JOIN_PASS player=SS1122Client',
    *(f'SIMPLYSPEAKERS_1122_SERVER_PHASE_PASS {phase}' for phase in PHASES),
    'SIMPLYSPEAKERS_1122_SERVER_DISCONNECT_PASS',
    'SIMPLYSPEAKERS_1122_SERVER_ACCEPTANCE_PASS',
)

def prepare(module: Path) -> None:
    run = module / 'run/legacy-runtime'
    server, client = run / 'server', run / 'client'
    server.mkdir(parents=True, exist_ok=True); client.mkdir(parents=True, exist_ok=True)
    eula = server / 'eula.txt'
    if not eula.is_file() or not any(line.strip().lower() == 'eula=true' for line in eula.read_text().splitlines()):
        raise RuntimeError(f'Minecraft EULA must already be accepted in {eula}; this harness will not accept it')
    # A fresh uniquely named fixture world avoids stale emitter/catalog evidence.
    (server / 'server.properties').write_text(
        'online-mode=false\nserver-ip=127.0.0.1\nserver-port=25576\n'
        f'level-name=acceptance-{time.time_ns()}\n'
        'level-type=flat\ngenerator-settings=3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;\n'
        'difficulty=0\nspawn-protection=0\nspawn-monsters=false\nspawn-animals=false\nview-distance=3\n'
        'max-players=1\nmotd=Simply Speakers native acceptance\n', encoding='utf-8')
    (client / 'options.txt').write_text(
        'pauseOnLostFocus:false\nrenderDistance:3\nmaxFps:30\nguiScale:2\n'
        'soundCategory_master:1.0\nsoundCategory_record:1.0\n', encoding='utf-8')

def command(root: Path, task: str, openui: Path, packetweave: Path) -> list[str]:
    if task not in ('classes', 'runLegacyRuntimeServer', 'runLegacyRuntimeClient'):
        raise ValueError('Unsupported native acceptance task: ' + task)
    module = root / 'forge-1.12.2'
    return ['bash', str(module / 'gradlew'), '--project-dir', str(module), task,
            '--no-daemon', '--no-parallel', '--console=plain', '--max-workers=2',
            '-Dorg.gradle.jvmargs=-Xmx256m -XX:ActiveProcessorCount=2',
            '-PopenuiJar=' + str(openui.resolve()), '-PpacketweaveJar=' + str(packetweave.resolve())]

def client_command(root: Path, openui: Path, packetweave: Path) -> list[str]:
    # LWJGL 2.9.4's XRandR.populate invokes the real `xrandr -q` executable.
    # An absent executable or parser failure clears its screen names, which
    # LinuxDisplay.getAvailableDisplayModes then indexes without a length guard.
    # Disabling XRandR is not a safe substitute: Xvfb need not offer XF86VidMode.
    if not shutil.which('xrandr'):
        raise RuntimeError('LWJGL 2 requires the xrandr executable (Ubuntu package x11-xserver-utils)')
    prefix = []
    if not os.environ.get('DISPLAY'):
        xvfb = shutil.which('xvfb-run')
        if not xvfb: raise RuntimeError('DISPLAY is unset and xvfb-run is unavailable')
        prefix = [xvfb, '-a']
    # Query inside the SAME owned display session, not a separate short-lived
    # Xvfb. A failed query cannot launch the game. exec preserves argv (including
    # paths containing spaces) and shared process-group cleanup supervises both.
    return prefix + ['sh', '-c', 'set -eu; xrandr --query; exec "$@"',
                     'simplyspeakers-1122-display',
                     *command(root, 'runLegacyRuntimeClient', openui, packetweave)]

def validate_log(text: str, required: tuple[str, ...]) -> None:
    for line in text.splitlines(): live.reject_failure('native1122', line)
    missing = [marker for marker in required if marker not in text]
    if missing: raise RuntimeError('Missing native game evidence: ' + ', '.join(missing))

def artifact(path: Path) -> dict[str, str]:
    if not path.is_file(): raise RuntimeError('Missing genuine dependency artifact: ' + str(path))
    return {'path': str(path.resolve()), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}

def run_preparation(cmd: list[str], cwd: Path, timeout: float, label: str) -> None:
    """Bound each prerequisite graph and always retire its entire owned session."""
    if os.name == 'nt':
        raise RuntimeError('Native 1.12 acceptance preparation requires POSIX process sessions')
    process = live.popen(cmd, cwd)
    pump = None
    try:
        pump = live.OutputPump(process, label)
        result = process.wait(timeout=timeout)
        if result:
            raise subprocess.CalledProcessError(result, cmd, output=''.join(pump.history))
    finally:
        try:
            live.stop_tree(process)
        finally:
            if pump is not None:
                pump.thread.join(timeout=5)
            if process.stdin is not None:
                process.stdin.close()
            if process.stdout is not None and (pump is None or not pump.thread.is_alive()):
                process.stdout.close()


def run(args) -> None:
    module = ROOT / 'forge-1.12.2'
    dependencies = {'openui': artifact(args.openui_jar), 'packetweave': artifact(args.packetweave_jar)}
    if not os.environ.get('JAVA_HOME'): raise RuntimeError('Set JAVA_HOME to the genuine Java 8 game runtime')
    java_version = subprocess.check_output([str(Path(os.environ['JAVA_HOME']) / 'bin/java'), '-version'], stderr=subprocess.STDOUT, text=True)
    if 'version "1.8.' not in java_version: raise RuntimeError('Native Forge 1.12 game runtime must be Java 8')
    client_launch = client_command(ROOT, args.openui_jar, args.packetweave_jar)
    prepare(module)
    report_dir = module / 'run/legacy-runtime/receipts'; report_dir.mkdir(parents=True, exist_ok=True)
    receipt = {'mode': 'development-classes', 'physical_audibility_tested': False, 'dependencies': dependencies,
               'source_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
               'source_dirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip()),
               'result': 'not-completed'}
    server = client = None
    try:
        run_preparation(command(ROOT, 'classes', args.openui_jar, args.packetweave_jar), ROOT, args.timeout, 'prepare-classes')
        # Resolve both task graphs serially; no concurrent compilation/remapping.
        for task in ('runLegacyRuntimeServer', 'runLegacyRuntimeClient'):
            run_preparation(command(ROOT, task, args.openui_jar, args.packetweave_jar) +
                            ['--init-script', str(ROOT / 'tools/prepare_requested_1122_runtime.gradle')], ROOT, args.timeout, 'prepare-' + task)
        server = live.OutputPump(live.popen(command(ROOT, 'runLegacyRuntimeServer', args.openui_jar, args.packetweave_jar), ROOT), 'server1122')
        if server.wait_for(live.SERVER_READY_MARKERS, args.timeout) is None: raise RuntimeError('Native server did not become ready')
        client = live.OutputPump(live.popen(client_launch, ROOT), 'client1122')
        client.wait_for_all(CLIENT_MARKERS, args.timeout, peer=server)
        # The client deliberately exits after disconnect; do not treat that as a peer failure.
        server.wait_for_all(SERVER_MARKERS, 60)
        validate_log(''.join(client.history), CLIENT_MARKERS); validate_log(''.join(server.history), SERVER_MARKERS)
        receipt['result'] = 'passed'
        print('SIMPLYSPEAKERS_1122_DEVELOPMENT_ACCEPTANCE_PASS physicalAudibility=false')
    finally:
        for name, process in (('client', client), ('server', server)):
            if process:
                live.stop_tree(process.process, graceful_server=name == 'server')
                (report_dir / (name + '.log')).write_text(''.join(process.history), encoding='utf-8')
        (report_dir / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--openui-jar', type=Path, required=True)
    parser.add_argument('--packetweave-jar', type=Path, required=True)
    parser.add_argument('--timeout', type=int, default=600)
    args = parser.parse_args()
    with checkout_lock(ROOT, 'requested1122-runtime.lock'): run(args)
    return 0

if __name__ == '__main__':
    try: raise SystemExit(main())
    except (RuntimeError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
        print('NATIVE1122 ACCEPTANCE FAILED: ' + str(error), file=sys.stderr); raise SystemExit(1)
