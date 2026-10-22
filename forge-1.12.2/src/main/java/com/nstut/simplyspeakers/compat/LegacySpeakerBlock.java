package com.nstut.simplyspeakers.compat;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.*;
import net.minecraft.block.state.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.nstut.simplyspeakers.blocks.entities.*;
/** Genuine BlockContainer with persistent 1.12 metadata and native tile ticking. */
public abstract class LegacySpeakerBlock extends BlockContainer {
 public static final PropertyDirection FACING=BlockHorizontal.FACING;
 public static final PropertyBool POWERED=PropertyBool.create("powered");
 protected LegacySpeakerBlock(String id){super(Material.IRON);setRegistryName("simplyspeakers",id);setTranslationKey("simplyspeakers."+id);setHardness(5);setResistance(6);setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(POWERED,false));setCreativeTab(com.nstut.simplyspeakers.SimplySpeakers.CREATIVE_TAB);}
 protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,POWERED);}
 public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(FACING,EnumFacing.byHorizontalIndex(meta&3)).withProperty(POWERED,(meta&4)!=0);}
 public int getMetaFromState(IBlockState state){return state.getValue(FACING).getHorizontalIndex()|(state.getValue(POWERED)?4:0);}
 public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase placer,EnumHand hand){return getDefaultState().withProperty(FACING,placer.getHorizontalFacing().getOpposite());}
 public EnumBlockRenderType getRenderType(IBlockState state){return EnumBlockRenderType.MODEL;}
 public boolean isOpaqueCube(IBlockState state){return false;}
 public boolean isFullCube(IBlockState state){return false;}
 public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,EntityLivingBase placer,ItemStack stack){if(placer instanceof EntityPlayer && world.getTileEntity(pos) instanceof RedstoneControllerBlockEntity)((RedstoneControllerBlockEntity)world.getTileEntity(pos)).claim(placer.getUniqueID());}
 public void breakBlock(World world,BlockPos pos,IBlockState state){TileEntity tile=world.getTileEntity(pos);if(!world.isRemote){if(tile instanceof SpeakerBlockEntity){SpeakerBlockEntity speaker=(SpeakerBlockEntity)tile;speaker.detachEmitterForPowerOff();com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.unregisterSpeaker(world,pos,speaker.getStateKey());}else if(tile instanceof ProxySpeakerBlockEntity){ProxySpeakerBlockEntity proxy=(ProxySpeakerBlockEntity)tile;proxy.stopAudio();com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry.unregisterProxySpeaker(world,pos,proxy.getSpeakerId());}}super.breakBlock(world,pos,state);}
 public void neighborChanged(IBlockState state,World world,BlockPos pos,Block block,BlockPos from){TileEntity tile=world.getTileEntity(pos);if(!world.isRemote&&tile instanceof RedstoneControllerBlockEntity)((RedstoneControllerBlockEntity)tile).observeSignal(world.getRedstonePowerFromNeighbors(pos));}
}
