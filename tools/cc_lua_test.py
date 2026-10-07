#!/usr/bin/env python3
"""Run real CC/Cobalt Lua on placed computers in isolated dedicated test worlds."""
import argparse, socket, subprocess, sys
from pathlib import Path
from live_join_test import OutputPump, popen, stop_tree, command
from verify import checkout_lock

TARGETS=("fabric-1.20.1","forge-1.20.1","fabric-1.21.1","neoforge-1.21.1")
PASS="SIMPLYSPEAKERS_CC_LUA_PASS"

def prepare(root,target):
    directory=(root/target/"run/cc-lua/server").resolve()
    expected=(root/target/"run").resolve()
    if not directory.is_relative_to(expected):raise RuntimeError("CC fixture escaped run directory")
    directory.mkdir(parents=True,exist_ok=True)
    # Never re-use a populated computer or another task's world.
    import shutil
    world=(directory/"cc-lua-world").resolve()
    if world.parent!=directory:raise RuntimeError("Unexpected CC world path")
    if world.exists():shutil.rmtree(world)
    (directory/"eula.txt").write_text("eula=true\n",encoding="utf-8")
    (directory/"server.properties").write_text(
        "online-mode=false\nserver-ip=127.0.0.1\nserver-port=25585\nlevel-name=cc-lua-world\nlevel-type=minecraft:flat\n"
        'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"structure_overrides":[]}\n'
        "spawn-protection=0\nview-distance=3\nsimulation-distance=3\nmotd=Simply Speakers CC Lua fixture\n",
        encoding="utf-8")

def run(root,target,timeout):
    with socket.socket() as port:
        port.bind(("127.0.0.1",25585))
    prepare(root,target)
    cmd=command(root,":"+target+":runCcLuaTestServer")
    cmd=[arg.replace("--max-workers=4","--max-workers=2") for arg in cmd]
    process=popen(cmd,root)
    pump=OutputPump(process,target)
    try:
        if pump.wait_for((PASS,),timeout) is None:raise RuntimeError(target+": Lua did not complete")
        print(target+": REAL CC LUA PASS",flush=True)
    finally:
        stop_tree(process,graceful_server=True)

def main():
    parser=argparse.ArgumentParser();parser.add_argument("--target",choices=TARGETS,action="append");parser.add_argument("--timeout",type=int,default=360)
    args=parser.parse_args();root=Path(__file__).resolve().parents[1]
    with checkout_lock(root,"cc-lua.lock"):
        for target in args.target or TARGETS:run(root,target,args.timeout)
    return 0
if __name__=="__main__":
    try:raise SystemExit(main())
    except (RuntimeError,subprocess.SubprocessError,OSError) as error:
        print("CC LUA TEST FAILED: "+str(error),file=sys.stderr);raise SystemExit(1)
