package com.jaysin.beesxi.server;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.jaysin.beesxi.BeeSXI;
import com.jaysin.beesxi.blockentity.BeeSXIControllerBlockEntity;

public class BeeSXIServerMenu extends AbstractContainerMenu {
    private static final int NETWORK_SLOT_COUNT = 27;

    private static final int ANALYZE_SLOT_INDEX = 0;
    private static final int NETWORK_INV_START = 1;
    private static final int NETWORK_INV_END = NETWORK_INV_START + NETWORK_SLOT_COUNT;

    private static final int PLAYER_INV_START = NETWORK_INV_END;

    private final BeeSXIControllerBlockEntity controller;
    private final ContainerData data;

    public BeeSXIServerMenu(int containerId, Inventory playerInventory, BeeSXIControllerBlockEntity controller) {
        super(BeeSXI.BEESXI_SERVER_MENU.get(), containerId);
        this.controller = controller;
        this.data = controller.getContainerData();

        addControllerSlots();
        addPlayerSlots(playerInventory);
        addDataSlots(this.data);
    }

    public static BeeSXIServerMenu fromNetwork(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        if (!(playerInventory.player.level().getBlockEntity(pos) instanceof BeeSXIControllerBlockEntity controller)) {
            throw new IllegalStateException("Missing BeeSXI controller block entity at " + pos);
        }
        return new BeeSXIServerMenu(containerId, playerInventory, controller);
    }

