#!/usr/bin/env python3
"""Merge loader resources deterministically and adapt legacy datapack formats."""
import argparse,json,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def generate(version,loader,output):
    files={}
    roots=[ROOT/'common/src/main/resources']
    if version=='26.2':roots.append(ROOT/'neoforge-26.2/src/main/resources')
    roots.append(ROOT/(loader+'-'+version)/'src/main/resources')
    for root in roots:
        for path in root.rglob('*'):
            if not path.is_file() or path.suffix=='.bbmodel':continue
            rel=path.relative_to(root)
            if version=='26.2' and loader=='fabric' and root.name=='resources' and 'neoforge-26.2' in root.parts and rel.parts[:2]==('META-INF','services'):continue
            files[str(rel)]=path.read_bytes()
    generated=set()
    for name,data in files.items():
        if version=='26.2' and loader=='fabric' and name.endswith('/recipe/guide_book.json'):
            recipe=json.loads(data);recipe.pop('neoforge:conditions',None)
            recipe['fabric:load_conditions']=[{'condition':'fabric:all_mods_loaded','values':['patchouli']}]
            data=(json.dumps(recipe,indent=2)+'\n').encode()
        if version in ['1.12.2','1.16.5']:
            if name.startswith('assets/simplyspeakers/items/'):continue
            if '/recipe/guide_book.json' in name:continue # Patchouli remains optional; no absent serializer at startup.
            name=name.replace('/recipe/','/recipes/').replace('/loot_table/','/loot_tables/').replace('/tags/block/','/tags/blocks/')
            if name.startswith('data/') and '/recipes/' in name:
                recipe=json.loads(data)
                recipe.pop('category',None);recipe.pop('show_notification',None)
                for field in ['fabric:load_conditions','conditions','neoforge:conditions']:recipe.pop(field,None)
                if 'id' in recipe.get('result',{}):recipe['result']['item']=recipe['result'].pop('id')
                # Copper and amethyst were added after both legacy versions.
                recipe=json.loads(json.dumps(recipe).replace('minecraft:copper_ingot','minecraft:gold_ingot').replace('minecraft:amethyst_shard','minecraft:quartz'))
                if version=='1.12.2':
                    name=name.replace('data/simplyspeakers/','assets/simplyspeakers/')
                    recipe=json.loads(json.dumps(recipe).replace('minecraft:note_block','minecraft:noteblock'))
                data=(json.dumps(recipe,indent=2)+'\n').encode()
            elif version=='1.12.2' and name.startswith('data/'):continue
            if version=='1.12.2' and name.startswith('assets/simplyspeakers/lang/') and name.endswith('.json'):
                values=json.loads(data);name=name[:-5]+'.lang'
                data=('\n'.join(k+'='+str(v).replace('\n','\\n') for k,v in sorted(values.items()))+'\n').encode()
        target=output/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data);generated.add(target)
    for path in output.rglob('*'):
        if path.is_file() and path not in generated:path.unlink()
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--version',required=True,choices=['1.12.2','1.16.5','26.2']);parser.add_argument('--loader',required=True,choices=['forge','fabric','neoforge']);parser.add_argument('--output',required=True,type=Path)
    args=parser.parse_args();generate(args.version,args.loader,args.output)
