package com.jaysin.beesxi.server;

import javax.annotation.Nonnull;

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
}
