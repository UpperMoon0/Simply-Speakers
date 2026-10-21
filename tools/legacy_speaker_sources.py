#!/usr/bin/env python3
"""Derive the full Speakers runtime for Java 8 and Minecraft 1.16.5.

Only owned mod sources are transformed. Canonical game sources remain external
reference material. The generator fails on changed structural anchors instead of
silently dropping behavior. Jabel lowers language syntax; --release 8 checks APIs.
"""
import argparse
import re
from pathlib import Path
from java8_records import lower_records, end_pair, split_params
ROOT=Path(__file__).resolve().parents[1]
COMPAT='com.nstut.simplyspeakers.compat.Java8'

def java8(text):
    text=lower_records(text)
    text=text.replace('AudioSystem.getAudioInputStream(audioFile)','com.nstut.simplyspeakers.compat.LegacyAudioFormats.open(audioFile)').replace('AudioSystem.getAudioInputStream(buffered)','com.nstut.simplyspeakers.compat.LegacyAudioFormats.open(buffered)')
    text=text.replace("org.slf4j.LoggerFactory", "org.apache.logging.log4j.LogManager").replace("org.slf4j.Logger", "org.apache.logging.log4j.Logger").replace("LoggerFactory.getLogger", "LogManager.getLogger")
    text=re.sub(r'\b(?:java\.util\.)?List\.<([^>]+)>of\(',COMPAT+r'.<\1>list(',text)
    text=re.sub(r'("(?:[^"\\]|\\.)*")\.repeat\(([^()]*)\)',COMPAT+r'.repeat(\1,\2)',text)
    text=re.sub(r'\b(\w+)\.readAllBytes\(\)',COMPAT+r'.readAllBytes(\1)',text)
    text=text.replace('JsonParser.parseString(', 'new JsonParser().parse(')
    text=text.replace('config.getAsJsonArray("client").asList().stream()', 'java.util.stream.StreamSupport.stream(config.getAsJsonArray("client").spliterator(),false)').replace('config.getAsJsonArray("mixins").isEmpty()', '(config.getAsJsonArray("mixins").size()==0)')
    text=text.replace('.orElseThrow()', '.orElseThrow(java.util.NoSuchElementException::new)')
    text=text.replace('.toArray(UIComponent[]::new)', '.toArray(new UIComponent[0])')
    text=re.sub(r'([A-Za-z_]\w*(?:\.[A-Za-z_]\w*(?:\([^()]*\))?)*)\.stripTrailing\(\)',COMPAT+r'.stripTrailing(\1)',text)
    for coll,method in [('List','of'),('List','copyOf'),('Set','of'),('Set','copyOf'),('Map','of'),('Map','copyOf')]:
        text=re.sub(r'\b(?:java\.util\.)?'+coll+r'\.'+method+r'\(', COMPAT+'.'+coll.lower()+('Copy' if method=='copyOf' else '')+'(',text)
    text=text.replace('.toList()', '.collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(),java.util.Collections::unmodifiableList))')
    text=text.replace('Path::of','java.nio.file.Paths::get')
    text=text.replace('Path.of(', 'java.nio.file.Paths.get(').replace('Files.readString(', COMPAT+'.readString(').replace('Files.writeString(',COMPAT+'.writeString(')
    text=re.sub(r'([A-Za-z_]\w*(?:\.[A-Za-z_]\w*(?:\([^()]*\))?)*)\.isBlank\(\)',COMPAT+r'.isBlank(\1)',text)
    text=text.replace('java.nio.file.java.nio.file.Paths', 'java.nio.file.Paths')
    text=text.replace('return ServiceLoader.load(clazz)\n                .findFirst()\n                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));', 'return '+COMPAT+'.service(clazz);')
    return text

def remove_method(text, marker):
    start=text.index(marker)
    # Include only the directly associated annotations, not previous methods.
    line=text.rfind('\n',0,start)+1
    while line>0:
        previous=text.rfind('\n',0,line-1)+1
        if text[previous:line].strip() in ['@Override','@Nullable','@NotNull']:line=previous
        else:break
    body=text.index('{',start);end=end_pair(text,body,'{','}')
    return text[:line]+text[end+1:]

