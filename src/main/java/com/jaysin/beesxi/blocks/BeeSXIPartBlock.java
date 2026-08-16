package com.jaysin.beesxi.blocks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.jaysin.beesxi.blockentity.BeeSXIControllerBlockEntity;
import com.jaysin.beesxi.server.BeeSXIPartType;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.network.chat.Component;
import java.util.List;
import net.minecraft.world.item.Item;

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
    public void onRemove(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            BeeSXIControllerBlockEntity.requestValidationNear(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public BlockState playerWillDestroy(@Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState state, @Nonnull Player player) {
        if (!level.isClientSide && !player.isCreative()) {
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
        
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
    if (Screen.hasShiftDown()) {
        // Text shown ONLY when holding SHIFT
        tooltipComponents.add(Component.literal("§ePart of the BeeSXI system."));
        tooltipComponents.add(Component.literal("§7Parts Required:"));
        tooltipComponents.add(Component.literal("§71x Controller, >1x Power Supply,"));
        tooltipComponents.add(Component.literal("§71x Molecular Analyzer,"));
        tooltipComponents.add(Component.literal("§7>1x RAM, >1x CPU,"));
        tooltipComponents.add(Component.literal("§7All edges must be Casings"));
        tooltipComponents.add(Component.literal("§73-15 blocks each side"));

    } else {
        // Text shown by default
        tooltipComponents.add(Component.literal("§7Hold §eSHIFT§7 for details."));
    }
    
    super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
}
}
