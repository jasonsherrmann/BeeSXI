package com.jaysin.beesxi.server;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.jaysin.beesxi.BeeSXI;

public class BeeSXIExportBusMenu extends AbstractContainerMenu {
    private static final int INVENTORY_SLOTS = BeeSXIExportBusBlockEntity.INVENTORY_SIZE;
    private static final int FILTER_SLOTS = BeeSXIExportBusBlockEntity.FILTER_SIZE;

    private static final int BUS_INV_START = 0;
    private static final int BUS_INV_END = BUS_INV_START + INVENTORY_SLOTS;
    private static final int FILTER_START = BUS_INV_END;
    private static final int FILTER_END = FILTER_START + FILTER_SLOTS;
    private static final int PLAYER_START = FILTER_END;

    private final BeeSXIExportBusBlockEntity exportBus;

    public BeeSXIExportBusMenu(int containerId, Inventory playerInventory, BeeSXIExportBusBlockEntity exportBus) {
        super(BeeSXI.BEESXI_EXPORT_BUS_MENU.get(), containerId);
        this.exportBus = exportBus;

        addBusInventorySlots();
        addFilterSlots();
        addPlayerSlots(playerInventory);
    }

    public static BeeSXIExportBusMenu fromNetwork(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        if (!(playerInventory.player.level().getBlockEntity(pos) instanceof BeeSXIExportBusBlockEntity exportBus)) {
            throw new IllegalStateException("Missing BeeSXI export bus block entity at " + pos);
        }
        return new BeeSXIExportBusMenu(containerId, playerInventory, exportBus);
    }

    private void addBusInventorySlots() {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new OutputSlot(this.exportBus, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }
    }

    private void addFilterSlots() {
        int index = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new FilterSlot(index++, 8 + col * 18, 86 + row * 18));
            }
        }
    }

    private void addPlayerSlots(Inventory playerInventory) {
        int top = 154;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, top + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, top + 58));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.exportBus.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot sourceSlot = this.slots.get(index);
        if (!sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copy = sourceStack.copy();

        if (index < BUS_INV_END) {
            if (!this.moveItemStackTo(sourceStack, PLAYER_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < FILTER_END) {
            return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (sourceStack.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }

        sourceSlot.onTake(player, sourceStack);
        return copy;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= FILTER_START && slotId < FILTER_END) {
            handleGhostFilterClick(slotId - FILTER_START, button, clickType, player);
            return;
        }

        super.clicked(slotId, button, clickType, player);
    }

    public BeeSXIExportBusBlockEntity getExportBus() {
        return this.exportBus;
    }

    private void handleGhostFilterClick(int filterIndex, int button, ClickType clickType, Player player) {
        if (filterIndex < 0 || filterIndex >= FILTER_SLOTS) {
            return;
        }

        ItemStack template = ItemStack.EMPTY;
        if (clickType == ClickType.SWAP && button >= 0 && button < 9) {
            template = player.getInventory().getItem(button);
        } else if (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_CRAFT || clickType == ClickType.CLONE) {
            template = getCarried();
        }

        if (template.isEmpty()) {
            this.exportBus.setFilterItem(filterIndex, ItemStack.EMPTY);
        } else {
            this.exportBus.setFilterItem(filterIndex, template.copyWithCount(1));
        }

        broadcastChanges();
    }

    private final class FilterSlot extends Slot {
        private final int filterIndex;

        private FilterSlot(int filterIndex, int x, int y) {
            super(new SimpleContainer(FILTER_SLOTS), filterIndex, x, y);
            this.filterIndex = filterIndex;
        }

        @Override
        public ItemStack getItem() {
            return BeeSXIExportBusMenu.this.exportBus.getFilterItem(this.filterIndex);
        }

        @Override
        public boolean hasItem() {
            return !getItem().isEmpty();
        }

        @Override
        public void set(ItemStack stack) {
            BeeSXIExportBusMenu.this.exportBus.setFilterItem(this.filterIndex, stack);
            setChanged();
        }

        @Override
        public ItemStack remove(int amount) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }

    private static final class OutputSlot extends Slot {
        private OutputSlot(BeeSXIExportBusBlockEntity exportBus, int slot, int x, int y) {
            super(exportBus, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}