#!/usr/bin/env python3
"""Translate the tested Mojang-named 1.16.5 adapter to Forge's MCP class identities.

Forge's official member mappings preserve Mojang method/field names but retain
MCP class/package identities. Mapping entries are API names, not game sources.
"""
import argparse,json,re
from pathlib import Path
import legacy_speaker_sources
ROOT=Path(__file__).resolve().parents[1]
TYPES=json.loads((ROOT/'tools/forge_1165_types.json').read_text())

def convert(text):
    aliases={}
    def imported(match):
        typ=match[1]
        if typ.endswith('.*'):
            package=typ[:-2]
            names=[a for a in TYPES if a.rsplit('.',1)[0]==package and re.search(r'\b'+re.escape(a.rsplit('.',1)[1])+r'\b',text)]
            if names:
                for a in names:aliases[a.rsplit('.',1)[1]]=TYPES[a].rsplit('.',1)[1]
                return '\n'.join('import '+TYPES[a]+';' for a in names)
        elif typ in TYPES:
            aliases[typ.rsplit('.',1)[1]]=TYPES[typ].rsplit('.',1)[1]
            return 'import '+TYPES[typ]+';'
        return match[0]
    text=re.sub(r'import ([\w.*]+);',imported,text)
    for old,new in sorted(TYPES.items(),key=lambda p:-len(p[0])):
        text=re.sub(r'\b'+re.escape(old)+r'\b',new,text)
    for old,new in aliases.items():
        if old!=new:text=re.sub(r'(?<![\w.])'+re.escape(old)+r'\b',new,text)
    return text

def generate(output, tests=False):
    intermediate=output.parent/(output.name+'-mojmap-sources')
    if tests:legacy_speaker_sources.generate_tests(intermediate, fabric=False)
    else:legacy_speaker_sources.generate(intermediate, fabric=False)
    generated=set()
    for source in ([intermediate] if tests else [intermediate,ROOT/'forge-1.16.5/src/main/java']):
        for path in source.rglob('*.java'):
            target=output/path.relative_to(source);target.parent.mkdir(parents=True,exist_ok=True)
            text=convert(path.read_text(encoding='utf-8'))
            if path.name=='LegacyBlockEntity.java':
                text=text.rsplit('}',1)[0]+'''    // Exact IForgeTileEntity callback signature; the vanilla compile reference lacks this Forge extension.
    public void onDataPacket(net.minecraft.network.NetworkManager network, net.minecraft.network.play.server.SUpdateTileEntityPacket packet) {
        if (packet.getType() == 0 && getBlockPos().equals(packet.getPos())) applyClientUpdate(packet.getTag());
    }
}
'''
            target.write_text(text,encoding='utf-8');generated.add(target)
    for path in output.rglob('*.java'):
        if path not in generated:path.unlink()
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--output',required=True,type=Path);parser.add_argument('--tests',action='store_true')
    args=parser.parse_args();generate(args.output,args.tests)
