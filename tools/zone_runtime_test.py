"""Run opt-in hidden Minecraft acceptance against an optional instrumented baseline.

Uses isolated development profiles and localhost port 25586. The baseline must
contain the identical test fixture while retaining its original preview code.
"""
import argparse, csv, json, os, shutil, subprocess, threading, time
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

def run(baseline: Path|None,java_home: Path,order: list[str],samples: int,output: Path,graphics: list[Path],shaderpack: Path|None):
    output=output.resolve();output.mkdir(parents=True,exist_ok=True)
    if any(mode not in ('shader','particles','compatibility') for mode in order):raise ValueError('Unknown run backend')
    if 'particles' in order and baseline is None:raise ValueError('Particle runs require an unchanged baseline')
    environment=dict(os.environ);environment['JAVA_HOME']=str(java_home)
    environment['PATH']=str(java_home/'bin')+os.pathsep+environment['PATH']
    environment['ALSOFT_DRIVERS']='null'
    ready=threading.Event()
    def start(root,task,name,backend):
        wrapper=root/('gradlew.bat' if os.name=='nt' else 'gradlew')
        command=[str(wrapper),':fabric-1.20.1:'+task,'--no-daemon','--console=plain','--max-workers=2',
                 '-Dorg.gradle.jvmargs=-Xmx1024m','-PzonePreviewBackend='+backend,'-PzoneSamples='+str(samples)]
        if graphics and backend=='compatibility':command.append('-PzoneGraphicsMods='+os.pathsep.join(str(p.resolve()) for p in graphics))
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
        # A deterministic empty arena avoids changing passive-entity draw costs between runs.
        for command in ('gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','time set 6000','weather clear','kill @e[type=!minecraft:player]'):
            server.stdin.write(command+'\n')
        server.stdin.flush()
        (output/'protocol.json').write_text(json.dumps({'order':order,'samples_per_scene':samples,'warmup_ticks':100,
            'graphics_files':[str(p.name) for p in graphics],'shaderpack':shaderpack.name if shaderpack else None},indent=2)+'\n',encoding='utf-8')
        for index,backend in enumerate(order):
            root=baseline if backend=='particles' else ROOT
            directory=profile(root,False)
            tag=f'{index+1:02}-{backend}'
            for folder,pattern in [('screenshots',backend+'-*.png'),('zone-preview-evidence',backend+'-*.csv')]:
                for path in (directory/folder).glob(pattern):
                    if not path.resolve().is_relative_to(root.resolve()):raise RuntimeError('Evidence path escapes test checkout')
                    path.unlink()
            if shaderpack is not None and backend=='compatibility':
                packs=directory/'shaderpacks';packs.mkdir(exist_ok=True);shutil.copy2(shaderpack,packs/shaderpack.name)
                config=directory/'config';config.mkdir(exist_ok=True)
                (config/'iris.properties').write_text('enableShaders=true\nshaderPack='+shaderpack.name+'\nmaxShadowRenderDistance=8\n',encoding='utf-8')
            client,reader=start(root,'runZonePreviewClient',tag,backend)
            try:
                client.wait(timeout=600)
                reader.join(timeout=10)
                log=(output/(tag+'.log')).read_text(encoding='utf-8')
                if client.returncode!=0 or 'ZONE_PREVIEW_RUNTIME_PASS backend='+backend not in log:
                    raise RuntimeError(backend+' game acceptance failed; see '+str(output/(tag+'.log')))
                expected=7 if backend=='shader' else 1 if backend=='compatibility' else 6
                captures=list((directory/'screenshots').glob(backend+'-*.png'))
                raw=list((directory/'zone-preview-evidence').glob(backend+'-*.csv'))
                if len(captures)!=expected or len(raw)!=(0 if backend=='compatibility' else expected):raise RuntimeError(backend+' capture/sample count mismatch')
                if backend=='compatibility' and ('ZONE_PREVIEW_SHADERPACK_ACTIVE' not in log or 'automaticFallback=true forcedHudOnly=false' not in log):
                    raise RuntimeError('Shaderpack or automatic fallback was not verified')
                destination=output/tag;destination.mkdir(exist_ok=True)
                for path in captures+raw:shutil.copy2(path,destination/path.name)
                for path in raw:
                    rows=list(csv.DictReader(path.open()))
                    if len(rows)!=samples:raise RuntimeError('Wrong timing sample count: '+str(path))
                print(backend+': verified '+str(expected)+' game screenshots and raw timing CSVs',flush=True)
            finally:stop(client)
    finally:
        stop(server);server_reader.join(timeout=10)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--baseline',type=Path);parser.add_argument('--java-home',type=Path,required=True)
    parser.add_argument('--order',default='shader,particles');parser.add_argument('--samples',type=int,default=300)
    parser.add_argument('--output',type=Path,default=ROOT/'build/zone-runtime')
    parser.add_argument('--graphics-mod',type=Path,action='append',default=[]);parser.add_argument('--shaderpack',type=Path)
    args=parser.parse_args();run(args.baseline,args.java_home,args.order.split(','),args.samples,args.output,args.graphics_mod,args.shaderpack)
