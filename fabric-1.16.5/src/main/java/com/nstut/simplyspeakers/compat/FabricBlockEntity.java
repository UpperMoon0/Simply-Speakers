package com.nstut.simplyspeakers.compat;

import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Use Fabric's native type-checked block-entity packet and initial-chunk routes. */
public abstract class FabricBlockEntity extends LegacyBlockEntity implements BlockEntityClientSerializable {
    protected FabricBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    @Override public void fromClientTag(CompoundTag tag) { applyClientUpdate(tag); }
    @Override public CompoundTag toClientTag(CompoundTag tag) { return tag.merge(getUpdateTag()); }
}
