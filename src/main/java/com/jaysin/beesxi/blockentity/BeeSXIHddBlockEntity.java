package com.jaysin.beesxi.blockentity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.jaysin.beesxi.BeeSXI;

public class BeeSXIHddBlockEntity extends BlockEntity {
    private static final int SIZE = 27;
    private static final int DEFAULT_TOTAL_BYTES = 8192; //1024 too low, 32768 is 32kB is too high, but it's a good starting point for testing.
    private static final int MAX_TYPES = 27;
    private static final int ITEMS_PER_BYTE = 8;

    public enum StorageState {
        EMPTY,
        HAS_ITEMS,
        FULL_TYPES_BUT_NOT_ITEMS,
        FULL
    }

    private final Map<ResourceLocation, Integer> itemCounts = new LinkedHashMap<>();

    public BeeSXIHddBlockEntity(BlockPos pos, BlockState state) {
        super(BeeSXI.BEESXI_HDD_BLOCK_ENTITY.get(), pos, state);
    }

    private int getTotalBytes() {
        return DEFAULT_TOTAL_BYTES;
    }

    private int getStoredItems() {
        int total = 0;
        for (int count : this.itemCounts.values()) {
            total += count;
        }
        return total;
    }

    public int getUsedTypes() {
        return getUsedTypesInternal();
    }

    private int getUsedTypesInternal() {
        return this.itemCounts.size();
    }

    public int getUsedBytes() {
        int itemBytes = (getStoredItems() + ITEMS_PER_BYTE - 1) / ITEMS_PER_BYTE;
        return Math.min(getTotalBytes(), itemBytes);
    }

    public int getTotalCapacityBytes() {
        return getTotalBytes();
    }

    public int getMaxTypes() {
        return MAX_TYPES;
    }

    public StorageState getStorageState() {
        if (this.itemCounts.isEmpty()) {
            return StorageState.EMPTY;
        }

        int maxItems = getMaxItemsForTypes(Math.min(getUsedTypesInternal(), MAX_TYPES));
        boolean fullTypes = getUsedTypesInternal() >= MAX_TYPES;
        boolean fullItems = getStoredItems() >= maxItems;
        if (fullTypes && !fullItems) {
            return StorageState.FULL_TYPES_BUT_NOT_ITEMS;
        }
        if (fullItems) {
            return StorageState.FULL;
        }
        return StorageState.HAS_ITEMS;
    }

    private int getMaxItemsForTypes(int ignoredTypesUsed) {
        return Math.max(0, getTotalBytes() * ITEMS_PER_BYTE);
    }

    private int getRemainingCapacityFor(ResourceLocation id) {
        boolean isNewType = !this.itemCounts.containsKey(id);
        int resultingTypes = getUsedTypes() + (isNewType ? 1 : 0);
        if (resultingTypes > MAX_TYPES) {
            return 0;
        }
        int maxItems = getMaxItemsForTypes(resultingTypes);
        return Math.max(0, maxItems - getStoredItems());
    }

