package com.jaysin.beesxi.blocks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.jaysin.beesxi.blockentity.BeeSXIControllerBlockEntity;
import com.jaysin.beesxi.server.BeeSXIPartType;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class BeeSXIPartBlock extends Block {
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");

    private final BeeSXIPartType partType;

    public BeeSXIPartBlock(BeeSXIPartType partType, Properties properties) {
        super(properties);
        this.partType = partType;
        this.registerDefaultState(this.stateDefinition.any().setValue(ASSEMBLED, false));
    }

    public BeeSXIPartType getPartType() {
        return this.partType;
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ASSEMBLED);
    }

    @Override
    public BlockState playerWillDestroy(@Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState state, @Nonnull Player player) {
        if (!level.isClientSide && !player.isCreative() && !(this instanceof BeeSXIHddBlock)) {
            popResource(level, pos, new ItemStack(this));
            BeeSXIControllerBlockEntity.requestValidationNear(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void setPlacedBy(@Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState state, @Nullable LivingEntity placer, @Nonnull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            BeeSXIControllerBlockEntity.requestValidationNear(level, pos);
        }
    }
}
