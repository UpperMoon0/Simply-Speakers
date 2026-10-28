#!/usr/bin/env python3
"""Derive both 26.2 loaders from maintained owned 26.1/shared runtime sources.

Only the native GUI owner changed. Domain, security, packet, audio, persistence,
and control behavior is never maintained as an independent new-version copy.
Native loader registries/entrypoints remain in their small loader source sets.
"""
import argparse,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ROOTS=('neoforge-26.1.2/src/main/java','shared/minecraft-1.21plus/src/main/java','shared/runtime-verification/src/main/java')
NATIVE={'SimplySpeakers.java','ItemRegistries.java','BlockRegistries.java','BlockEntityRegistries.java','CcLuaServerProbe.java'}

def convert(text,name):
    if name=='ZonePreviewRenderer.java':
        # 26.2 replaces uniform declarations/vertex modes with bind layouts and
        # primitive topology, and uses reversed depth for world rendering.
        text=text.replace('.withUniform("ZonePreview", UniformType.UNIFORM_BUFFER)',
            '.withBindGroupLayout(com.mojang.blaze3d.pipeline.BindGroupLayout.builder().withUniform("ZonePreview", UniformType.UNIFORM_BUFFER).build())')
        text=text.replace('.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.TRIANGLES)',
            '.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL).withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)')
        text=text.replace('CompareOp.LESS_THAN_OR_EQUAL','CompareOp.GREATER_THAN_OR_EQUAL').replace('CompareOp.GREATER_THAN);','CompareOp.LESS_THAN);')
        text=text.replace('Minecraft.getInstance().getMainRenderTarget()', 'Minecraft.getInstance().gameRenderer.mainRenderTarget()')
        text=text.replace('OptionalInt.empty()', 'java.util.Optional.empty()')
        text=text.replace('pass.setVertexBuffer(0,vertices)', 'pass.setVertexBuffer(0,vertices.slice())')
        text=text.replace('pass.draw(0,count)', 'pass.draw(count,1,0,0)')
    if name=='ZonePreviewLevelMixin.java':
        text=text.replace('getGameRenderState()', 'gameRenderState()')
        text=text.replace('LevelRenderer;renderLevel(', 'LevelRenderer;render(')
        text=text.replace(';ZLnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V', ';Z)V')
    for owner in ('client','Minecraft.getInstance()'):
        text=re.sub(re.escape(owner)+r'\.screen\b',lambda match: owner+'.gui.screen()',text)
        text=text.replace(owner+'.setScreen(',owner+'.gui.setScreen(')
        text=text.replace(owner+'.getOverlay()',owner+'.gui.overlay()')
    text=text.replace('client.options.hideGui','client.gui.hud.isHidden()')
    if name=='ClientEvents.java':
        text=text.replace('    public static void register() {','    public static void register() {\n        com.nstut.simplyspeakers.testing.RuntimeReceipt.emit(System.getProperty("simplyspeakers.livePlaybackRole","client"));')
    if name=='LivePlaybackServerProbe.java':
        text=text.replace('        if (server != currentServer) {','        if (server != currentServer) {\n            com.nstut.simplyspeakers.testing.RuntimeReceipt.emit("server");')
    if name=='SpeakerUiPreviewProbe.java':
        anchor='Object target = Minecraft.class.getMethod("getMainRenderTarget").invoke(client);'
        assert anchor in text,'Native screenshot target anchor changed'
        text=text.replace(anchor,'Object target = client.gameRenderer.mainRenderTarget();')
    if name=='LivePlaybackClientProbe.java':
        anchor='    private static void verifyGuide(Minecraft client) {'
        assert anchor in text,'Optional guide probe anchor changed'
        text=text.replace(anchor,anchor+'\n        if(Boolean.getBoolean("simplyspeakers.requestedPortRuntime")) { System.out.println("SIMPLYSPEAKERS_OPTIONAL_GUIDE_NOT_INCLUDED"); return; }')
    return '\n'.join(line.rstrip() for line in text.splitlines()).rstrip()+'\n'

def generate(output):
    generated=set()
    for folder in ROOTS:
        root=ROOT/folder
        for source in sorted(root.rglob('*.java')):
            relative=source.relative_to(root)
            if source.name in NATIVE or relative.parts[:3]==('com','nstut','neoforge'):continue
            target=output/relative
            if target in generated:raise ValueError(f'Duplicate shared source: {relative}')
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_text(convert(source.read_text(encoding='utf-8'),source.name),encoding='utf-8')
            generated.add(target)
    for path in output.rglob('*.java'):
        if path not in generated:path.unlink()
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--output',required=True,type=Path)
    generate(parser.parse_args().output)
