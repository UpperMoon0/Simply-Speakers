package com.nstut.simplyspeakers.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TickableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Preserve the newer lifecycle hooks without reading the world at portable routing tokens. */
public abstract class LegacyBlockEntity extends BlockEntity implements TickableBlockEntity {
    private BlockState cachedState;
    protected LegacyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type);
        this.worldPosition=pos;
        this.cachedState=state;
    }
    protected boolean usesWorldBlockState() { return true; }
    @Override public BlockState getBlockState() {
        return level == null || !usesWorldBlockState() ? cachedState : super.getBlockState();
    }
    public void setBlockState(BlockState state) { cachedState=state; }
    public void setLevel(Level level) { setLevelAndPosition(level,worldPosition); }
    @Override public void load(BlockState state, CompoundTag tag) { cachedState=state; load(tag); }
    public void load(CompoundTag tag) { super.load(cachedState,tag); }
    @Override public CompoundTag save(CompoundTag tag) { saveAdditional(tag); return tag; }
    protected void saveAdditional(CompoundTag tag) { super.save(tag); }
    // Do not call vanilla getUpdateTag here: Fabric's native mixin invokes
    // toClientTag from that method, which would recurse through derived tags.
    @Override public CompoundTag getUpdateTag() { return save(new CompoundTag()); }
    public final void applyClientUpdate(CompoundTag tag) {
        if (level == null || !level.isClientSide || tag == null) return;
        net.minecraft.resources.ResourceLocation type = net.minecraft.core.Registry.BLOCK_ENTITY_TYPE.getKey(getType());
        if (type == null || !type.toString().equals(tag.getString("id"))) return;
        if (tag.getInt("x") != worldPosition.getX() || tag.getInt("y") != worldPosition.getY()
                || tag.getInt("z") != worldPosition.getZ()) return;
        load(getBlockState(), tag);
    }
}
