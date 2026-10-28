"""Compile and run the real GL renderer in a hidden window using cached LWJGL.

This is a graphics fixture, not Minecraft visual or performance acceptance.
Requires a local JDK and LWJGL 3.3.3/JOML 1.10.5 in the Gradle module cache.
"""
import argparse, os, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def run(cache: Path, java_home: Path, output: Path):
    libraries=[]
    for module in ('lwjgl','lwjgl-opengl','lwjgl-glfw'):
        directory=cache/'org.lwjgl'/module/'3.3.3'
        names=[f'{module}-3.3.3.jar']
        if os.name=='nt': names.append(f'{module}-3.3.3-natives-windows.jar')
        else: names.append(f'{module}-3.3.3-natives-linux.jar')
        for name in names:
            matches=list(directory.glob('*/'+name))
            if len(matches)!=1: raise RuntimeError(f'Missing cached dependency: {directory}/{name}')
            libraries.append(matches[0])
    libraries.append(next((cache/'org.joml/joml/1.10.5').glob('*/joml-1.10.5.jar')))
    classes=ROOT/'build/zone-shader-smoke/classes'; classes.mkdir(parents=True,exist_ok=True)
    classpath=os.pathsep.join(str(p) for p in libraries)
    sources=[ROOT/'tools/ZonePreviewGlSmoke.java',ROOT/'shared/client-preview/src/main/java/com/nstut/simplyspeakers/client/ZonePreviewRenderer.java']
    sources += [ROOT/'common/src/main/java/com/nstut/simplyspeakers/audio'/name for name in
                ('ZonePreviewMesh.java','DirectionalPreviewGeometry.java','DirectionalAudio.java','SpatialAudioCalculator.java')]
    javac=java_home/'bin'/('javac.exe' if os.name=='nt' else 'javac')
    java=java_home/'bin'/('java.exe' if os.name=='nt' else 'java')
    subprocess.run([str(javac),'-encoding','UTF-8','-cp',classpath,'-d',str(classes),*[str(p) for p in sources]],check=True)
    subprocess.run([str(java),'-cp',os.pathsep.join([str(classes),str(ROOT/'common/src/main/resources'),classpath]),
                    'com.nstut.simplyspeakers.client.ZonePreviewGlSmoke',str(output)],check=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--cache',type=Path,required=True)
    parser.add_argument('--java-home',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();run(args.cache,args.java_home,args.output)
