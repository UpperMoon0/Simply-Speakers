package com.nstut.simplyspeakers.blocks;

import com.mojang.logging.LogUtils;
import com.nstut.simplyspeakers.blocks.entities.BlockEntityRegistries;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The Speaker block that allows players to play audio files.
 */
public class SpeakerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty POWERED = BooleanProperty.create("powered"); 
    
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Constructs a SpeakerBlock with the specified properties.
     *
     * @param properties The block properties
     */
    public SpeakerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, Boolean.FALSE));
    }

    /**
     * Convenience constructor using default properties.
     */
    public SpeakerBlock() {
        this(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockStateBuilder) {
        blockStateBuilder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState pState) {
        return RenderShape.MODEL;
    }

    @Override
    public @NotNull BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new SpeakerBlockEntity(pos, state);
    }    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, Level level, @NotNull BlockPos pos, 
                                          @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide) {
            LOGGER.info("Opening speaker screen at {}", pos);
            Services.CLIENT.openSpeakerScreen(pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, 
                                                                 @NotNull BlockEntityType<T> blockEntityType) {
        // Provide the server-side ticker
        if (level.isClientSide()) {
            return null; // No client ticker needed
        }
        // Check if the requested type matches our speaker BE type and return the serverTick method reference
        return createTickerHelper(blockEntityType, BlockEntityRegistries.SPEAKER.get(), SpeakerBlockEntity::serverTick);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, 
                                @NotNull Block block, @NotNull BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);

        if (!level.isClientSide) {
            boolean currentPower = state.getValue(POWERED);
            boolean hasSignal = level.hasNeighborSignal(pos);

            LOGGER.debug("NeighborChanged at {}: currentPower={}, hasSignal={}", pos, currentPower, hasSignal);

            if (currentPower != hasSignal) {
                LOGGER.info("Power state changed at {}: {} -> {}", pos, currentPower, hasSignal);
                // Update the block state first
                level.setBlock(pos, state.setValue(POWERED, hasSignal), 3);

                // Then trigger audio based on the new state
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof SpeakerBlockEntity speakerEntity) {
                    if (hasSignal) {
                        LOGGER.info("Triggering playAudio for speaker at {}", pos);
                        speakerEntity.playAudio();
                    } else {
                        LOGGER.info("Triggering stopAudio for speaker at {}", pos);
                        speakerEntity.stopAudio();
                    }
                } else {
                    LOGGER.warn("No SpeakerBlockEntity found at {} after power change.", pos);
                }
            }
        }
    }
}
