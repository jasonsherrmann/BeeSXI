package com.jaysin.beesxi.blocks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.jaysin.beesxi.blockentity.BeeSXIPowerSupplyBlockEntity;
import com.jaysin.beesxi.server.BeeSXIPartType;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BeeSXIPowerBlock extends BeeSXIPartBlock implements EntityBlock {
    public BeeSXIPowerBlock(BeeSXIPartType partType, Properties properties) {
        super(partType, properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new BeeSXIPowerSupplyBlockEntity(pos, state);
    }
}
