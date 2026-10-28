#!/usr/bin/env python3
"""Derive complete owned Speakers behavior for the genuine Forge 1.12.2 API.

Game source is only an external reference. The output combines shared behavior
with native Forge lifecycle, registration, GUI, commands and packet adapters.
"""
import argparse,re
from pathlib import Path
from legacy_speaker_sources import java8,remove_method,isolate_client_packet_handlers,replace_java_identifier
from java8_records import end_pair,split_params
ROOT=Path(__file__).resolve().parents[1]
P='com.nstut.simplyspeakers.compat.'
TYPES={
'net.minecraft.core.BlockPos':'net.minecraft.util.math.BlockPos','net.minecraft.core.Direction':'net.minecraft.util.EnumFacing',
'net.minecraft.nbt.CompoundTag':'net.minecraft.nbt.NBTTagCompound','net.minecraft.network.FriendlyByteBuf':P+'LegacyBuffer',
'net.minecraft.network.chat.Component':'net.minecraft.util.text.ITextComponent','net.minecraft.resources.ResourceLocation':'net.minecraft.util.ResourceLocation',
'net.minecraft.server.level.ServerLevel':'net.minecraft.world.WorldServer','net.minecraft.server.level.ServerPlayer':'net.minecraft.entity.player.EntityPlayerMP',
'net.minecraft.world.level.Level':'net.minecraft.world.World','net.minecraft.world.level.block.entity.BlockEntity':'net.minecraft.tileentity.TileEntity',
'net.minecraft.world.level.block.entity.BlockEntityType':P+'LegacyTileType','net.minecraft.world.level.block.state.BlockState':'net.minecraft.block.state.IBlockState',
'net.minecraft.world.entity.player.Player':'net.minecraft.entity.player.EntityPlayer','net.minecraft.world.entity.LivingEntity':'net.minecraft.entity.EntityLivingBase',
'net.minecraft.world.item.ItemStack':'net.minecraft.item.ItemStack','net.minecraft.world.item.Item':'net.minecraft.item.Item',
'net.minecraft.world.phys.Vec3':'net.minecraft.util.math.Vec3d','net.minecraft.world.InteractionHand':'net.minecraft.util.EnumHand',
'net.minecraft.world.InteractionResult':'net.minecraft.util.EnumActionResult','net.minecraft.world.InteractionResultHolder':'net.minecraft.util.ActionResult',
'net.minecraft.client.gui.screens.Screen':'net.minecraft.client.gui.GuiScreen','net.minecraft.client.gui.GuiGraphics':'com.nstut.openui.graphics.LegacyGuiGraphics',
'net.minecraft.client.gui.Font':'com.nstut.openui.compat.LegacyFont','net.minecraft.ChatFormatting':'net.minecraft.util.text.TextFormatting',
'net.minecraft.network.protocol.Packet':'net.minecraft.network.Packet','net.minecraft.network.protocol.game.ClientGamePacketListener':'net.minecraft.network.play.INetHandlerPlayClient',
'net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket':'net.minecraft.network.play.server.SPacketUpdateTileEntity',
'net.minecraft.commands.CommandSourceStack':P+'LegacyCommandSource','net.minecraft.commands.Commands':P+'LegacyCommands',
'net.minecraft.commands.arguments.coordinates.BlockPosArgument':P+'LegacyBlockPosArgument',
'dev.architectury.networking.NetworkManager':P+'LegacyNetwork','dev.architectury.networking.NetworkChannel':P+'LegacyChannel',
'dev.architectury.platform.Platform':P+'LegacyPlatform','com.mojang.logging.LogUtils':P+'LogUtils',
}
OVERRIDES={'SimplySpeakers.java','ClientEvents.java','SpeakerBlock.java','ProxySpeakerBlock.java','RedstoneControllerBlock.java','BlockRegistries.java','BlockEntityRegistries.java','ItemRegistries.java','Services.java','SoundEngineMixin.java'}

