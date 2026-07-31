package com.jaysin.beesxi.blocks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.jaysin.beesxi.blockentity.BeeSXIHddBlockEntity;
import com.jaysin.beesxi.server.BeeSXIPartType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
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

    @Override
    public BlockState playerWillDestroy(@Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState state, @Nonnull Player player) {
        if (!level.isClientSide && !player.isCreative()) {
            ItemStack drop = new ItemStack(this);
            if (level.getBlockEntity(pos) instanceof BeeSXIHddBlockEntity hdd) {
                CustomData data = CustomData.of(hdd.toItemTag(level.registryAccess()));
                drop.set(DataComponents.CUSTOM_DATA, data);
            }
            popResource(level, pos, drop);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void setPlacedBy(@Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState state, @Nullable LivingEntity placer, @Nonnull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level.getBlockEntity(pos) instanceof BeeSXIHddBlockEntity hdd)) {
            return;
        }

        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            hdd.fromItemTag(data.copyTag(), level.registryAccess());
        }
    }
}
