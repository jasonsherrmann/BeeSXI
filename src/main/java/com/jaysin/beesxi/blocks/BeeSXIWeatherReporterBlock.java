package com.jaysin.beesxi.blocks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.jaysin.beesxi.blockentity.BeeSXIWeatherReporterBlockEntity;

public class BeeSXIWeatherReporterBlock extends Block implements EntityBlock {
    private static final int TICK_INTERVAL = 20;

    public BeeSXIWeatherReporterBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new BeeSXIWeatherReporterBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, TICK_INTERVAL);
        }
    }

    @Override
    protected void tick(@Nonnull BlockState state, @Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull RandomSource random) {
        if (level.getBlockEntity(pos) instanceof BeeSXIWeatherReporterBlockEntity reporter) {
            reporter.processScheduledTick(level, pos, state, TICK_INTERVAL);
        }
        level.scheduleTick(pos, this, TICK_INTERVAL);
    }
}