def types(text):
    aliases={}
    def imp(m):
        old=m[1]
        if old in TYPES:
            new=TYPES[old];aliases[old.rsplit('.',1)[-1]]=new.rsplit('.',1)[-1]
            return 'import '+new+';'
        return m[0]
    text=re.sub(r'import ([\w.]+);',imp,text)
    for a,b in sorted(TYPES.items(),key=lambda p:-len(p[0])):text=re.sub(r'\b'+re.escape(a)+r'\b',b,text)
    for a,b in aliases.items():text=re.sub(r'(?<![\w.])'+a+r'\b',b,text)
    return text

def split_arguments(text):
    parts=[];start=0;depth=0;quote=None;escape=False
    for i,char in enumerate(text):
        if quote:
            if escape:escape=False
            elif char=='\\':escape=True
            elif char==quote:quote=None
        elif char in ['"',"'"]:quote=char
        elif char in '([{':depth+=1
        elif char in ')]}':depth-=1
        elif char==',' and depth==0:parts.append(text[start:i]);start=i+1
    parts.append(text[start:]);return parts

def convert(text,name):
    text=java8(text)
    text=text.replace('Component.literal(',P+'LegacyText.literal(').replace('Component.translatable(',P+'LegacyText.translatable(').replace('Component.empty()',P+'LegacyText.literal("")')
    text=text.replace('net.minecraft.network.chat.'+P,P)
    text=types(text)
    text=text.replace('Minecraft.getInstance()', 'Minecraft.getMinecraft()')
    text=text.replace('net.minecraft.client.Minecraft.getInstance()', 'net.minecraft.client.Minecraft.getMinecraft()')
    text=re.sub(r'\b(player|oldPlayer|newPlayer|holder|observer)\.level\(\)',r'\1.world',text).replace('.holder().level()', '.holder().world')
    for a,b in [('getUUID','getUniqueID'),('serverLevel','getServerWorld'),('getItemInHand','getHeldItem'),('distanceToSqr','getDistanceSq'),('getPlayerByUUID','getPlayerEntityByUUID'),('getBlockEntity','getTileEntity'),('hasChunkAt','isBlockLoaded'),('getGameTime','getTotalWorldTime'),('getServer','getMinecraftServer'),('sendBlockUpdated','notifyBlockUpdate'),('updateNeighbourForOutputSignal','updateComparatorOutputLevel'),('getBestNeighborSignal','getRedstonePowerFromNeighbors'),('mayInteract','isBlockModifiable'),('getBlockPos','getPos'),('setChanged','markDirty'),('setRemoved','invalidate'),('getLevel','getWorld'),('setLevel','setWorld'),('defaultBlockState','getDefaultState'),('setValue','withProperty'),('getValue','getValue'),('getTickCount','getTickCounter'),('getEyePosition','getPositionEyes'),('getLookAngle','getLookVec'),('getYRot','getRotationYaw'),('getXRot','getRotationPitch'),('toShortString','toString'),('getTag','getTagCompound'),('setTag','setTagCompound'),('readUtf','readUtf'),('immutable','toImmutable'),('fromYRot','fromAngle')]:
        text=text.replace('.'+a+'(','.'+b+'(')
    text=re.sub(r'\b(player|holder|observer)\.isAlive\(\)',r'\1.isEntityAlive()',text)
    text=re.sub(r'\b(player|holder|observer)\.position\(\)',r'\1.getPositionVector()',text)
    text=text.replace('.hasTag()', '.hasTagCompound()').replace('.asLong()', '.toLong()')
    text=text.replace('.hasPermissions(2)', '.canUseCommand(2, "simplyspeakers")')
    text=text.replace('.isRemoved()', '.isDead').replace('.isClientSide()', '.isRemote').replace('.isClientSide', '.isRemote')
    text=text.replace('player.getInventory()', 'player.inventory')
    text=re.sub(r'\b(player|holder|observer)\.getName\(\)',r'\1.getDisplayName()',text)
    text=re.sub(r'\b(player|holder|observer)\.getMinecraftServer\(\)',r'\1.getServer()',text)
    if name=='PortableSpeakerManager.java':text=text.replace('player.inventory.items','player.inventory.mainInventory')
    text=text.replace('player.getInventory()', 'player.inventory')
    text=re.sub(r'\b(player|holder|observer)\.getName\(\)',r'\1.getDisplayName()',text)
    text=re.sub(r'\b(player|holder|observer)\.getMinecraftServer\(\)',r'\1.getServer()',text)
    text=re.sub(r'\b(player|holder|entity|target)\.getRotationYaw\(\)',r'\1.rotationYaw',text)
    text=re.sub(r'\b(player|holder|entity|target)\.getRotationPitch\(\)',r'\1.rotationPitch',text)
    text=text.replace('.getPlayerList().getPlayer(', '.getPlayerList().getPlayerByUUID(')
    text=text.replace('server.getAllLevels()', 'java.util.Arrays.asList(server.worlds)')
    text=re.sub(r'\b(\w+)\.players\(\)',P+r'LegacyWorld.players(\1)',text)
    text=text.replace('Minecraft.getMinecraft().level.dimension().location().toString()', P+'LegacyWorld.dimension(Minecraft.getMinecraft().world)')
    text=re.sub(r'([\w]+[\w.]*)\.dimension\(\)\.location\(\)\.toString\(\)',P+r'LegacyWorld.dimension(\1)',text)
    text=re.sub(r'\b(client\.level|level)\.dimension\(\)',P+r'LegacyWorld.dimension(\1)',text)
    text=text.replace('target.level().dimension().location().toString()',P+'LegacyWorld.dimension(target.level())')
    text=text.replace('.getString()', '.getUnformattedText()')
    text=text.replace('online.getName().getUnformattedText()', 'online.getName()')
    text=text.replace('server::execute','server::addScheduledTask').replace('Minecraft.getMinecraft()::execute','Minecraft.getMinecraft()::addScheduledTask')
    text=text.replace('getBlockState().hasProperty(', 'getBlockState().getPropertyKeys().contains(')
    text=text.replace('c.isDead','c.isInvalid()')
    text=text.replace('.hideGui','.hideGUI').replace('.getProfile()', '.getGameProfile()')
    text=text.replace('.getPlayerByName(', '.getPlayerByUsername(')
    text=text.replace('inventory.getContainerSize()', 'inventory.getSizeInventory()').replace('inventory.getItem(', 'inventory.getStackInSlot(')
    text=re.sub(r'([\w.]+)\.hasProperty\(([^)]*)\)',r'\1.getPropertyKeys().contains(\2)',text)
    text=text.replace('Vec3d.atCenterOf(',P+'LegacyWorld.center(')
    text=text.replace('from.lerp(target, alpha)', 'from.add(target.subtract(from).scale(alpha))')
    text=text.replace('holder.getPosition(partial)', P+'LegacyWorld.position(holder,partial)')
    text=text.replace('.getDistanceSq(target)', '.squareDistanceTo(target)').replace('.getDistanceSq(emitterPos)', '.squareDistanceTo(emitterPos)').replace('.getDistanceSq(renderPosition)', '.squareDistanceTo(renderPosition)')
    text=text.replace('net.minecraft.util.math.'+P,P)
    for a,b in [('client.level','client.world'),('minecraft.level','mc.world'),('minecraft','mc'),('.gameDirectory','.gameDir'),('client.options','client.gameSettings'),('.setScreen(','.displayGuiScreen('),('.tell(','.addScheduledTask('),('server.execute(', 'server.addScheduledTask('),('client.execute(', 'client.addScheduledTask('),('Minecraft.getMinecraft().execute(', 'Minecraft.getMinecraft().addScheduledTask('),('.getSoundManager()', '.getSoundHandler()')]:
        # Avoid rewriting net.minecraft package names.
        if a=='minecraft':text=replace_java_identifier(text,'minecraft','mc')
        else:text=text.replace(a,b)
    text=re.sub(r'(?<![\w.])(client|mc)\.screen\b',r'\1.currentScreen',text)
    text=text.replace('Minecraft.getMinecraft().screen','Minecraft.getMinecraft().currentScreen')
    text=text.replace('Minecraft.getMinecraft().level','Minecraft.getMinecraft().world')
    text=text.replace('mc.level','mc.world').replace('mc.options','mc.gameSettings')
    text=text.replace('net.minecraft.sounds.SoundSource','net.minecraft.util.SoundCategory').replace('.getSoundSourceVolume(','.getSoundLevel(')
    text=text.replace('.displayClientMessage(','.sendStatusMessage(')
    text=text.replace('mc.getSoundHandler().reload()', 'mc.refreshResources()')
    text=text.replace('client.getSoundHandler().reload()', 'client.refreshResources()')
    text=text.replace('client.font','com.nstut.openui.compat.LegacyFont.current()').replace('mc.font','com.nstut.openui.compat.LegacyFont.current()')
    text=text.replace('Minecraft.getMinecraft().font','com.nstut.openui.compat.LegacyFont.current()')
    text=text.replace('client.getWindow().getGuiScaledWidth()', 'new net.minecraft.client.gui.ScaledResolution(client).getScaledWidth()')
    text=text.replace('LegacyPlatform.getConfigFolder()', 'net.minecraftforge.fml.common.Loader.instance().getConfigDir().toPath()')
    text=text.replace('.getDirection()', '.getDirection()')
    # Old NBT names are adapted only on known compound receivers/method references.
    for a,b in [('putString','setString'),('putInt','setInteger'),('putLong','setLong'),('putBoolean','setBoolean'),('putFloat','setFloat'),('putUUID','setUniqueId'),('hasUUID','hasUniqueId'),('getUniqueID','getUniqueId'),('getInt','getInteger'),('contains','hasKey'),('remove','removeTag')]:
        for receiver in ['tag','data','nbt']:
            text=text.replace(receiver+'.'+a+'(',receiver+'.'+b+'(').replace(receiver+'::'+a,receiver+'::'+b)
    text=text.replace('buf.readList(',P+'LegacyBuffers.readList(buf, ').replace('buf.writeCollection(',P+'LegacyBuffers.writeCollection(buf, ')
    for m in list(re.finditer(r'\bg\.drawString\(',text))[::-1]:
        start=m.end()-1;end=end_pair(text,start)
        if len(split_arguments(text[start+1:end]))==5:text=text[:end]+', true'+text[end:]
    if name.endswith('BlockEntity.java') or name=='PortableSpeakerEndpoint.java':
        text=re.sub(r'\bworldPosition\b','pos',text);text=re.sub(r'\blevel\b','world',text)
        text=text.replace('extends TileEntity','extends '+P+'LegacyBlockEntity')
        text=text.replace('SPacketUpdateTileEntity.create(this)','new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag())')
        text=text.replace('public Packet<INetHandlerPlayClient> getUpdatePacket()', 'public SPacketUpdateTileEntity getUpdatePacket()')
        if name!='PortableSpeakerEndpoint.java':
            cls=name[:-5];block={'SpeakerBlockEntity':'SPEAKER','ProxySpeakerBlockEntity':'PROXY_SPEAKER','RedstoneControllerBlockEntity':'REDSTONE_CONTROLLER'}[cls]
            pos=text.index('{',text.index('public class '+cls))+1
            text=text[:pos]+('\n    public %s() { this(BlockPos.ORIGIN, com.nstut.simplyspeakers.blocks.BlockRegistries.%s.get().getDefaultState()); }\n    @Override public void update() { if(world!=null && !world.isRemote)serverTick(world,getPos(),getBlockState(),this); }\n'%(cls,block))+text[pos:]
    for before,after in [('setChanged','markDirty'),('setRemoved','invalidate'),('getBlockPos','getPos')]:text=re.sub(r'\b'+before+r'\(',after+'(',text)
    text=text.replace('!currentState.is(com.nstut.simplyspeakers.blocks.BlockRegistries.PROXY_SPEAKER.get())','currentState.getBlock()!=com.nstut.simplyspeakers.blocks.BlockRegistries.PROXY_SPEAKER.get()')
    text=text.replace('BlockPos.ZERO','BlockPos.ORIGIN')
    if name=='RedstoneControllerBlockEntity.java':
        anchor='    boolean coordinatorEligible() {'
        assert anchor in text, 'Native controller lifecycle anchor changed'
        text=text.replace(anchor, '''    void retiredFromWorld() { awaitingFirstObservation = true; pulseSignal = 0; }
    @Override public void onChunkUnload() {
        retiredFromWorld();
        ControllerCoordinator.remove(this);
        super.onChunkUnload();
    }
'''+anchor)
    if name=='ControllerCoordinator.java':
        anchor='        controllers.removeIf(c -> c.isInvalid() || c.getWorld() == null);'
        assert anchor in text, 'Native controller retirement anchor changed'
        text=text.replace(anchor, '''        controllers.removeIf(c -> {
            if(com.nstut.simplyspeakers.compat.LegacyControllerLifecycle.isCurrent(c)) return false;
            c.retiredFromWorld(); return true;
        });
        pulses.values().forEach(list -> list.removeIf(c -> !controllers.contains(c)));''')
        text=text.replace('return c.coordinatorEligible();', 'return com.nstut.simplyspeakers.compat.LegacyControllerLifecycle.isCurrent(c) && c.coordinatorEligible();')
    if name=='PortableSpeakerItem.java':
        text=text.replace('public PortableSpeakerItem(Properties properties) { super(properties.stacksTo(1)); }','public PortableSpeakerItem() { setMaxStackSize(1); }')
        text=text.replace(' use(World level, EntityPlayer player, EnumHand hand)', ' onItemRightClick(World level, EntityPlayer player, EnumHand hand)')
        text=text.replace('ActionResult.sidedSuccess(player.getHeldItem(hand), level.isRemote)', 'new ActionResult<ItemStack>(net.minecraft.util.EnumActionResult.SUCCESS, player.getHeldItem(hand))')
    if name=='ClientAudioPlayer.java':
        text=text.replace('private static final AudioContextGate audioContext = new AudioContextGate();', 'private static final AudioContextGate audioContext = '+P+'LegacySoundContext.newSuspendedGate();')
        text=text.replace('AL10.alGenBuffers(bufferIDs)',P+'LegacyAudio.genBuffers(bufferIDs)').replace('AL10.alDeleteBuffers(bufferIDs)',P+'LegacyAudio.deleteBuffers(bufferIDs)').replace('AL10.alSourceUnqueueBuffers(sourceID, buffers)',P+'LegacyAudio.unqueueBuffers(sourceID, buffers)')
    if name=='SimplySpeakersUiScreen.java':
        text=remove_method(text,'public boolean mouseClicked(');text=remove_method(text,'public boolean mouseReleased(')
        text=text.replace('    protected ButtonWidget buildThemeToggle()', '    public net.minecraft.util.text.ITextComponent getTitle() { return title; }\n    public void onClose() { mc.displayGuiScreen(null); }\n    @Override protected void keyTyped(char character, int scanCode) {\n        if (scanCode == org.lwjgl.input.Keyboard.KEY_ESCAPE) {\n            if (uiRuntime() != null && uiRuntime().keyPressed(256, scanCode, com.nstut.openui.minecraft.LegacyKeys.modifiers())) return;\n            onClose();\n        } else super.keyTyped(character, scanCode);\n    }\n\n    protected ButtonWidget buildThemeToggle()')
    if name.endswith('Screen.java'):
        text=text.replace('protected void init()', 'public void initGui()').replace('super.init()', 'super.initGui()')
        text=text.replace('public void removed()', 'public void onGuiClosed()').replace('super.removed()', 'super.onGuiClosed()')
        text=text.replace('public void tick()', 'public void updateScreen()').replace('super.tick()', 'super.updateScreen()')
        text=text.replace('public boolean isPauseScreen()', 'public boolean doesGuiPauseGame()')
        text=text.replace('@Override public void onClose()', 'public void onClose()').replace('super.onClose()', 'mc.displayGuiScreen(null)')
    if name=='SpeakerScreen.java':
        text=text.replace('var mouse = Minecraft.getMinecraft().mouseHandler;', 'var mouse = new '+P+'LegacyMouse();')
        text=text.replace('var window = Minecraft.getMinecraft().getWindow();', 'var window = new '+P+'LegacyWindow();')
    if name=='ZonePreviewRenderer.java':
        text=text.replace('org.lwjgl.opengl.GL.getCapabilities()', 'org.lwjgl.opengl.GLContext.getCapabilities()')
        text=text.replace('GL11.glGetIntegerv(', 'GL11.glGetInteger(')
        text=text.replace('GL20.glUniformMatrix4fv(', 'GL20.glUniformMatrix4(')
    if name=='DirectionalPreview.java':
        text=text.replace('    private static final org.joml.Matrix4f viewProjection = new org.joml.Matrix4f();\n','')
        begin=text.index('    public static void renderWorld(')
        end=text.index('    public static void reload()',begin)
        text=text[:begin]+'''    public static void renderWorld(java.nio.FloatBuffer matrixBuffer, double cameraX, double cameraY, double cameraZ, float partial) {
        var client=net.minecraft.client.Minecraft.getMinecraft();
        if(remaining<=0 || client.world==null || client.world!=world || client.currentScreen!=null)return;
        double originX=renderOrigin.x,originY=renderOrigin.y,originZ=renderOrigin.z;
        if(portableHolder!=null) {
            var holder=client.world.getPlayerEntityByUUID(portableHolder);
            if(holder==null || !holder.isEntityAlive()) { clear(); return; }
            originX=holder.lastTickPosX+(holder.posX-holder.lastTickPosX)*partial;
            originY=holder.lastTickPosY+(holder.posY-holder.lastTickPosY)*partial+.5;
            originZ=holder.lastTickPosZ+(holder.posZ-holder.lastTickPosZ)*partial;
            rotation=Math.toRadians(holder.prevRotationYaw+net.minecraft.util.math.MathHelper.wrapDegrees(holder.rotationYaw-holder.prevRotationYaw)*partial+180);
        }
        if(mesh!=null) { renderer.upload(mesh); mesh=null; }
        try { renderer.draw(matrixBuffer,originX-cameraX,originY-cameraY,originZ-cameraZ,rotation); } catch(RuntimeException error) { renderer.fail(error); }
        if(renderer.hasResources()) emitted++;
    }
'''+text[end:]
    if name=='DirectionalPreview.java':
        text=re.sub(r'\s*var particle=new DustParticleOptions\([^;]*;\s*client.world.addAlwaysVisibleParticle\([^;]*;', '\n            client.world.spawnParticle(net.minecraft.util.EnumParticleTypes.REDSTONE, renderOrigin.x+x, renderOrigin.y+point.y(), renderOrigin.z+z, Math.max(0.001, ((color>>16)&255)/255.0), ((color>>8)&255)/255.0, (color&255)/255.0);',text)
        text=text.replace('import net.minecraft.core.particles.DustParticleOptions;','')
    # Runtime verification for later versions is separate from the shipping port.
    text=text.replace('        if (Boolean.getBoolean("simplyspeakers.ccLuaTest")) com.nstut.simplyspeakers.testing.CcLuaServerProbe.tick(server);','')
    text=re.sub(r'        if \(Boolean.getBoolean\("simplyspeakers.livePlaybackTest"\)\) com.nstut.simplyspeakers.testing.LivePlaybackServerProbe.tick\(server\);', '',text)
    if name=='SpeakerBlockEntity.java':
        text=text.replace('boolean migratedInternalId = !tag.hasKey(NBT_INTERNAL_ID);', 'boolean migratedInternalId = !tag.hasUniqueId(NBT_INTERNAL_ID) && !tag.hasKey(NBT_INTERNAL_ID);')
    return isolate_client_packet_handlers(text,name,'!{context}.get().isClient()')

