package com.nstut.simplyspeakers.blocks.entities;
import com.nstut.simplyspeakers.compat.LegacyTileType;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraft.util.ResourceLocation;
public final class BlockEntityRegistries {
 public static final LegacyTileType<SpeakerBlockEntity> SPEAKER=new LegacyTileType<>(SpeakerBlockEntity.class);
 public static final LegacyTileType<ProxySpeakerBlockEntity> PROXY_SPEAKER=new LegacyTileType<>(ProxySpeakerBlockEntity.class);
 public static final LegacyTileType<RedstoneControllerBlockEntity> REDSTONE_CONTROLLER=new LegacyTileType<>(RedstoneControllerBlockEntity.class);
 public static void init(){GameRegistry.registerTileEntity(SpeakerBlockEntity.class,new ResourceLocation("simplyspeakers","speaker"));GameRegistry.registerTileEntity(ProxySpeakerBlockEntity.class,new ResourceLocation("simplyspeakers","proxy_speaker"));GameRegistry.registerTileEntity(RedstoneControllerBlockEntity.class,new ResourceLocation("simplyspeakers","redstone_controller"));}
}
