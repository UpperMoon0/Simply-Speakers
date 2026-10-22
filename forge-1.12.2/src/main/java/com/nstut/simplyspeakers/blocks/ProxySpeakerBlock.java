package com.nstut.simplyspeakers.blocks;
public final class ProxySpeakerBlock extends com.nstut.simplyspeakers.compat.LegacySpeakerBlock {
 public ProxySpeakerBlock(){super("proxy_speaker");}
 public net.minecraft.tileentity.TileEntity createNewTileEntity(net.minecraft.world.World world,int meta){return new com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity(net.minecraft.util.math.BlockPos.ORIGIN,getStateFromMeta(meta));}
 public boolean onBlockActivated(net.minecraft.world.World world,net.minecraft.util.math.BlockPos pos,net.minecraft.block.state.IBlockState state,net.minecraft.entity.player.EntityPlayer player,net.minecraft.util.EnumHand hand,net.minecraft.util.EnumFacing side,float x,float y,float z){if(world.isRemote)com.nstut.simplyspeakers.platform.Services.CLIENT.openProxySpeakerScreen(pos);return true;}

}
