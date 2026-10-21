package com.nstut.simplyspeakers.compat;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
/** Native persistence and update packets; portable endpoints never read their routing token as a block. */
public abstract class LegacyBlockEntity extends TileEntity implements ITickable {
 private IBlockState cachedState;
 protected LegacyBlockEntity(LegacyTileType<?> type, BlockPos pos,IBlockState state) { this.pos=pos;cachedState=state; }
 public IBlockState getBlockState() { return world==null || this instanceof com.nstut.simplyspeakers.portable.PortableSpeakerEndpoint ? cachedState : world.getBlockState(pos); }
 public void setBlockState(IBlockState state) { cachedState=state; }
 public void load(NBTTagCompound tag) { super.readFromNBT(tag); }
 protected void saveAdditional(NBTTagCompound tag) { super.writeToNBT(tag); }
 @Override public void readFromNBT(NBTTagCompound tag) { load(tag); }
 @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) { saveAdditional(tag);return tag; }
 @Override public void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet) { handleUpdateTag(packet.getNbtCompound()); }
 @Override public void handleUpdateTag(NBTTagCompound tag) { load(tag); }
 @Override public boolean shouldRefresh(net.minecraft.world.World world,BlockPos pos,IBlockState oldState,IBlockState newState) { cachedState=newState;return oldState.getBlock()!=newState.getBlock(); }
}
