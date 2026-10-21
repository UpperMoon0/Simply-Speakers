package com.nstut.simplyspeakers.client;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.client.event.*;
import com.nstut.simplyspeakers.network.PlayAudioPacketS2C;
/** Client references are isolated behind Forge's side proxy. */
public final class LegacyClient extends com.nstut.simplyspeakers.forge.CommonProxy {
 private net.minecraft.world.World previousWorld;
 @SubscribeEvent public void models(ModelRegistryEvent event){for(com.nstut.simplyspeakers.compat.RegistryValue<net.minecraft.item.Item> value:com.nstut.simplyspeakers.items.ItemRegistries.ITEM_LIST){net.minecraft.item.Item item=value.get();net.minecraftforge.client.model.ModelLoader.setCustomModelResourceLocation(item,0,new net.minecraft.client.renderer.block.model.ModelResourceLocation(item.getRegistryName(),"inventory"));}}

 public void preInit(){MinecraftForge.EVENT_BUS.register(this);com.nstut.simplyspeakers.client.ui.SimplySpeakersUiPreferences.applyListeningPreferences();PlayAudioPacketS2C.startLiveJoinProbe();}
 public static com.nstut.simplyspeakers.compat.LegacyNetwork.PacketContext packetContext(){return new com.nstut.simplyspeakers.compat.LegacyNetwork.PacketContext(){public boolean isClient(){return true;}public net.minecraft.entity.player.EntityPlayer getPlayer(){return Minecraft.getMinecraft().player;}public void queue(Runnable work){Minecraft.getMinecraft().addScheduledTask(work);}};}
 @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){if(event.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();if(Boolean.getBoolean("simplyspeakers.legacyRuntimeAcceptance"))LegacyClientRuntimeAcceptance.tick(mc);if(previousWorld!=mc.world){if(previousWorld!=null)clear();previousWorld=mc.world;}ClientPortableSpeakers.tick();if(mc.player!=null&&mc.world!=null){PlayAudioPacketS2C.processPendingPlays();ClientAudioPlayer.updateSpeakerVolumes();DirectionalPreview.tick(mc);}}
 @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post event){if(event.getType()==RenderGameOverlayEvent.ElementType.ALL){try(com.nstut.openui.graphics.LegacyGuiGraphics g=new com.nstut.openui.graphics.LegacyGuiGraphics()){DirectionalPreview.renderHud(g);}ClientAudioPlayer.updateSpeakerVolumes();}}
 @SubscribeEvent public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event){Minecraft.getMinecraft().addScheduledTask(()->{clear();ClientAudioPlayer.clearAudioList();com.nstut.simplyspeakers.Config.restoreLocalConfig();});}
 public static void contextReady(boolean ready){com.nstut.simplyspeakers.compat.LegacySoundContext.ready(ready,ClientAudioPlayer::soundContextReady,ClientAudioPlayer::soundContextDestroying);}
 public static void contextDestroying(){ClientAudioPlayer.soundContextDestroying();}
 private static void clear(){DirectionalPreview.clear();ClientAudioPlayer.stopAll();PlayAudioPacketS2C.clearPendingPlays();ClientSpeakerRegistry.clear();}
 public void openSpeakerScreen(BlockPos pos){ClientEvents.openSpeakerScreen(pos);}
 public void openProxySpeakerScreen(BlockPos pos){ClientEvents.openProxySpeakerScreen(pos);}
 public void openRedstoneControllerScreen(BlockPos pos){ClientEvents.openRedstoneControllerScreen(pos);}
 public void openFileDialog(String filter,java.util.function.Consumer<java.io.File> callback){javax.swing.SwingUtilities.invokeLater(()->{javax.swing.JFileChooser chooser=new javax.swing.JFileChooser();chooser.setDialogTitle("Open Audio File");chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("MP3 or WAV audio","mp3","wav"));if(chooser.showOpenDialog(null)==javax.swing.JFileChooser.APPROVE_OPTION){java.io.File file=chooser.getSelectedFile();com.nstut.simplyspeakers.platform.ClientFileSelection.complete(file,Minecraft.getMinecraft()::addScheduledTask,callback);}});}
}
