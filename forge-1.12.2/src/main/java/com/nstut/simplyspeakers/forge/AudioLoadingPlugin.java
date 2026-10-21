package com.nstut.simplyspeakers.forge;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import java.util.Map;
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.Name("SimplySpeakersAudioLifecycle")
@IFMLLoadingPlugin.TransformerExclusions({"com.nstut.simplyspeakers.forge.Audio"})
public final class AudioLoadingPlugin implements IFMLLoadingPlugin {
 public String[] getASMTransformerClass(){return new String[]{"com.nstut.simplyspeakers.forge.AudioTransformer"};}
 public String getModContainerClass(){return null;}
 public String getSetupClass(){return null;}
 public void injectData(Map<String,Object> data){}
 public String getAccessTransformerClass(){return null;}
}
