package com.nstut.simplyspeakers.forge;
import com.nstut.simplyspeakers.*;
import com.nstut.simplyspeakers.blocks.*;
import com.nstut.simplyspeakers.blocks.entities.*;
import com.nstut.simplyspeakers.items.ItemRegistries;
import com.nstut.simplyspeakers.network.*;
import com.nstut.simplyspeakers.speakers.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.*;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
@Mod(modid="simplyspeakers",name="Simply Speakers",version="@VERSION@",acceptedMinecraftVersions="[1.12.2]",dependencies="required-after:openui_mc;required-after:packetweave")
public final class SimplySpeakersForge {
 @SidedProxy(clientSide="com.nstut.simplyspeakers.client.LegacyClient",serverSide="com.nstut.simplyspeakers.forge.CommonProxy")
 public static CommonProxy PROXY;
 private MinecraftServer server;
 @Mod.EventHandler public void preInit(FMLPreInitializationEvent event){loadConfig(event.getSuggestedConfigurationFile());MinecraftForge.EVENT_BUS.register(this);PacketRegistries.init();BlockEntityRegistries.init();PROXY.preInit();}
 @Mod.EventHandler public void starting(FMLServerStartingEvent event){server=event.getServer();java.nio.file.Path root=server.getEntityWorld().getSaveHandler().getWorldDirectory().toPath();SimplySpeakers.initializeAudio(root);ServerSpeakerRegistry.init(root);event.registerServerCommand(new com.nstut.simplyspeakers.commands.LegacySpeakerCommand());}
 @Mod.EventHandler public void stopping(FMLServerStoppingEvent event){com.nstut.simplyspeakers.portable.PortableSpeakerManager.shutdown(server);ServerSpeakerRegistry.flushDirty();SimplySpeakers.shutdownAudio();}
 @Mod.EventHandler public void stopped(FMLServerStoppedEvent event){ServerPlaybackManager.resetForWorld();ServerSpeakerRegistry.resetForWorld();server=null;}
 @SubscribeEvent public void blocks(RegistryEvent.Register<net.minecraft.block.Block> event){event.getRegistry().registerAll(BlockRegistries.SPEAKER.get(),BlockRegistries.PROXY_SPEAKER.get(),BlockRegistries.REDSTONE_CONTROLLER.get());}
 @SubscribeEvent public void items(RegistryEvent.Register<net.minecraft.item.Item> event){for(com.nstut.simplyspeakers.compat.RegistryValue<net.minecraft.item.Item> item:ItemRegistries.ITEM_LIST)event.getRegistry().register(item.get());}
 @SubscribeEvent public void sounds(RegistryEvent.Register<net.minecraft.util.SoundEvent> event){event.getRegistry().register(SimplySpeakers.MUSIC.get());}
 @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){if(event.phase==TickEvent.Phase.END&&server!=null){ServerPlaybackManager.serverTick(server);if(Boolean.getBoolean("simplyspeakers.legacyRuntimeAcceptance"))com.nstut.simplyspeakers.testing.LegacyRuntimeAcceptance.tick(server);if(server.getTickCounter()%6000==0)ServerSpeakerRegistry.flushDirty();}}
 @SubscribeEvent public void join(PlayerEvent.PlayerLoggedInEvent event){if(event.player instanceof EntityPlayerMP)PacketRegistries.CHANNEL.sendToPlayer((EntityPlayerMP)event.player,new SyncConfigPacketS2C(Config.speakerRange,Config.disableUpload,Config.maxUploadSize));}
 @SubscribeEvent public void quit(PlayerEvent.PlayerLoggedOutEvent event){com.nstut.packetweave.api.OutboundTransfers.shared().cancelPeer(event.player);if(SimplySpeakers.getAudioFileManager()!=null)SimplySpeakers.getAudioFileManager().cancelUploads(event.player.getUniqueID());ServerPlaybackManager.handlePlayerQuit(event.player.getServer(),event.player.getUniqueID());}
 @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event){ServerPlaybackManager.handlePlayerDimensionChange(event.player.getServer(),event.player.getUniqueID());}
 @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event){ServerPlaybackManager.handlePlayerDimensionChange(event.player.getServer(),event.player.getUniqueID());}
 private void loadConfig(java.io.File file){net.minecraftforge.common.config.Configuration cfg=new net.minecraftforge.common.config.Configuration(file);cfg.load();Config.setLocalConfig(cfg.getInt("speakerRange","general",64,1,512,"Maximum speaker range in blocks"),cfg.getBoolean("disableUpload","general",false,"Disable local audio uploads"),cfg.getInt("maxUploadSize","general",5*1024*1024,1024,100*1024*1024,"Maximum upload bytes"),cfg.getBoolean("allowRemoteStreams","general",false,"Allow public HTTP(S) audio streams; SSRF checks remain enforced"));Config.debugLogging=cfg.getBoolean("debugLogging","general",false,"Enable diagnostic logging");Config.clientCacheLimitBytes=cfg.getInt("clientCacheLimitMB","client",500,1,10240,"LRU audio cache size in MiB")*1024L*1024L;if(cfg.hasChanged())cfg.save();}
}
