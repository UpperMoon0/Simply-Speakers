#!/usr/bin/env python3
"""Reject drifting build-only dependency checkouts before generating ports."""
import json,re,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def verify(root=ROOT):
    pins=json.loads((root/'docs/requested-port-dependencies.json').read_text())
    for key,folder in [('openui','OpenUI-MC'),('packetweave','PacketWeave')]:
        pin=pins[key];checkout=root/'.port-deps'/folder
        actual=subprocess.check_output(['git','rev-parse','HEAD'],cwd=checkout,text=True).strip()
        if actual!=pin['commit']:raise ValueError(f'{folder} commit differs from the reviewed pin')
        version=re.search(r'^mod_version\s*=\s*(\S+)',(checkout/'gradle.properties').read_text(),re.M)[1]
        if version!=pin['version']:raise ValueError(f'{folder} version differs from the dependency contract')
    print('Pinned requested-generation dependency commits and versions verified')
if __name__=='__main__':verify()
