#!/usr/bin/env python3
"""Real standalone-port server/two-client acceptance; never a unit-test surrogate.

Requires native OpenAL source/context evidence, four sound-engine reload cases,
spatial/portable playback, real upload packets and UI ownership interactions.
ALSOFT_DRIVERS=null deliberately does not prove physical audible output.
"""
from __future__ import annotations
import argparse
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

def required_markers(target):
    client,server=ORIGINAL_MARKERS(target)
    return (tuple(marker for marker in client if marker!='SIMPLYSPEAKERS_GUIDE_PASS')+
            ('SIMPLYSPEAKERS_OPTIONAL_GUIDE_NOT_INCLUDED',f'SIMPLYSPEAKERS_RUNTIME_JVM role=client major={8 if target.endswith("1.16.5") else 25}'),
            tuple(marker for marker in server if marker!='SIMPLYSPEAKERS_PERIPHERAL_PASS')+
            (f'SIMPLYSPEAKERS_RUNTIME_JVM role=server major={8 if target.endswith("1.16.5") else 25}',))

def dependency_arguments(root,target):
    checkout=root/'.port-deps'
    openui=checkout/'OpenUI-MC' if checkout.is_dir() else root.parent/'OpenUI-MC'
    packetweave=checkout/'PacketWeave' if checkout.is_dir() else root.parent/'PacketWeave'
    return [f'-PopenuiJar={openui/target/"build/libs"/f"openui-mc-{target}-0.0.15.jar"}',
            f'-PpacketweaveJar={packetweave/"targets"/target/"build/libs"/f"packetweave-{target}-0.1.4.jar"}',
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
    with checkout_lock(ROOT,'requested-live-join.lock'):
        live.run_target(ROOT,args.target,args.timeout)
    return 0

if __name__=='__main__':
    try:raise SystemExit(main())
    except (RuntimeError,subprocess.CalledProcessError,subprocess.TimeoutExpired) as error:
        print(f'REQUESTED PORT LIVE ACCEPTANCE FAILED: {error}',file=sys.stderr);raise SystemExit(1)
