"""Run opt-in hidden Minecraft acceptance against an optional instrumented baseline.

Uses isolated development profiles and localhost port 25586. The baseline must
contain the identical test fixture while retaining its original preview code.
"""
import argparse, os, subprocess, threading, time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def profile(root: Path, server: bool):
    directory=root/'fabric-1.20.1/run/zone-preview'/('server' if server else 'client')
    directory.mkdir(parents=True,exist_ok=True)
    if server:
        (directory/'eula.txt').write_text('eula=true\n',encoding='utf-8')
        (directory/'server.properties').write_text('''server-ip=127.0.0.1
server-port=25586
online-mode=false
enforce-secure-profile=false
level-type=minecraft:flat
level-seed=24024
view-distance=8
simulation-distance=4
gamemode=creative
difficulty=peaceful
spawn-protection=0
max-tick-time=0
''',encoding='utf-8')
    else:
        (directory/'options.txt').write_text('''narrator:0
narratorHotkey:false
onboardAccessibility:false
pauseOnLostFocus:false
enableVsync:false
maxFps:260
renderDistance:8
simulationDistance:5
fullscreen:false
clouds:false
''',encoding='utf-8')
    return directory

def stop(process):
    if process.poll() is not None:return
    if process.stdin:
        try: process.stdin.write('stop\n');process.stdin.flush();process.wait(timeout=30);return
        except (OSError,subprocess.TimeoutExpired):pass
    if os.name=='nt':subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],capture_output=True)
    else:process.terminate()
    process.wait(timeout=30)

def run(baseline: Path|None,java_home: Path):
    output=ROOT/'build/zone-runtime';output.mkdir(parents=True,exist_ok=True)
    environment=dict(os.environ);environment['JAVA_HOME']=str(java_home)
    environment['PATH']=str(java_home/'bin')+os.pathsep+environment['PATH']
    environment['ALSOFT_DRIVERS']='null'
    ready=threading.Event()
    def start(root,task,name,backend):
        wrapper=root/('gradlew.bat' if os.name=='nt' else 'gradlew')
        command=[str(wrapper),':fabric-1.20.1:'+task,'--no-daemon','--console=plain','--max-workers=2',
                 '-Dorg.gradle.jvmargs=-Xmx1024m','-PzonePreviewBackend='+backend]
        flags=(subprocess.CREATE_NEW_PROCESS_GROUP|subprocess.CREATE_NO_WINDOW) if os.name=='nt' else 0
        process=subprocess.Popen(command,cwd=root,env=environment,stdin=subprocess.PIPE,stdout=subprocess.PIPE,
                                 stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',creationflags=flags)
        def collect():
            with (output/(name+'.log')).open('w',encoding='utf-8') as log:
                for line in process.stdout:
                    log.write(line);log.flush()
                    if 'Done (' in line:ready.set()
                    if 'ZONE_PREVIEW_' in line:print(name+': '+line.rstrip(),flush=True)
        thread=threading.Thread(target=collect,daemon=True);thread.start()
        return process,thread
    profile(ROOT,True)
    server,server_reader=start(ROOT,'runZonePreviewServer','server','shader')
    try:
        deadline=time.monotonic()+300
        while not ready.wait(1):
            if server.poll() is not None:raise RuntimeError('Fixture server exited; see '+str(output/'server.log'))
            if time.monotonic()>deadline:raise RuntimeError('Fixture server readiness timed out')
        print('Fixture server ready on localhost:25586',flush=True)
        for root,backend in [(ROOT,'shader')]+([(baseline,'particles')] if baseline else []):
            directory=profile(root,False)
            client,reader=start(root,'runZonePreviewClient',backend,backend)
            try:
                client.wait(timeout=600)
                reader.join(timeout=10)
                log=(output/(backend+'.log')).read_text(encoding='utf-8')
                if client.returncode!=0 or 'ZONE_PREVIEW_RUNTIME_PASS backend='+backend not in log:
                    raise RuntimeError(backend+' game acceptance failed; see '+str(output/(backend+'.log')))
                expected=7 if backend=='shader' else 6
                captures=list((directory/'screenshots').glob(backend+'-*.png'))
                raw=list((directory/'zone-preview-evidence').glob(backend+'-*.csv'))
                if len(captures)!=expected or len(raw)!=expected:raise RuntimeError(backend+' capture/sample count mismatch')
                print(backend+': verified '+str(expected)+' game screenshots and raw timing CSVs',flush=True)
            finally:stop(client)
    finally:
        stop(server);server_reader.join(timeout=10)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--baseline',type=Path);parser.add_argument('--java-home',type=Path,required=True)
    args=parser.parse_args();run(args.baseline,args.java_home)