def convert(text, name):
    text=java8(text)
    text=text.replace('dev.architectury.', 'me.shedaniel.architectury.')
    text=text.replace('me.shedaniel.architectury.registry.registries.', 'me.shedaniel.architectury.registry.')
    text=text.replace('me.shedaniel.architectury.event.events.common.', 'me.shedaniel.architectury.event.events.')
    text=text.replace('me.shedaniel.architectury.event.events.client.ClientGuiEvent', 'me.shedaniel.architectury.event.events.GuiEvent')
    text=text.replace('net.minecraft.client.gui.GuiGraphics','com.nstut.openui.graphics.LegacyGuiGraphics')
    text=re.sub(r'\bGuiGraphics\b','LegacyGuiGraphics',text)
    text=text.replace('Component.empty()', 'new net.minecraft.network.chat.TextComponent("")').replace('Component.literal(', 'new net.minecraft.network.chat.TextComponent(').replace('Component.translatable(', 'new net.minecraft.network.chat.TranslatableComponent(')
    text=text.replace('net.minecraft.network.chat.new net.minecraft.network.chat.', 'new net.minecraft.network.chat.')
    text=text.replace('net.minecraft.core.registries.Registries','net.minecraft.core.Registry')
    text=text.replace('Registries.BLOCK_ENTITY_TYPE','Registry.BLOCK_ENTITY_TYPE_REGISTRY').replace('Registries.BLOCK','Registry.BLOCK_REGISTRY').replace('Registries.ITEM','Registry.ITEM_REGISTRY').replace('Registries.SOUND_EVENT','Registry.SOUND_EVENT_REGISTRY')
    text=text.replace('SoundEvent.createVariableRangeEvent(', 'new SoundEvent(')
    text=text.replace('com.mojang.logging.LogUtils','com.nstut.simplyspeakers.compat.LogUtils')
    # Entity.level became an accessor after 1.16. Keep record accessors untouched.
    text=re.sub(r'\b(player|oldPlayer|newPlayer|holder|observer)\.level\(\)',r'\1.level',text)
    text=text.replace('.holder().level()', '.holder().level')
    text=text.replace('player.serverLevel()', 'player.getLevel()').replace('player.getInventory()', 'player.inventory')
    text=re.sub(r'\b(holder|player|entity|target)\.getYRot\(\)',r'\1.yRot',text)
    text=re.sub(r'\b(holder|player|entity|target)\.getXRot\(\)',r'\1.xRot',text)
    text=text.replace('player.isRemoved()', 'player.removed')
    text=text.replace('ctx.getSource().getPlayer()', 'ctx.getSource().getPlayerOrException()')
    text=re.sub(r'client\.player\.set([XY])Rot\(([^;]*)\);',r'client.player.\1Rot = \2;',text)
    text=text.replace('player.YRot','player.yRot').replace('player.XRot','player.xRot')
    text=text.replace('client.options.guiScale().get()', 'client.options.guiScale')
    text=re.sub(r'client\.options\.guiScale\(\).set\(([^;]*)\);',r'client.options.guiScale = \1;',text)
    text=text.replace('client.getSoundManager().reload()', 'client.reloadResourcePacks()')
    text=re.sub(r'client\.getConnection\(\).sendCommand\(([^;]*)\);',r'client.getConnection().send(new net.minecraft.network.protocol.game.ServerboundChatPacket("/" + (\1)));',text)
    text=text.replace('.sendSuccess(() ->', '.sendSuccess(')
    text=text.replace('BlockPosArgument.getBlockPos(', 'BlockPosArgument.getLoadedBlockPos(')
    text=text.replace('from.lerp(target, alpha)', 'from.add(target.subtract(from).scale(alpha))')
    text=re.sub(r'\b(buf|buffer)\.writeUtf\(',r'com.nstut.simplyspeakers.compat.LegacyBuffers.writeUtf(\1, ',text)
    text=text.replace('buf.readList(', 'com.nstut.simplyspeakers.compat.LegacyBuffers.readList(buf, ')
    text=text.replace('buf.writeCollection(', 'com.nstut.simplyspeakers.compat.LegacyBuffers.writeCollection(buf, ')
    text=text.replace('        if (Boolean.getBoolean("simplyspeakers.ccLuaTest")) com.nstut.simplyspeakers.testing.CcLuaServerProbe.tick(server);', '')
    # LegacyGuiGraphics exposes the native explicit-shadow form of drawString.
    for match in list(re.finditer(r'\bg\.drawString\(',text))[::-1]:
        start=match.end()-1;end=end_pair(text,start)
        if len(split_params(text[start+1:end].replace('<','').replace('>','')))==5:text=text[:end]+', true'+text[end:]
    if name in ['SpeakerBlock.java','ProxySpeakerBlock.java','RedstoneControllerBlock.java']:
        text=text.replace('import net.minecraft.world.level.block.entity.BlockEntityTicker;\n','')
        text=remove_method(text, 'public <T extends BlockEntity> BlockEntityTicker<T> getTicker(')
        text=re.sub(r'newBlockEntity\((?:@NotNull )?BlockPos pos, (?:@NotNull )?BlockState state\)', 'newBlockEntity(net.minecraft.world.level.BlockGetter world)',text)
        text=text.replace('new '+name[:-5]+'Entity(pos, state)', 'new '+name[:-5]+'Entity()')
    if name in ['SpeakerBlockEntity.java','ProxySpeakerBlockEntity.java','RedstoneControllerBlockEntity.java']:
        cls=name[:-5]
        text=text.replace('extends BlockEntity', 'extends com.nstut.simplyspeakers.compat.LegacyBlockEntity')
        marker='public class '+cls
        start=text.index('{', text.index(marker))+1
        text=text[:start]+'''\n    public %s() { this(BlockPos.ZERO, com.nstut.simplyspeakers.blocks.BlockRegistries.%s.get().defaultBlockState()); }
    @Override public void tick() { if(level != null && !level.isClientSide) serverTick(level, getBlockPos(), getBlockState(), this); }
'''%(cls,{'SpeakerBlockEntity':'SPEAKER','ProxySpeakerBlockEntity':'PROXY_SPEAKER','RedstoneControllerBlockEntity':'REDSTONE_CONTROLLER'}[cls])+text[start:]
        text=text.replace('Packet<ClientGamePacketListener> getUpdatePacket()', 'ClientboundBlockEntityDataPacket getUpdatePacket()')
        text=text.replace('ClientboundBlockEntityDataPacket.create(this)', 'new ClientboundBlockEntityDataPacket(getBlockPos(), 0, getUpdateTag())')
    if name=='RedstoneControllerBlockEntity.java':
        text=text.replace('    boolean coordinatorEligible() {', '    void retiredFromWorld() { awaitingFirstObservation = true; }\n    boolean coordinatorEligible() {')
    if name=='ControllerCoordinator.java':
        old='        controllers.removeIf(c -> c.isRemoved() || c.getLevel() == null);'
        assert old in text, 'Controller retirement anchor changed'
        text=text.replace(old, '        controllers.removeIf(c -> {\n            if(com.nstut.simplyspeakers.compat.LegacyControllerLifecycle.isCurrent(c))return false;\n            c.retiredFromWorld();return true;\n        });\n        pulses.values().forEach(list -> list.removeIf(c -> !controllers.contains(c)));')
        text=text.replace('return c.coordinatorEligible();', 'return com.nstut.simplyspeakers.compat.LegacyControllerLifecycle.isCurrent(c) && c.coordinatorEligible();')
    if name=='PortableSpeakerEndpoint.java':
        text=text.replace('@Override protected boolean isPhysicalSpeaker()', '@Override protected boolean usesWorldBlockState() { return false; }\n    @Override protected boolean isPhysicalSpeaker()')
    if name=='SoundEngineMixin.java' and 'emergencyShutdown' in text:
        text=remove_method(text, 'private void simplyspeakers$beforeEmergencyShutdown(')
        text=text.replace('    @Inject(method = "emergencyShutdown", at = @At("HEAD"))\n','')
    if name=='SimplySpeakers.java':
        text=re.sub(r'    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS[^\n]*\n','',text)
        start=text.index('    public static final RegistrySupplier<CreativeModeTab> CREATIVE_TAB')
        end=text.index('\n    /**',start)
        text=text[:start]+'''    public static final CreativeModeTab CREATIVE_TAB = me.shedaniel.architectury.registry.CreativeTabs.create(
            new ResourceLocation(MOD_ID, "tab"), () -> new ItemStack(Services.ITEMS.getSpeakerItem()));
'''+text[end:]
        text=text.replace('        CREATIVE_TABS.register(); // Register the creative tab\n','')
    if name=='ItemRegistries.java':
        text=text.replace('new Item.Properties()', 'new Item.Properties().tab(SimplySpeakers.CREATIVE_TAB)')
    if name=='PacketRegistries.java':
        text=text.replace('me.shedaniel.architectury.networking.NetworkChannel','com.nstut.simplyspeakers.compat.LegacyChannel').replace('NetworkChannel','LegacyChannel')
    if name=='SpeakerCommands.java':
        text=re.sub(r'(private static int (?:speakerInfo|speakerTransport|speakerSeek|setAccessMode)\([^\n]+\)) \{',r'\1 throws com.mojang.brigadier.exceptions.CommandSyntaxException {',text)
    if name=='DirectionalPreview.java':
        text=text.replace('new org.joml.Vector3f(((color >> 16) & 255)/255f,((color >> 8) & 255)/255f,(color & 255)/255f)', '((color >> 16) & 255)/255f,((color >> 8) & 255)/255f,(color & 255)/255f')
    if name=='ClientEvents.java':
        text=text.replace('DirectionalPreview.renderHud(graphics)', 'DirectionalPreview.renderHud(new com.nstut.openui.graphics.LegacyGuiGraphics(graphics))')
    return text

