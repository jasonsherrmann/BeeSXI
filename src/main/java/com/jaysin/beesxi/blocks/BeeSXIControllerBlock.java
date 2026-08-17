package com.jaysin.beesxi.blocks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import java.util.List;
import net.minecraft.world.item.Item;

import com.jaysin.beesxi.BeeSXI;
import com.jaysin.beesxi.blockentity.BeeSXIControllerBlockEntity;

public class BeeSXIControllerBlock extends Block implements EntityBlock {
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");

    public BeeSXIControllerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ASSEMBLED, false));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new BeeSXIControllerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        @Nonnull Level level,
        @Nonnull BlockState state,
        @Nonnull BlockEntityType<T> type
    ) {
        if (type != BeeSXI.BEESXI_CONTROLLER_BLOCK_ENTITY.get()) {
            return null;
        }

        return (tickLevel, tickPos, tickState, entity) -> {
            if (entity instanceof BeeSXIControllerBlockEntity controller) {
                controller.serverTick(tickLevel, tickPos, tickState);
            }
        };
    }

    @Nonnull
    @Override
    protected InteractionResult useWithoutItem(
        @Nonnull BlockState state,
        @Nonnull Level level,
        @Nonnull BlockPos pos,
        @Nonnull Player player,
        @Nonnull BlockHitResult hitResult
    ) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof BeeSXIControllerBlockEntity controller) {
            if (!controller.isFormed()) {
                controller.sendStructureDiagnosticsTo(serverPlayer);
            }
            serverPlayer.openMenu(controller, controller::writeMenuData);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ASSEMBLED);
    }

    private static ItemStack createControllerDrop(Level level, BlockPos pos) {
        ItemStack stack = new ItemStack(BeeSXI.BEESXI_CONTROLLER_ITEM.get());
        if (level.getBlockEntity(pos) instanceof BeeSXIControllerBlockEntity controller) {
            CompoundTag tag = controller.saveWithFullMetadata(level.registryAccess());
            if (!tag.isEmpty()) {
                BlockItem.setBlockEntityData(stack, BeeSXI.BEESXI_CONTROLLER_BLOCK_ENTITY.get(), tag);
            }
        }
        return stack;
    }

    @Override
    public void onRemove(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            if (level.getBlockEntity(pos) instanceof BeeSXIControllerBlockEntity controller) {
                controller.clearAssembledStateForConnectedStructure();
                Containers.dropContents(level, pos, controller);
                if (!level.isClientSide) {
                    popResource(level, pos, createControllerDrop(level, pos));
                }
            }
            BeeSXIControllerBlockEntity.requestValidationNear(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public BlockState playerWillDestroy(@Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState state, @Nonnull Player player) {
        if (!level.isClientSide && !player.isCreative()) {
            popResource(level, pos, createControllerDrop(level, pos));
            if (level.getBlockEntity(pos) instanceof BeeSXIControllerBlockEntity controller) {
                Containers.dropContents(level, pos, controller);
            }
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
