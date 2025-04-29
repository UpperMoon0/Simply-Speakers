package com.nstut.simplyspeakers.blocks;

import com.mojang.logging.LogUtils;
import com.nstut.simplyspeakers.client.screens.SpeakerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty; // Import BooleanProperty
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.NotNull;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import com.nstut.simplyspeakers.blocks.entities.BlockEntityRegistries; // Added import
import org.jetbrains.annotations.Nullable; // Added import

public class SpeakerBlock extends BaseEntityBlock { // Extend BaseEntityBlock for convenience

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty POWERED = BooleanProperty.create("powered"); // Add POWERED state

    private static final Logger LOGGER = LogUtils.getLogger();

    public SpeakerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, Boolean.FALSE)); // Default POWERED to false
    }

    // Convenience constructor using default properties
    public SpeakerBlock() {
        this(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion());
    }


    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockStateBuilder) {
        blockStateBuilder.add(FACING, POWERED); // Add both properties
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite()); // Set facing direction when placed
    }

    @SuppressWarnings("deprecation")
    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState pState) {
        return RenderShape.MODEL;
    }

    @Override
    public @NotNull BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new SpeakerBlockEntity(pos, state);
    }

    @SuppressWarnings("deprecation")
    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide) {
            Minecraft.getInstance().setScreen(new SpeakerScreen(pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable // Ticker can be null
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> blockEntityType) {
        // Provide the server-side ticker
        if (level.isClientSide()) {
            return null; // No client ticker needed
        }
        // Check if the requested type matches our speaker BE type and return the serverTick method reference
        return createTickerHelper(blockEntityType, BlockEntityRegistries.SPEAKER.get(), SpeakerBlockEntity::serverTick);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);

        if (!level.isClientSide) {
            boolean currentPower = state.getValue(POWERED);
            boolean hasSignal = level.hasNeighborSignal(pos);

            if (currentPower != hasSignal) {
                // Update the block state first
                level.setBlock(pos, state.setValue(POWERED, hasSignal), 3);

                // Then trigger audio based on the new state
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof SpeakerBlockEntity speakerEntity) {
                    if (hasSignal) {
                        speakerEntity.playAudio();
                    } else {
                        speakerEntity.stopAudio();
                    }
                }
            }
        }
    }
}
