package com.jaysin.beesxi.blockentity;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import com.jaysin.beesxi.BeeSXI;
import com.jaysin.beesxi.server.BeeSXIExportBusMenu;

public class BeeSXIExportBusBlockEntity extends BlockEntity implements Container, net.minecraft.world.MenuProvider {
    public static final int INVENTORY_SIZE = 27;
    public static final int FILTER_SIZE = 27;

    private static final int TRANSFER_INTERVAL_TICKS = 20;
    private static final int MAX_TRANSFER_PER_INTERVAL = 64;

    private NonNullList<ItemStack> inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private NonNullList<ItemStack> filters = NonNullList.withSize(FILTER_SIZE, ItemStack.EMPTY);
    private long lastTransferResetTick = Long.MIN_VALUE;
    private int remainingTransferBudget = MAX_TRANSFER_PER_INTERVAL;
    private final IItemHandler outputItemHandler = new IItemHandler() {
        @Override
        public int getSlots() {
            return INVENTORY_SIZE;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= INVENTORY_SIZE) {
                return ItemStack.EMPTY;
            }
            return BeeSXIExportBusBlockEntity.this.inventory.get(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= INVENTORY_SIZE || amount <= 0) {
                return ItemStack.EMPTY;
            }

            ItemStack inSlot = BeeSXIExportBusBlockEntity.this.inventory.get(slot);
            if (inSlot.isEmpty()) {
                return ItemStack.EMPTY;
            }

            int extractedCount = Math.min(amount, inSlot.getCount());
            ItemStack extracted = inSlot.copyWithCount(extractedCount);
            if (simulate) {
                return extracted;
            }

            inSlot.shrink(extractedCount);
            if (inSlot.isEmpty()) {
                BeeSXIExportBusBlockEntity.this.inventory.set(slot, ItemStack.EMPTY);
            }
            BeeSXIExportBusBlockEntity.this.setChanged();
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            return BeeSXIExportBusBlockEntity.this.getMaxStackSize();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    };

    public BeeSXIExportBusBlockEntity(BlockPos pos, BlockState state) {
        super(BeeSXI.BEESXI_EXPORT_BUS_BLOCK_ENTITY.get(), pos, state);
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) {
            return;
        }

        if (!refreshTransferBudget(level.getGameTime())) {
            return;
        }

        BeeSXIControllerBlockEntity controller = findConnectedController();
        if (controller == null) {
            return;
        }

