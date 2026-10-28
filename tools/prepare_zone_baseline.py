"""Prepare an isolated original-particle worktree with identical opt-in test hooks.

Never replaces DirectionalPreview or its geometry. Requires the pinned Fabric
PacketWeave jar to have been built in the source checkout first.
"""
import argparse,json,shutil,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def git(root,*args):return subprocess.check_output(['git',*args],cwd=root,text=True).strip()

def prepare(output: Path,ref: str,refresh: bool):
    output=output.resolve()
    if output==ROOT.resolve():raise ValueError('Baseline must be a separate worktree')
    vendor=ROOT/'vendor/packetweave'
    jar=Path('targets/fabric-1.20.1/build/libs/packetweave-fabric-1.20.1-0.1.2.jar')
    if not (vendor/jar).is_file():raise RuntimeError('Run tools/prepare_packetweave.py --target fabric-1.20.1 first')
    commit=git(ROOT,'rev-parse','--verify',ref+'^{commit}')
    if output.exists():
        if not refresh:raise RuntimeError('Existing output requires explicit --refresh-fixture')
        if git(output,'rev-parse','HEAD')!=commit:raise RuntimeError('Existing baseline is at a different commit')
    else:subprocess.run(['git','worktree','add','--detach',str(output),commit],cwd=ROOT,check=True)
    fixture=Path('shared/zone-verification/src/main/java')
    names=['com/nstut/simplyspeakers/client/ZonePreviewRuntimeProbe.java',
           'com/nstut/mixin/ZonePreviewFrameProbeMixin.java','com/nstut/mixin/ZonePreviewOffscreenMixin.java',
           'com/nstut/mixin/ZonePreviewParticleTimingMixin.java']
    for name in names:
        source=ROOT/fixture/name
        old=output/'common-1.20.1/src/main/java'/name
        # Earlier local fixture revisions lived in the maintained source root.
        if old.exists():
            if not old.resolve().is_relative_to(output):raise RuntimeError('Fixture path escapes baseline')
            if old.read_text(encoding='utf-8')!=source.read_text(encoding='utf-8'):raise RuntimeError('Old fixture has separate edits: '+str(old))
            old.unlink()
        target=output/fixture/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
    path=output/'common-1.20.1/build.gradle'
    text=path.read_text(encoding='utf-8');line="sourceSets.main.java.srcDir('../shared/zone-verification/src/main/java')"
    if line not in text:path.write_text(text+'\n'+line+'\n',encoding='utf-8')
    path=output/'common-1.20.1/src/main/java/com/nstut/simplyspeakers/client/ClientEvents.java'
    text=path.read_text(encoding='utf-8');anchor='        SpeakerUiPreviewProbe.tick(client);'
    if anchor not in text:raise RuntimeError('Baseline tick anchor changed')
    if 'ZonePreviewRuntimeProbe.tick' not in text:path.write_text(text.replace(anchor,anchor+'\n        ZonePreviewRuntimeProbe.tick(client);'),encoding='utf-8')
    path=output/'common-1.20.1/src/main/resources/simplyspeakers.mixins.json';data=json.loads(path.read_text())
    for name in ['ZonePreviewFrameProbeMixin','ZonePreviewOffscreenMixin','ZonePreviewParticleTimingMixin']:
        if name not in data['client']:data['client'].append(name)
    path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
    shutil.copy2(ROOT/'fabric-1.20.1/build.gradle',output/'fabric-1.20.1/build.gradle')
    for name in git(vendor,'ls-files','-z').split('\0'):
        if not name:continue
        target=output/'vendor/packetweave'/name
        if not target.resolve().is_relative_to(output):raise RuntimeError('Dependency path escapes baseline')
        target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(vendor/name,target)
    target=output/'vendor/packetweave'/jar;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(vendor/jar,target)
    for name in ['common-1.20.1/src/main/java/com/nstut/simplyspeakers/client/DirectionalPreview.java',
                 'common/src/main/java/com/nstut/simplyspeakers/audio/DirectionalPreviewGeometry.java']:
        expected=subprocess.check_output(['git','show',commit+':'+name],cwd=ROOT).replace(b'\r\n',b'\n')
        if (output/name).read_bytes().replace(b'\r\n',b'\n')!=expected:raise RuntimeError('Baseline preview differs: '+name)
    print('Baseline ready: '+commit+'; preview and geometry are unchanged')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--ref',default='fc75a98');parser.add_argument('--refresh-fixture',action='store_true')
    args=parser.parse_args();prepare(args.output,args.ref,args.refresh_fixture)
