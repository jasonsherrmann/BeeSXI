package com.jaysin.beesxi.server;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.jaysin.beesxi.BeeSXI;
import com.jaysin.beesxi.blockentity.BeeSXIWeatherReporterBlockEntity;

public class BeeSXIWeatherReporterMenu extends AbstractContainerMenu {
    private final BeeSXIWeatherReporterBlockEntity weatherReporter;

    public BeeSXIWeatherReporterMenu(int containerId, Inventory playerInventory, BeeSXIWeatherReporterBlockEntity weatherReporter) {
        super(BeeSXI.BEESXI_WEATHER_REPORTER_MENU.get(), containerId);
        this.weatherReporter = weatherReporter;

        this.addSlot(new Slot(weatherReporter, 0, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.PAPER);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
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

        if (index == 0) {
            if (!this.moveItemStackTo(sourceStack, 1, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!this.moveItemStackTo(sourceStack, 0, 1, false)) {
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
}
