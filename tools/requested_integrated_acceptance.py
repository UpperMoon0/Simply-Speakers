#!/usr/bin/env python3
"""Separate native Singleplayer-menu acceptance, NOT dedicated/two-client qualification."""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import live_join_test as live
from requested_live_join import dependency_arguments
from requested_1122_runtime import run_preparation
from verify import checkout_lock

ROOT=Path(__file__).resolve().parents[1]
TARGETS=('fabric-1.16.5','forge-1.16.5')
MARKERS=(
    'SIMPLYSPEAKERS_INTEGRATED_MENU_PASS enabled=true path=singleplayer-create',
    'SIMPLYSPEAKERS_INTEGRATED_WORLD_PASS server=',
    'SIMPLYSPEAKERS_INTEGRATED_RUNTIME_PASS javaMajor=8',
    'SIMPLYSPEAKERS_INTEGRATED_AUDIO_RELOAD_PASS playing',
    'SIMPLYSPEAKERS_INTEGRATED_AUDIO_RELOAD_PASS paused',
    'SIMPLYSPEAKERS_INTEGRATED_UI_PASS tabs=4 nativePointer=true nativeKeyboard=true productionSettingsRoundTrip=true resize=true closeReopen=true',
    'SIMPLYSPEAKERS_INTEGRATED_SERVER_PASS phases=7 participants=1 productionC2S=true productionS2CObservedByClient=true dedicated=false twoClient=false',
    'SIMPLYSPEAKERS_INTEGRATED_CLIENT_PASS phases=7 participants=1 productionTransport=true nativeOpenUI=true backendOnly=true audibleOutputVerified=false dedicated=false twoClient=false',
)
ORIGINAL_REJECT_FAILURE=live.reject_failure

def reject_failure(prefix,line):
    ORIGINAL_REJECT_FAILURE(prefix,line)
    if 'SIMPLYSPEAKERS_INTEGRATED_FAIL' in line: raise RuntimeError(prefix+': '+line.strip())

def allowed_boundary(data,target,commit):
    return (isinstance(data,dict) and target in TARGETS and data.get('target')==target and
            data.get('source_commit')==commit and data.get('mode')=='dedicated-two-client' and
            data.get('result')=='blocked-native-multiplayer' and data.get('runtime_qualified') is False and
            data.get('native_multiplayer_allowed') is False)

def boundary(root,target,commit):
    try: data=json.loads((root/target/'build/runtime-evidence/dedicated-qualification.json').read_text())
    except (OSError,ValueError): return False
    return allowed_boundary(data,target,commit)

def command(root,target,task,directory):
    if target not in TARGETS or task not in ('classes','runIntegratedAcceptance'): raise ValueError('Unsupported integrated target/task')
    base=(root/target/'run/integrated-acceptance').resolve(); directory=directory.resolve()
    if directory==base or base not in directory.parents: raise ValueError('Integrated directory escaped its isolated parent')
    return ['bash',str(root/target/'gradlew'),'--project-dir',str(root/target),task,
            '--no-daemon','--no-parallel','--console=plain','--max-workers=2',
            '-Dorg.gradle.jvmargs=-Xmx512m -XX:ActiveProcessorCount=2',
            '-PintegratedRunDir='+str(directory),*dependency_arguments(root,target)]

def client_command(cmd):
    if os.environ.get('DISPLAY'): return cmd
    xvfb=shutil.which('xvfb-run')
    if not xvfb: raise RuntimeError('No display available for ordinary Singleplayer menu')
    return [xvfb,'-a',*cmd]

def run(root,target,timeout,commit):
    if not boundary(root,target,commit): raise RuntimeError('No current exact native-ineligibility receipt; integrated alternative not entered')
    directory=root/target/'run/integrated-acceptance'/('run-'+str(time.time_ns()))
    directory.mkdir(parents=True,exist_ok=False)
    (directory/'options.txt').write_text('pauseOnLostFocus:false\nrenderDistance:3\nmaxFps:30\nguiScale:2\nsoundCategory_master:1.0\nsoundCategory_record:1.0\n')
    receipt={'target':target,'source_commit':commit,'mode':'integrated-singleplayer-development',
             'dedicated_qualified':False,'two_client_qualified':False,'physical_audibility_tested':False,'result':'not-completed'}
    output=None
    verified=False
    previous=live.reject_failure
    live.reject_failure=reject_failure
    try:
        run_preparation(command(root,target,'classes',directory),root,timeout,'integrated-prepare-classes')
        run_preparation(command(root,target,'runIntegratedAcceptance',directory)+['--init-script',str(root/'tools/prepare_integrated_acceptance.gradle')],root,timeout,'integrated-prepare-runtime')
        output=live.OutputPump(live.popen(client_command(command(root,target,'runIntegratedAcceptance',directory)),root),target+'/integrated')
        output.wait_for_all(MARKERS,timeout)
        if output.process.poll() is not None: raise RuntimeError('Integrated game exited before completion was verified')
        verified=True
    finally:
        cleanup_ok=False
        try:
            if output is not None:
                live.stop_tree(output.process)
                output.thread.join(timeout=5)
                (directory/'integrated.log').write_text(''.join(output.history))
                if output.thread.is_alive(): raise RuntimeError('Integrated output reader did not drain after process cleanup')
            cleanup_ok=True
        finally:
            live.reject_failure=previous
            receipt['result']='passed' if verified and cleanup_ok else 'not-completed'
            (directory/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
    print('SIMPLYSPEAKERS_INTEGRATED_ACCEPTANCE_PASS dedicated=false twoClient=false physicalAudibility=false')

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target',choices=TARGETS,required=True)
    parser.add_argument('--timeout',type=int,default=600)
    parser.add_argument('--check-boundary',action='store_true')
    parser.add_argument('--github-output',type=Path)
    args=parser.parse_args(argv)
    commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    if args.check_boundary:
        allowed=boundary(ROOT,args.target,commit)
        print('Separate integrated alternative: '+('permitted by observed native refusal' if allowed else 'not entered; no exact native-refusal receipt'))
        if args.github_output:
            with args.github_output.open('a') as output: output.write('accepted='+str(allowed).lower()+'\n')
        return 0
    with checkout_lock(ROOT,'requested-integrated.lock'): run(ROOT,args.target,args.timeout,commit)
    return 0

if __name__=='__main__':
    try: raise SystemExit(main())
    except (RuntimeError,subprocess.CalledProcessError,subprocess.TimeoutExpired) as error:
        print('INTEGRATED ACCEPTANCE FAILED: '+str(error),file=sys.stderr);raise SystemExit(1)
