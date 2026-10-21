package com.nstut.simplyspeakers.blocks;
public final class RedstoneControllerBlock extends com.nstut.simplyspeakers.compat.LegacySpeakerBlock {
 public RedstoneControllerBlock(){super("redstone_controller");}
 public net.minecraft.tileentity.TileEntity createNewTileEntity(net.minecraft.world.World world,int meta){return new com.nstut.simplyspeakers.blocks.entities.RedstoneControllerBlockEntity(net.minecraft.util.math.BlockPos.ORIGIN,getStateFromMeta(meta));}
 public boolean onBlockActivated(net.minecraft.world.World world,net.minecraft.util.math.BlockPos pos,net.minecraft.block.state.IBlockState state,net.minecraft.entity.player.EntityPlayer player,net.minecraft.util.EnumHand hand,net.minecraft.util.EnumFacing side,float x,float y,float z){if(world.isRemote)com.nstut.simplyspeakers.platform.Services.CLIENT.openRedstoneControllerScreen(pos);return true;}

}