def generate(output):
    generated=set()
    for folder in ['shared/client-preview/src/main/java','common/src/main/java','common-1.20.1/src/main/java','shared/runtime-verification/src/main/java','common-legacy/src/main/java','forge-1.12.2/src/main/java']:
        source=ROOT/folder
        for path in source.rglob('*.java'):
            if folder=='shared/runtime-verification/src/main/java' and path.name not in ['LiveUploadServerProbe.java','LiveUploadClientProbe.java','LiveDownloadClientProbe.java']:continue
            if path.name in ('ZonePreviewLevelMixin.java','ZonePreviewReloadMixin.java','ZonePreviewRuntimeProbe.java','ZonePreviewFrameProbeMixin.java','ZonePreviewOffscreenMixin.java'):continue
            if folder=='common-1.20.1/src/main/java' and path.name in OVERRIDES:continue
            target=output/path.relative_to(source);target.parent.mkdir(parents=True,exist_ok=True)
            value=path.read_text(encoding='utf-8')
            if folder=='common/src/main/java':value=java8(value)
            elif not folder.startswith(('forge-1.12.2','common-legacy')):value=convert(value,path.name)
            if folder=='shared/runtime-verification/src/main/java':
                value=value.replace('LivePlaybackServerProbe.enabled()', 'Boolean.getBoolean("simplyspeakers.livePlaybackTest")').replace('import com.nstut.simplyspeakers.testing.LivePlaybackServerProbe;', '')
                value=value.replace('.blockPosition()', '.getPosition()').replace('.offset(2, 1, 0)', '.add(2, 1, 0)')
            value=value.replace('@VERSION@',next(line.split('=',1)[1].strip() for line in (ROOT/'gradle.properties').read_text().splitlines() if line.strip().startswith('mod_version')))
            target.write_text(value,encoding='utf-8');generated.add(target)
    for path in output.rglob('*.java'):
        if path not in generated:path.unlink()