def generate(output):
    generated=set()
    sources=['common/src/main/java','common-1.20.1/src/main/java','shared/runtime-verification/src/main/java','common-legacy/src/main/java','common-1.16.5/src/main/java']
    for folder in sources:
        root=ROOT/folder
        for path in root.rglob('*.java'):
            if path.name=='CcLuaServerProbe.java': continue
            relative=path.relative_to(root);target=output/relative
            text=path.read_text(encoding='utf-8')
            if folder not in ['common-legacy/src/main/java','common-1.16.5/src/main/java']:text=convert(text,path.name)
            target.parent.mkdir(parents=True,exist_ok=True);target.write_text(text,encoding='utf-8');generated.add(target)
    for path in output.rglob('*.java'):
        if path not in generated:path.unlink()
def generate_tests(output):
    generated=set()
    for folder in ['common/src/test/java','common-legacy/src/test/java','common-1.16.5/src/test/java']:
        root=ROOT/folder
        for path in root.rglob('*.java'):
            target=output/path.relative_to(root);target.parent.mkdir(parents=True,exist_ok=True)
            target.write_text(java8(path.read_text(encoding='utf-8')),encoding='utf-8');generated.add(target)
    for name in ['PortablePacketDedicatedSideLoadingTest.java','PlaybackWireContractTest.java']:
        dedicated=ROOT/'shared/integration-tests/src/test/java/com/nstut/simplyspeakers/network'/name
        target=output/'com/nstut/simplyspeakers/network'/name
        target.parent.mkdir(parents=True,exist_ok=True);target.write_text(java8(dedicated.read_text()),encoding='utf-8');generated.add(target)
    for path in output.rglob('*.java'):
        if path not in generated:path.unlink()

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,required=True);parser.add_argument('--tests',action='store_true')
    args=parser.parse_args();(generate_tests if args.tests else generate)(args.output)
