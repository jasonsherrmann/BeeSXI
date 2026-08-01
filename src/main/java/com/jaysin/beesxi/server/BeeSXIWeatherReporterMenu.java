package com.jaysin.beesxi.server;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.jaysin.beesxi.BeeSXI;
import com.jaysin.beesxi.blockentity.BeeSXIWeatherReporterBlockEntity;

public class BeeSXIWeatherReporterMenu extends AbstractContainerMenu {
    private static final int MACHINE_SLOT_COUNT = 2;
    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;
    private static final int PLAYER_INV_START = MACHINE_SLOT_COUNT;

    private final BeeSXIWeatherReporterBlockEntity weatherReporter;
    private final ContainerData data;

    public BeeSXIWeatherReporterMenu(int containerId, Inventory playerInventory, BeeSXIWeatherReporterBlockEntity weatherReporter) {
        super(BeeSXI.BEESXI_WEATHER_REPORTER_MENU.get(), containerId);
        this.weatherReporter = weatherReporter;
        this.data = weatherReporter.getContainerData();

        this.addSlot(new Slot(weatherReporter, 0, 77, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return weatherReporter.canPlaceItem(0, stack);
            }
        });
        this.addSlot(new Slot(weatherReporter, 1, 125, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        int left = 8;
        int top = 117;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, left + col * 18, top + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, left + col * 18, top + 58));
        }

        addDataSlots(this.data);
    }

    public static BeeSXIWeatherReporterMenu fromNetwork(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        if (!(playerInventory.player.level().getBlockEntity(pos) instanceof BeeSXIWeatherReporterBlockEntity weatherReporter)) {
            throw new IllegalStateException("Missing BeeSXI weather reporter block entity at " + pos);
        }
        return new BeeSXIWeatherReporterMenu(containerId, playerInventory, weatherReporter);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.weatherReporter.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot sourceSlot = this.slots.get(index);
        if (!sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copy = sourceStack.copy();

        if (index == INPUT_SLOT || index == OUTPUT_SLOT) {
            if (!this.moveItemStackTo(sourceStack, PLAYER_INV_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (sourceStack.is(Items.PAPER)) {
                if (!this.moveItemStackTo(sourceStack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (sourceStack.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }

        sourceSlot.onTake(player, sourceStack);
        return copy;
    }

    public boolean isProcessing() {
        return this.data.get(0) == 1;
    }

    public int getProgressTicks() {
        return this.data.get(1);
    }

    public int getEnergyStored() {
        return this.data.get(2);
    }

    public int getEnergyCapacity() {
        return this.data.get(3);
    }

    public ResourceLocation getCurrentBiome() {
        return this.weatherReporter.getCurrentBiome();
    }

    public int getProgressPercent() {
        if (this.getProgressTicks() <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, (int) ((this.getProgressTicks() * 100L) / (20L * 60L * 10L))));
    }
}
