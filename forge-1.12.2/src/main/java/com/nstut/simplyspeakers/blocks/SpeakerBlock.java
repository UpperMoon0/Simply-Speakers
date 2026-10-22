package com.nstut.simplyspeakers.blocks;
public final class SpeakerBlock extends com.nstut.simplyspeakers.compat.LegacySpeakerBlock {
 public SpeakerBlock(){super("speaker");}
 public net.minecraft.tileentity.TileEntity createNewTileEntity(net.minecraft.world.World world,int meta){return new com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity(net.minecraft.util.math.BlockPos.ORIGIN,getStateFromMeta(meta));}
 public boolean onBlockActivated(net.minecraft.world.World world,net.minecraft.util.math.BlockPos pos,net.minecraft.block.state.IBlockState state,net.minecraft.entity.player.EntityPlayer player,net.minecraft.util.EnumHand hand,net.minecraft.util.EnumFacing side,float x,float y,float z){if(world.isRemote)com.nstut.simplyspeakers.platform.Services.CLIENT.openSpeakerScreen(pos);return true;}
 public boolean hasComparatorInputOverride(net.minecraft.block.state.IBlockState state){return true;}
 public int getComparatorInputOverride(net.minecraft.block.state.IBlockState state,net.minecraft.world.World world,net.minecraft.util.math.BlockPos pos){net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);return tile instanceof com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity?((com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity)tile).getComparatorOutput():0;}
}
