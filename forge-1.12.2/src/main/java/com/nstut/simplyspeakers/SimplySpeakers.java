package com.nstut.simplyspeakers;
import com.nstut.simplyspeakers.audio.AudioFileManager;
import com.nstut.simplyspeakers.network.*;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.ResourceLocation;
public final class SimplySpeakers {
 public static final String MOD_ID="simplyspeakers";
 public static final org.apache.logging.log4j.Logger LOGGER=org.apache.logging.log4j.LogManager.getLogger(MOD_ID);
 public static final CreativeTabs CREATIVE_TAB=new CreativeTabs(MOD_ID+".tab"){public ItemStack createIcon(){return new ItemStack(com.nstut.simplyspeakers.items.ItemRegistries.SPEAKER.get());}};
 public static final ResourceLocation MUSIC_SOUND_ID=new ResourceLocation(MOD_ID,"custom.music");
 public static final com.nstut.simplyspeakers.compat.RegistryValue<SoundEvent> MUSIC=new com.nstut.simplyspeakers.compat.RegistryValue<>(new SoundEvent(MUSIC_SOUND_ID).setRegistryName(MUSIC_SOUND_ID));
 private static AudioFileManager audioFileManager;
 public static void initializeAudio(java.nio.file.Path path){shutdownAudio();audioFileManager=new AudioFileManager(path);}
 public static void shutdownAudio(){if(audioFileManager!=null){audioFileManager.shutdown();audioFileManager=null;}}
 public static AudioFileManager getAudioFileManager(){return audioFileManager;}
 public static void broadcastConfig(net.minecraft.server.MinecraftServer server){if(server!=null)PacketRegistries.CHANNEL.sendToPlayers(server.getPlayerList().getPlayers(),new SyncConfigPacketS2C(Config.speakerRange,Config.disableUpload,Config.maxUploadSize));}
}
