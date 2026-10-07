package com.nstut.simplyspeakers.blocks;

import com.mojang.logging.LogUtils;
import com.nstut.simplyspeakers.blocks.entities.BlockEntityRegistries;
import com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity;
import com.nstut.simplyspeakers.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.mojang.serialization.MapCodec;

/**
 * The Proxy Speaker block that syncs with a main speaker.
 */
public class ProxySpeakerBlock extends BaseEntityBlock {
    public static final EnumProperty<net.minecraft.core.Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty POWERED = BooleanProperty.create("powered"); 
    
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Constructs a ProxySpeakerBlock with the specified properties.
     *
     * @param properties The block properties
     */
    public ProxySpeakerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, Boolean.FALSE));
    }

    /**
     * Convenience constructor using default properties.
     */
    public ProxySpeakerBlock() {
        this(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    }

    public static final MapCodec<ProxySpeakerBlock> CODEC = simpleCodec(ProxySpeakerBlock::new);

    @Override
    public MapCodec<ProxySpeakerBlock> codec() {
        return CODEC;
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
        return new ProxySpeakerBlockEntity(pos, state);
    }
    
    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos, 
                                          @NotNull Player player, @NotNull BlockHitResult hit) {
        if (level.isClientSide()) {
            LOGGER.info("Opening proxy speaker screen at {}", pos);
            Services.CLIENT.openProxySpeakerScreen(pos);
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
        // Check if the requested type matches our proxy speaker BE type and return the serverTick method reference
        return createTickerHelper(blockEntityType, BlockEntityRegistries.PROXY_SPEAKER.get(), ProxySpeakerBlockEntity::serverTick);
    }

    // Redstone input is handled exclusively by linked Redstone Controllers.
}
