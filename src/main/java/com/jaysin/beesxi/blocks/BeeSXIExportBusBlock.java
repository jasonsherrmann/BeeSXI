package com.jaysin.beesxi.blocks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.jaysin.beesxi.BeeSXI;
import com.jaysin.beesxi.blockentity.BeeSXIExportBusBlockEntity;
import com.jaysin.beesxi.server.BeeSXIPartType;

public class BeeSXIExportBusBlock extends BeeSXIPartBlock implements EntityBlock {
    public BeeSXIExportBusBlock(Properties properties) {
        super(BeeSXIPartType.EXPORT_BUS, properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new BeeSXIExportBusBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@Nonnull Level level, @Nonnull BlockState state, @Nonnull BlockEntityType<T> type) {
        if (type != BeeSXI.BEESXI_EXPORT_BUS_BLOCK_ENTITY.get()) {
            return null;
        }

        return (tickLevel, tickPos, tickState, entity) -> {
            if (entity instanceof BeeSXIExportBusBlockEntity exportBus) {
                exportBus.serverTick(tickLevel, tickPos, tickState);
            }
        };
    }

    @Override
    public BlockState playerWillDestroy(@Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState state, @Nonnull Player player) {
        if (!level.isClientSide && !player.isCreative()) {
            popResource(level, pos, new ItemStack(this));
            if (level.getBlockEntity(pos) instanceof BeeSXIExportBusBlockEntity exportBus) {
                Containers.dropContents(level, pos, exportBus);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Nonnull
    @Override
    protected InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull Player player, @Nonnull BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof BeeSXIExportBusBlockEntity exportBus) {
            serverPlayer.openMenu(exportBus, exportBus::writeMenuData);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }
}