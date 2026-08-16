package com.jaysin.beesxi.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

import java.util.function.Supplier;

public class CoolantFluidBlock extends LiquidBlock {
    public CoolantFluidBlock(Supplier<? extends FlowingFluid> fluid, Properties properties) {
        super(fluid.get(), properties);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide() && entity instanceof LivingEntity livingEntity) {
            // Check if the entity is deep enough in the fluid
            if (livingEntity.getY() <= pos.getY() + 0.8) {
                // Uses generic damage, replace with custom DamageType if preferred
                if (livingEntity.getY() <= pos.getY() + 0.8) {

                DamageSource genericDamage = level.damageSources().generic();
                livingEntity.hurt(genericDamage, 2.0F); // 2.0F = 1 Heart per tick cycle
                }
            }
        }
        super.entityInside(state, level, pos, entity);
    }
}