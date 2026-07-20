package com.jaysin.beesxi.server;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BeeSXIHddBlock extends BeeSXIPartBlock implements EntityBlock {
    public BeeSXIHddBlock(Properties properties) {
        super(BeeSXIPartType.HDD, properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new BeeSXIHddBlockEntity(pos, state);
    }
}
