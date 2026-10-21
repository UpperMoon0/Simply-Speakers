#!/usr/bin/env python3
"""Check production artifact contents, bytecode floor and external API isolation."""
import json,struct,sys,zipfile,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def verify(target):
    pins=json.loads((ROOT/'docs/requested-port-dependencies.json').read_text())
    if target not in pins['targets']:raise ValueError(target)
    version=re.search(r'^mod_version\s*=\s*(\S+)',(ROOT/'gradle.properties').read_text(),re.M)[1]
    path=ROOT/target/'build/libs'/f'simplyspeakers-{target}-{version}.jar'
    with zipfile.ZipFile(path) as archive:
        names=set(archive.namelist())
        for name in ['com/nstut/simplyspeakers/SpeakerState.class','com/nstut/simplyspeakers/client/ClientAudioPlayer.class','com/nstut/simplyspeakers/network/SpeakerPacketSecurity.class','com/nstut/simplyspeakers/audio/AudioFileManager.class','com/nstut/simplyspeakers/client/screens/SpeakerScreen.class']:
            if name not in names:raise ValueError(f'Missing runtime class {name}')
        if any(name.startswith(('com/nstut/packetweave/','com/nstut/openui/')) for name in names):raise ValueError('Dependency API accidentally embedded')
        maximum=69 if target.endswith('26.2') else 52
        for name in names:
            if name.endswith('.class') and not name.startswith('META-INF/'):
                major=struct.unpack('>H',archive.read(name)[6:8])[0]
                if major>maximum:raise ValueError(f'{name} requires class version {major}, expected <= {maximum}')
        if target=='forge-1.16.5' and 'simplyspeakers.refmap.json' not in names:raise ValueError('Missing production audio lifecycle mixin refmap')
    print(f'Validated production port: {path.name}')
if __name__=='__main__':verify(sys.argv[1])
