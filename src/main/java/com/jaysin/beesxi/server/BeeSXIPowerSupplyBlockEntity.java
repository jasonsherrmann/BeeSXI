package com.jaysin.beesxi.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;

import com.jaysin.beesxi.BeeSXI;

public class BeeSXIPowerSupplyBlockEntity extends BlockEntity {
    private static final int POWER_SUPPLY_CAPACITY = 1_000_000;
    private static final int BATTERY_CAPACITY = 5_000_000;

    private int energy;
    private final IEnergyStorage energyStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return BeeSXIPowerSupplyBlockEntity.this.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return BeeSXIPowerSupplyBlockEntity.this.extractEnergy(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return BeeSXIPowerSupplyBlockEntity.this.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return BeeSXIPowerSupplyBlockEntity.this.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    public BeeSXIPowerSupplyBlockEntity(BlockPos pos, BlockState state) {
        super(BeeSXI.BEESXI_POWER_SUPPLY_BLOCK_ENTITY.get(), pos, state);
    }

    public int getMaxEnergyStored() {
        if (this.getBlockState().getBlock() instanceof BeeSXIPartBlock part && part.getPartType() == BeeSXIPartType.BATTERY) {
            return BATTERY_CAPACITY;
        }
        return POWER_SUPPLY_CAPACITY;
    }

    public int getEnergyStored() {
        return this.energy;
    }

    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0) {
            return 0;
        }
        int accepted = Math.min(getMaxEnergyStored() - this.energy, maxReceive);
        if (!simulate && accepted > 0) {
            this.energy += accepted;
            setChanged();
        }
        return Math.max(0, accepted);
    }

    public IEnergyStorage getEnergyStorage() {
        return this.energyStorage;
    }

    public int extractEnergy(int maxExtract, boolean simulate) {
        if (maxExtract <= 0) {
            return 0;
        }
        int extracted = Math.min(this.energy, maxExtract);
        if (!simulate && extracted > 0) {
            this.energy -= extracted;
            setChanged();
        }
        return Math.max(0, extracted);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("Energy", this.energy);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.energy = Math.max(0, Math.min(getMaxEnergyStored(), tag.getInt("Energy")));
    }
}