        pullFromController(controller);
    }

    public void writeMenuData(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.worldPosition);
    }

    public IItemHandler getOutputItemHandler() {
        return this.outputItemHandler;
    }

    public ItemStack routeIncomingStack(ItemStack stack) {
        if (stack.isEmpty() || this.level == null || !matchesWhitelist(stack)) {
            return stack;
        }

        refreshTransferBudget(this.level.getGameTime());
        if (this.remainingTransferBudget <= 0) {
            return stack;
        }

        int toMove = Math.min(stack.getCount(), this.remainingTransferBudget);
        if (toMove <= 0) {
            return stack;
        }

        ItemStack transferStack = stack.copyWithCount(toMove);
        ItemStack remainder = insertIntoInventory(transferStack);
        int moved = toMove - remainder.getCount();
        if (moved <= 0) {
            return stack;
        }

        this.remainingTransferBudget -= moved;
        setChanged();

        if (moved >= stack.getCount()) {
            return ItemStack.EMPTY;
        }

        ItemStack fullRemainder = stack.copy();
        fullRemainder.shrink(moved);
        return fullRemainder;
    }

    public ItemStack getFilterItem(int slot) {
        if (slot < 0 || slot >= FILTER_SIZE) {
            return ItemStack.EMPTY;
        }
        return this.filters.get(slot);
    }

    public void setFilterItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= FILTER_SIZE) {
            return;
        }

        this.filters.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        setChanged();
    }

    public ItemStack removeFilterItem(int slot) {
        if (slot < 0 || slot >= FILTER_SIZE) {
            return ItemStack.EMPTY;
        }

        ItemStack removed = this.filters.get(slot);
        this.filters.set(slot, ItemStack.EMPTY);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    public Set<ResourceLocation> getWhitelistItemIds() {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (ItemStack filter : this.filters) {
            if (filter.isEmpty()) {
                continue;
            }
            ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(filter.getItem());
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    public boolean matchesWhitelist(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && getWhitelistItemIds().contains(id);
    }

    public boolean canAcceptIncomingStack(ItemStack stack) {
        if (stack.isEmpty() || !matchesWhitelist(stack)) {
            return false;
        }
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && getAvailableSpaceFor(id) > 0;
    }

    private void pullFromController(BeeSXIControllerBlockEntity controller) {
        if (this.remainingTransferBudget <= 0) {
            return;
        }

        for (ResourceLocation id : getWhitelistItemIds()) {
            if (this.remainingTransferBudget <= 0) {
                break;
            }

            int freeSpace = getAvailableSpaceFor(id);
            if (freeSpace <= 0) {
                continue;
            }

            int moveAmount = Math.min(this.remainingTransferBudget, freeSpace);
            ItemStack extracted = controller.extractStoredItem(id, moveAmount);
            if (extracted.isEmpty()) {
                continue;
            }

            ItemStack remainder = insertIntoInventory(extracted);
            int moved = extracted.getCount() - remainder.getCount();
            if (moved > 0) {
                this.remainingTransferBudget -= moved;
                setChanged();
            }
        }
    }

    private int getAvailableSpaceFor(ResourceLocation id) {
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
        if (item == null) {
            return 0;
        }

        int space = 0;
        int maxStack = item.getDefaultMaxStackSize();
        for (ItemStack slotStack : this.inventory) {
            if (slotStack.isEmpty()) {
                space += maxStack;
                continue;
            }

            ResourceLocation slotId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(slotStack.getItem());
            if (id.equals(slotId) && slotStack.getCount() < Math.min(slotStack.getMaxStackSize(), getMaxStackSize())) {
                space += Math.min(slotStack.getMaxStackSize(), getMaxStackSize()) - slotStack.getCount();
            }
        }
        return space;
    }

    private ItemStack insertIntoInventory(ItemStack stack) {
        ItemStack remaining = stack.copy();

        for (int slot = 0; slot < this.inventory.size(); slot++) {
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }

            ItemStack inSlot = this.inventory.get(slot);
            if (inSlot.isEmpty() || !ItemStack.isSameItemSameComponents(inSlot, remaining)) {
                continue;
            }

            int max = Math.min(getMaxStackSize(), inSlot.getMaxStackSize());
            int room = max - inSlot.getCount();
            if (room <= 0) {
                continue;
            }

            int move = Math.min(room, remaining.getCount());
            inSlot.grow(move);
            remaining.shrink(move);
        }

        for (int slot = 0; slot < this.inventory.size(); slot++) {
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }

            if (!this.inventory.get(slot).isEmpty()) {
                continue;
            }

            int move = Math.min(getMaxStackSize(), remaining.getCount());
            this.inventory.set(slot, remaining.copyWithCount(move));
            remaining.shrink(move);
        }

        return remaining;
    }

    private boolean refreshTransferBudget(long gameTime) {
        if (this.lastTransferResetTick == Long.MIN_VALUE || gameTime - this.lastTransferResetTick >= TRANSFER_INTERVAL_TICKS) {
            this.lastTransferResetTick = gameTime;
            this.remainingTransferBudget = MAX_TRANSFER_PER_INTERVAL;
            return true;
        }
        return false;
    }

    @Nullable
    private BeeSXIControllerBlockEntity findConnectedController() {
        if (this.level == null) {
            return null;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int radius = 15;
        for (int x = this.worldPosition.getX() - radius; x <= this.worldPosition.getX() + radius; x++) {
            for (int y = this.worldPosition.getY() - radius; y <= this.worldPosition.getY() + radius; y++) {
                for (int z = this.worldPosition.getZ() - radius; z <= this.worldPosition.getZ() + radius; z++) {
                    cursor.set(x, y, z);
                    if (this.level.getBlockEntity(cursor) instanceof BeeSXIControllerBlockEntity controller
                        && controller.isFormed()
                        && controller.isPartOfCurrentStructure(this.worldPosition)) {
                        return controller;
                    }
                }
            }
        }

        return null;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.beesxi.beesxi_export_bus");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BeeSXIExportBusMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        ContainerHelper.saveAllItems(tag, this.inventory, provider);

        ListTag filterList = new ListTag();
        for (int slot = 0; slot < this.filters.size(); slot++) {
            ItemStack filter = this.filters.get(slot);
            if (filter.isEmpty()) {
                continue;
            }

            CompoundTag filterTag = new CompoundTag();
            filterTag.putInt("Slot", slot);
            filterTag.put("Stack", filter.saveOptional(provider));
            filterList.add(filterTag);
        }
        tag.put("Filters", filterList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
        this.filters = NonNullList.withSize(FILTER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.inventory, provider);

        ListTag filterList = tag.getList("Filters", Tag.TAG_COMPOUND);
        for (int i = 0; i < filterList.size(); i++) {
            CompoundTag filterTag = filterList.getCompound(i);
            int slot = filterTag.getInt("Slot");
            if (slot < 0 || slot >= this.filters.size()) {
                continue;
            }
            this.filters.set(slot, ItemStack.parseOptional(provider, filterTag.getCompound("Stack")));
        }
    }

    @Override
    public int getContainerSize() {
        return INVENTORY_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.inventory) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.inventory.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(this.inventory, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.inventory, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.inventory.set(slot, stack);
        if (!stack.isEmpty() && stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.inventory.clear();
        setChanged();
    }
}