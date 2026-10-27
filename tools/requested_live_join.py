#!/usr/bin/env python3
"""Real standalone-port server/two-client acceptance; never a unit-test surrogate.

Requires native OpenAL source/context evidence, four sound-engine reload cases,
spatial/portable playback, real upload packets and UI ownership interactions.
ALSOFT_DRIVERS=null deliberately does not prove physical audible output.
"""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import live_join_test as live
from verify import checkout_lock
TARGETS=('fabric-1.16.5','forge-1.16.5','fabric-26.2','neoforge-26.2')
ROOT=Path(__file__).resolve().parents[1]
ORIGINAL_MARKERS=live.required_markers
ORIGINAL_PREPARE_SERVER=live.prepare_server
ORIGINAL_REJECT_FAILURE=live.reject_failure

def required_markers(target):
    client,server=ORIGINAL_MARKERS(target)
    return (tuple(marker for marker in client if marker!='SIMPLYSPEAKERS_GUIDE_PASS')+
            ('SIMPLYSPEAKERS_OPTIONAL_GUIDE_NOT_INCLUDED',f'SIMPLYSPEAKERS_RUNTIME_JVM role=client major={8 if target.endswith("1.16.5") else 25}'),
            tuple(marker for marker in server if marker!='SIMPLYSPEAKERS_PERIPHERAL_PASS')+
            (f'SIMPLYSPEAKERS_RUNTIME_JVM role=server major={8 if target.endswith("1.16.5") else 25}',))

class NativeEligibilityUnavailable(RuntimeError):
    """Observed native refusal; never a successful dedicated qualification."""

def reject_failure(prefix, line):
    ORIGINAL_REJECT_FAILURE(prefix, line)
    # Read-only native diagnostics prove startup did not enter the requested
    # connection. Report the boundary instead of waiting 900 seconds or forcing
    # a connection past Minecraft's own eligibility check. This is never success.
    labels = {target + '/' + role for target in ('fabric-1.16.5', 'forge-1.16.5')
              for role in ('carrier', 'observer')}
    titles = ('net.minecraft.client.gui.screens.TitleScreen',
              'net.minecraft.client.gui.screen.MainMenuScreen')
    if prefix in labels and any(line.rstrip().endswith(
            'SIMPLYSPEAKERS_PREJOIN_WAIT screen=' + title +
            ', overlay=none, multiplayerAllowed=false') for title in titles):
        raise NativeEligibilityUnavailable(prefix + ': native Minecraft multiplayer eligibility is false after loading; '
                           'no join acceptance is claimed and no connection override is attempted')

def dependency_arguments(root,target):
    checkout=root/'.port-deps'
    openui=checkout/'OpenUI-MC' if checkout.is_dir() else root.parent/'OpenUI-MC'
    packetweave=checkout/'PacketWeave' if checkout.is_dir() else root.parent/'PacketWeave'
    return [f'-PopenuiJar={openui/target/"build/libs"/f"openui-mc-{target}-0.0.15.jar"}',
            f'-PpacketweaveJar={packetweave/"targets"/target/"build/libs"/f"packetweave-{target}-0.1.5.jar"}',
            f'-PpacketweaveSource={packetweave/"core/src/main/java"}']

def command(root,task):
    parts=task.strip(':').split(':')
    if len(parts)!=2 or parts[0] not in TARGETS:raise ValueError(f'Not a standalone port task: {task}')
    target,name=parts;module=root/target
    return ['bash',str(module/'gradlew'),'--project-dir',str(module),name,
            '--no-daemon','--no-parallel','--console=plain','--max-workers=2',
            '-Dorg.gradle.jvmargs=-Xmx768m -XX:ActiveProcessorCount=2',*dependency_arguments(root,target)]

def prepare_server(module):
    ORIGINAL_PREPARE_SERVER(module)
    properties=module/'run/live-join/server/server.properties'
    properties.write_text(properties.read_text()+'server-ip=127.0.0.1\n',encoding='utf-8')
    if module.name.endswith('1.16.5'):
        path=module/'run/live-join/server/server.properties'
        path.write_text(path.read_text().replace('level-type=minecraft:flat','level-type=flat'),encoding='utf-8')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target',choices=TARGETS,required=True)
    parser.add_argument('--timeout',type=int,default=900)
    args=parser.parse_args()
    # Reuse the maintained fail-closed process supervision and per-role markers.
    # Only module invocation and two explicitly unsupported OPTIONAL integrations differ.
    live.TARGETS={target:target for target in TARGETS}
    live.command=command;live.required_markers=required_markers;live.prepare_server=prepare_server
    live.reject_failure=reject_failure
    with checkout_lock(ROOT,'requested-live-join.lock'):
        receipt_path=ROOT/args.target/'build/runtime-evidence/dedicated-qualification.json'
        receipt_path.parent.mkdir(parents=True,exist_ok=True)
        receipt={'target':args.target,'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
                 'mode':'dedicated-two-client','result':'not-completed','runtime_qualified':False}
        receipt_path.write_text(json.dumps(receipt,indent=2)+'\n')
        try:
            live.run_target(ROOT,args.target,args.timeout)
            receipt.update(result='passed',runtime_qualified=True)
        except NativeEligibilityUnavailable:
            receipt.update(result='blocked-native-multiplayer',native_multiplayer_allowed=False)
            raise
        finally:
            receipt_path.write_text(json.dumps(receipt,indent=2)+'\n')
    return 0

if __name__=='__main__':
    try:raise SystemExit(main())
    except (RuntimeError,subprocess.CalledProcessError,subprocess.TimeoutExpired) as error:
        print(f'REQUESTED PORT LIVE ACCEPTANCE FAILED: {error}',file=sys.stderr);raise SystemExit(1)
