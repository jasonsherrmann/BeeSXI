package com.jaysin.beesxi.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.inventory.ContainerData;

import net.neoforged.neoforge.energy.IEnergyStorage;

import com.jaysin.beesxi.BeeSXI;
import com.jaysin.beesxi.server.BeeSXIWeatherReporterMenu;

public class BeeSXIWeatherReporterBlockEntity extends BlockEntity implements Container, MenuProvider {
    private static final int SIZE = 2;
    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;
    private static final int REPORT_DURATION_TICKS = 20 * 60 * 10;
    private static final int REPORT_TOTAL_RF_COST = 100_000;
    private static final int ENERGY_CAPACITY = 100_000;
    private static final String WEATHER_BIOME_KEY = "BeeSXIWeatherReportBiome";

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private boolean processing;
    private int progressTicks;
    private int consumedEnergy;
    private int energy;
    private ResourceLocation pendingBiome;

    private final IEnergyStorage energyStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return BeeSXIWeatherReporterBlockEntity.this.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return BeeSXIWeatherReporterBlockEntity.this.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return BeeSXIWeatherReporterBlockEntity.this.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    private final ContainerData containerData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> BeeSXIWeatherReporterBlockEntity.this.processing ? 1 : 0;
                case 1 -> BeeSXIWeatherReporterBlockEntity.this.progressTicks;
                case 2 -> BeeSXIWeatherReporterBlockEntity.this.energy;
                case 3 -> ENERGY_CAPACITY;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> BeeSXIWeatherReporterBlockEntity.this.processing = value != 0;
                case 1 -> BeeSXIWeatherReporterBlockEntity.this.progressTicks = Math.max(0, Math.min(REPORT_DURATION_TICKS, value));
                case 2 -> BeeSXIWeatherReporterBlockEntity.this.energy = Math.max(0, Math.min(ENERGY_CAPACITY, value));
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public BeeSXIWeatherReporterBlockEntity(BlockPos pos, BlockState state) {
        super(BeeSXI.BEESXI_WEATHER_REPORTER_BLOCK_ENTITY.get(), pos, state);
    }

    public IEnergyStorage getEnergyStorage() {
        return this.energyStorage;
    }

    public ContainerData getContainerData() {
        return this.containerData;
    }

    public boolean isProcessing() {
        return this.processing;
    }

    public int getProgressPercent() {
        return Math.max(0, Math.min(100, (int) ((this.progressTicks * 100L) / REPORT_DURATION_TICKS)));
    }

    public int getEnergyStored() {
        return this.energy;
    }

    public int getMaxEnergyStored() {
        return ENERGY_CAPACITY;
    }

    public ResourceLocation getCurrentBiome() {
        if (this.level == null) {
            return null;
        }
        return this.level.getBiome(this.worldPosition)
            .unwrapKey()
            .map(ResourceKey::location)
            .orElse(null);
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

    private void resetProcess() {
        this.processing = false;
        this.progressTicks = 0;
        this.consumedEnergy = 0;
        this.pendingBiome = null;
    }

    public void processScheduledTick(Level level, BlockPos pos, BlockState state, int elapsedTicks) {
        if (level.isClientSide) {
            return;
        }

        ItemStack inputStack = this.items.get(INPUT_SLOT);
        ItemStack outputStack = this.items.get(OUTPUT_SLOT);

        if (!this.processing) {
            if (inputStack.isEmpty() || !inputStack.is(Items.PAPER) || isWeatherReport(inputStack) || !outputStack.isEmpty()) {
                resetProcess();
                return;
            }

            ResourceLocation biomeAtPosition = level.getBiome(pos)
                .unwrapKey()
                .map(ResourceKey::location)
                .orElse(null);
            if (biomeAtPosition == null) {
                resetProcess();
                return;
            }

            inputStack.shrink(1);
            if (inputStack.isEmpty()) {
                this.items.set(INPUT_SLOT, ItemStack.EMPTY);
            }
            this.pendingBiome = biomeAtPosition;
            this.processing = true;
            this.progressTicks = 0;
            this.consumedEnergy = 0;
            setChanged();
            return;
        }

        if (!outputStack.isEmpty()) {
            return;
        }

        if (this.pendingBiome == null) {
            resetProcess();
            return;
        }

        int deltaTicks = Math.max(1, elapsedTicks);
        int remainingTicks = REPORT_DURATION_TICKS - this.progressTicks;
        if (remainingTicks <= 0) {
            remainingTicks = 1;
        }
        int stepTicks = Math.min(deltaTicks, remainingTicks);

        int targetConsumed = (int) (((long) (this.progressTicks + stepTicks) * REPORT_TOTAL_RF_COST + REPORT_DURATION_TICKS - 1L) / REPORT_DURATION_TICKS);
        int requiredForStep = Math.max(0, targetConsumed - this.consumedEnergy);

        if (requiredForStep > 0) {
            int available = Math.min(requiredForStep, this.energy);
            if (available < requiredForStep) {
                return;
            }
            this.energy -= requiredForStep;
            this.consumedEnergy += requiredForStep;
        }

        this.progressTicks += stepTicks;
        if (this.progressTicks < REPORT_DURATION_TICKS) {
            setChanged();
            return;
        }

        ItemStack output = new ItemStack(Items.PAPER);
        CompoundTag tag = new CompoundTag();
        tag.putString(WEATHER_BIOME_KEY, this.pendingBiome.toString());
        output.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        output.set(DataComponents.CUSTOM_NAME, Component.literal("Weather Report: " + this.pendingBiome.getPath()));
        this.items.set(OUTPUT_SLOT, output);

        resetProcess();
        setChanged();
    }

    private boolean isWeatherReport(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return false;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains(WEATHER_BIOME_KEY, Tag.TAG_STRING);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        ContainerHelper.saveAllItems(tag, this.items, provider);
        tag.putBoolean("Processing", this.processing);
        tag.putInt("ProgressTicks", this.progressTicks);
        tag.putInt("ConsumedEnergy", this.consumedEnergy);
        tag.putInt("Energy", this.energy);
        tag.putString("PendingBiome", this.pendingBiome == null ? "" : this.pendingBiome.toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items, provider);
        this.processing = tag.getBoolean("Processing");
        this.progressTicks = Math.max(0, tag.getInt("ProgressTicks"));
        this.consumedEnergy = Math.max(0, Math.min(REPORT_TOTAL_RF_COST, tag.getInt("ConsumedEnergy")));
        this.energy = Math.max(0, Math.min(ENERGY_CAPACITY, tag.getInt("Energy")));
        this.pendingBiome = ResourceLocation.tryParse(tag.getString("PendingBiome"));
        if (!this.processing || this.progressTicks <= 0 || this.pendingBiome == null) {
            resetProcess();
        }
    }

    public void writeMenuData(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.worldPosition);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.beesxi.beesxi_weather_reporter");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BeeSXIWeatherReporterMenu(containerId, playerInventory, this);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = ContainerHelper.removeItem(this.items, slot, amount);
        if (!stack.isEmpty()) {
            setChanged();
        }
        return stack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SIZE) {
            return;
        }
        if (slot == INPUT_SLOT && stack.getCount() > 1) {
            stack.setCount(1);
        }
        this.items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT_SLOT && stack.is(Items.PAPER) && !isWeatherReport(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < this.items.size(); i++) {
            this.items.set(i, ItemStack.EMPTY);
        }
        resetProcess();
        setChanged();
    }
}