    public ItemStack insertStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return stack;
        }

        int canInsert = Math.min(stack.getCount(), getRemainingCapacityFor(id));
        if (canInsert <= 0) {
            return stack;
        }

        this.itemCounts.merge(id, canInsert, Integer::sum);
        setChanged();

        if (canInsert >= stack.getCount()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        remainder.shrink(canInsert);
        return remainder;
    }

    public boolean canAcceptStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return false;
        }

        return getRemainingCapacityFor(id) > 0;
    }

    public ItemStack extractStack(int slot, int amount) {
        if (amount <= 0 || slot < 0 || slot >= SIZE) {
            return ItemStack.EMPTY;
        }

        List<ResourceLocation> types = getOrderedTypes();
        if (slot >= types.size()) {
            return ItemStack.EMPTY;
        }

        ResourceLocation id = types.get(slot);
        int stored = this.itemCounts.getOrDefault(id, 0);
        if (stored <= 0) {
            return ItemStack.EMPTY;
        }

        int removed = Math.min(stored, Math.min(amount, 64));
        int remaining = stored - removed;
        if (remaining <= 0) {
            this.itemCounts.remove(id);
        } else {
            this.itemCounts.put(id, remaining);
        }

        var itemHolder = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
        if (itemHolder == null || removed <= 0) {
            return ItemStack.EMPTY;
        }

        this.setChanged();
        return new ItemStack(itemHolder, removed);
    }

    public ItemStack extractByItemId(ResourceLocation id, int amount) {
        if (id == null || amount <= 0) {
            return ItemStack.EMPTY;
        }

        List<ResourceLocation> types = getOrderedTypes();
        int slot = types.indexOf(id);
        if (slot < 0) {
            return ItemStack.EMPTY;
        }

        return extractStack(slot, amount);
    }

    private List<ResourceLocation> getOrderedTypes() {
        List<ResourceLocation> ordered = new ArrayList<>(this.itemCounts.keySet());
        ordered.sort(Comparator.comparing(ResourceLocation::toString));
        return ordered;
    }

    public int getContainerSize() {
        return SIZE;
    }

    public boolean isEmpty() {
        return this.itemCounts.isEmpty();
    }

    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= SIZE) {
            return ItemStack.EMPTY;
        }

        List<ResourceLocation> types = getOrderedTypes();
        if (slot >= types.size()) {
            return ItemStack.EMPTY;
        }

        ResourceLocation id = types.get(slot);
        var itemHolder = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
        if (itemHolder == null) {
            return ItemStack.EMPTY;
        }

        int stored = this.itemCounts.getOrDefault(id, 0);
        if (stored <= 0) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(itemHolder, stored);
    }

    public ItemStack removeItem(int slot, int amount) {
        return ItemStack.EMPTY;
    }

    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SIZE) {
            return;
        }

        if (stack.isEmpty()) {
            List<ResourceLocation> types = getOrderedTypes();
            if (slot < types.size()) {
                this.itemCounts.remove(types.get(slot));
                this.setChanged();
            }
            return;
        }

        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return;
        }

        int toSet = Math.max(0, stack.getCount());
        if (toSet <= 0) {
            List<ResourceLocation> types = getOrderedTypes();
            if (slot < types.size()) {
                this.itemCounts.remove(types.get(slot));
                this.setChanged();
            }
            return;
        }

        if (this.itemCounts.containsKey(id) || getRemainingCapacityFor(id) > 0) {
            this.itemCounts.put(id, toSet);
        }
        this.setChanged();
    }

    public void clearContent() {
        this.itemCounts.clear();
        this.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        ListTag list = new ListTag();
        for (Map.Entry<ResourceLocation, Integer> entry : this.itemCounts.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            CompoundTag typeTag = new CompoundTag();
            typeTag.putString("Item", entry.getKey().toString());
            typeTag.putInt("Count", entry.getValue());
            list.add(typeTag);
        }
        tag.put("StoredData", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.itemCounts.clear();

        ListTag list = tag.getList("StoredData", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag typeTag = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(typeTag.getString("Item"));
            int count = Math.max(0, typeTag.getInt("Count"));
            if (id != null && count > 0) {
                this.itemCounts.put(id, count);
            }
        }

        if (this.itemCounts.isEmpty() && tag.contains("Items", Tag.TAG_LIST)) {
            // Legacy migration path from physical item-slot storage.
            ListTag legacy = tag.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < legacy.size(); i++) {
                ItemStack stack = ItemStack.parseOptional(provider, legacy.getCompound(i));
                if (!stack.isEmpty()) {
                    insertStack(stack);
                }
            }
        }
    }

    public CompoundTag toItemTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, provider);
        return tag;
    }

    public void fromItemTag(CompoundTag tag, HolderLookup.Provider provider) {
        this.itemCounts.clear();
        if (tag != null) {
            loadAdditional(tag, provider);
        }
        this.setChanged();
    }
}