def generate_tests(output):
    generated=set()
    folders=['common/src/test/java','common-legacy/src/test/java','common-1.16.5/src/test/java','forge-1.12.2/src/test/java']
    for folder in folders:
        source=ROOT/folder
        for path in source.rglob('*.java'):
            if folder=='common-1.16.5/src/test/java' and path.name!='LegacyPacketCodecTest.java':continue
            target=output/path.relative_to(source);target.parent.mkdir(parents=True,exist_ok=True)
            value=path.read_text(encoding='utf-8')
            value=convert(value,path.name) if folder=='common-1.16.5/src/test/java' else java8(value)
            target.write_text(value,encoding='utf-8');generated.add(target)
    for name in ['PortablePacketDedicatedSideLoadingTest.java','PlaybackWireContractTest.java']:
        source=ROOT/'shared/integration-tests/src/test/java/com/nstut/simplyspeakers/network'/name
        target=output/'com/nstut/simplyspeakers/network'/name;target.parent.mkdir(parents=True,exist_ok=True)
        target.write_text(convert(source.read_text(encoding='utf-8'),name),encoding='utf-8');generated.add(target)
    for path in output.rglob('*.java'):
        if path not in generated:path.unlink()

def resources(output):
    import json,port_resources
    port_resources.generate('1.12.2','forge',output)
    assets=output/'assets/simplyspeakers'
    for path in (assets/'recipes').glob('*.json'):
        value=json.loads(path.read_text())
        for ingredient in value.get('key',{}).values():
            if ingredient.get('item')=='minecraft:copper_ingot':ingredient['item']='minecraft:gold_ingot'
            if ingredient.get('item')=='minecraft:amethyst_shard':ingredient['item']='minecraft:quartz'
            if ingredient.get('tag')=='minecraft:planks':ingredient.clear();ingredient.update(item='minecraft:planks',data=32767)
        path.write_text(json.dumps(value,indent=2)+'\n')
    for path in (assets/'blockstates').glob('*.json'):
        value=json.loads(path.read_text())
        variants=value.get('variants',{})
        value['variants']={key+',powered='+powered:port_resources.legacy_variant_model(variant) for key,variant in variants.items() for powered in ['false','true']}
        path.write_text(json.dumps(value,indent=2)+'\n')
    for path in (assets/'lang').glob('*.lang'):
        lines=path.read_text().splitlines()
        for line in list(lines):
            if line.startswith('block.simplyspeakers.'):
                key,value=line.split('=',1);lines.append('tile.'+key[len('block.'):] + '.name='+value)
            elif line.startswith('item.simplyspeakers.'):
                key,value=line.split('=',1);lines.append(key+'.name='+value)
        path.write_text('\n'.join(lines)+'\n')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path);parser.add_argument('--resources',type=Path);parser.add_argument('--tests',action='store_true');args=parser.parse_args()
    if args.output:(generate_tests if args.tests else generate)(args.output)
    elif args.resources:resources(args.resources)
    else:parser.error('--output or --resources is required')