    private void addControllerSlots() {
        this.addSlot(new TabSlot(this.controller, ANALYZE_SLOT_INDEX, 108, 62, BeeSXIControllerBlockEntity.TAB_ANALYSIS));

        int index = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new TabSlot(this.controller, NETWORK_INV_START + index++, 123 + col * 18, 45 + row * 18, BeeSXIControllerBlockEntity.TAB_INVENTORY));
            }
        }
    }

    private void addPlayerSlots(Inventory playerInventory) {
        int left = 125;
        int top = 189;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, left + col * 18, top + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, left + col * 18, top + 58));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        boolean handled = this.controller.clickMenuButton(player, id);
        if (handled) {
            this.broadcastChanges();
        }
        return handled;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.controller.stillValid(player);
    }

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        Slot sourceSlot = this.slots.get(index);
        if (!sourceSlot.hasItem() || !sourceSlot.isActive()) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }

        net.minecraft.world.item.ItemStack sourceStack = sourceSlot.getItem();
        net.minecraft.world.item.ItemStack sourceCopy = sourceStack.copy();

        if (index < PLAYER_INV_START) {
            if (!this.moveItemStackTo(sourceStack, PLAYER_INV_START, this.slots.size(), true)) {
                return net.minecraft.world.item.ItemStack.EMPTY;
            }
        } else {
            if (this.getActiveTab() == BeeSXIControllerBlockEntity.TAB_ANALYSIS) {
                if (!this.moveItemStackTo(sourceStack, ANALYZE_SLOT_INDEX, ANALYZE_SLOT_INDEX + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.getActiveTab() == BeeSXIControllerBlockEntity.TAB_INVENTORY) {
                if (!this.moveItemStackTo(sourceStack, NETWORK_INV_START, NETWORK_INV_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (sourceStack.isEmpty()) {
            sourceSlot.set(net.minecraft.world.item.ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }

        sourceSlot.onTake(player, sourceStack);
        return sourceCopy;
    }

    private final class TabSlot extends Slot {
        private final int tab;

        private TabSlot(BeeSXIControllerBlockEntity container, int index, int x, int y, int tab) {
            super(container, index, x, y);
            this.tab = tab;
        }

        @Override
        public boolean isActive() {
            return BeeSXIServerMenu.this.getActiveTab() == this.tab;
        }
    }

    public int getActiveTab() {
        return this.data.get(4);
    }

    public boolean hasAnalyzerTab() {
        return this.data.get(1) == 1;
    }

    public boolean isMachineStoppedForInventoryFull() {
        return this.data.get(28) == 1;
    }

    public int getCpuCount() {
        return this.data.get(2);
    }

    public int getRamCount() {
        return this.data.get(3);
    }

    public boolean isFormed() {
        return this.data.get(0) == 1;
    }

    public boolean isAnalyzing() {
        return this.data.get(8) == 1;
    }

    public int getAnalyzeProgressPercent() {
        return this.data.get(9);
    }

    public long getPowerStoredForUi() {
        return this.controller.getPowerStoredForUi();
    }

    public long getPowerCapacityForUi() {
        return this.controller.getPowerCapacityForUi();
    }

    public long getTotalRfPerTickForUi() {
        return this.data.get(12);
    }

    public long getInstanceRfPerTickForUi() {
        return this.data.get(13);
    }

    public long getAnalyzeRfPerTickForUi() {
        return this.data.get(14);
    }

    public int getStructureDimX() {
        return this.data.get(15);
    }

    public int getStructureDimY() {
        return this.data.get(16);
    }

    public int getStructureDimZ() {
        return this.data.get(17);
    }

    public int getStructureControllerCount() {
        return this.data.get(18);
    }

    public int getStructureCasingCount() {
        return this.data.get(19);
    }

    public int getStructureCpuCount() {
        return this.data.get(20);
    }

    public int getStructureRamCount() {
        return this.data.get(21);
    }

    public int getStructureAnalyzerCount() {
        return this.data.get(23);
    }

    public int getStructurePowerSupplyCount() {
        return this.data.get(24);
    }

    public int getStructureBatteryCount() {
        return this.data.get(25);
    }

    public int getStructureInvalidCount() {
        return this.data.get(26);
    }

    public int getStructureExportBusCount() {
        return this.data.get(27);
    }

    public java.util.List<ResourceLocation> getAnalyzedSpeciesIds() {
        return this.controller.getAnalyzedSpeciesIds();
    }

    public java.util.List<ResourceLocation> getUnlockedBiomeIds() {
        return this.controller.getUnlockedBiomeIds();
    }

    public java.util.List<ResourceLocation> getUnlockedFlowerIds() {
        return this.controller.getUnlockedFlowerIds();
    }

    public java.util.List<BeeSXIControllerBlockEntity.VirtualHiveConfig> getVirtualHives() {
        return this.controller.getVirtualHives();
    }

    @Nullable
    public ResourceLocation getSpeciesForLine(int line) {
        var hives = getVirtualHives();
        if (line < 0 || line >= hives.size()) {
            return null;
        }
        return hives.get(line).speciesId;
    }

    public int getInstancesForLine(int line) {
        var hives = getVirtualHives();
        if (line < 0 || line >= hives.size()) {
            return 0;
        }
        return hives.get(line).instances;
    }

    @Nullable
    public ResourceLocation getBiomeForLine(int line) {
        var hives = getVirtualHives();
        if (line < 0 || line >= hives.size()) {
            return null;
        }
        return hives.get(line).biomeId;
    }

    @Nullable
    public ResourceLocation getFlowerForLine(int line) {
        var hives = getVirtualHives();
        if (line < 0 || line >= hives.size()) {
            return null;
        }
        return hives.get(line).flowerItemId;
    }

    public float getSpeedForLine(int line) {
        ResourceLocation species = getSpeciesForLine(line);
        if (species == null) {
            return 0.0F;
        }
        return this.controller.getSpeedForSpecies(species);
    }

    public float getSpeedForSpecies(ResourceLocation speciesId) {
        if (speciesId == null) {
            return 0.0F;
        }
        return this.controller.getSpeedForSpecies(speciesId);
    }

    @Nullable
    public ResourceLocation getActivityForLine(int line) {
        ResourceLocation species = getSpeciesForLine(line);
        if (species == null) {
            return null;
        }
        return this.controller.getActivityForSpecies(species);
    }

    @Nullable
    public ResourceLocation getActivityForSpecies(ResourceLocation speciesId) {
        if (speciesId == null) {
            return null;
        }
        return this.controller.getActivityForSpecies(speciesId);
    }
}
