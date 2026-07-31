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

import com.jaysin.beesxi.BeeSXI;
import com.jaysin.beesxi.server.BeeSXIWeatherReporterMenu;

public class BeeSXIWeatherReporterBlockEntity extends BlockEntity implements Container, MenuProvider {
    private static final int SIZE = 1;
    private static final int REPORT_DURATION_TICKS = 20 * 60 * 10;
    private static final String WEATHER_BIOME_KEY = "BeeSXIWeatherReportBiome";

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private int progressTicks;
    private ResourceLocation pendingBiome;

    public BeeSXIWeatherReporterBlockEntity(BlockPos pos, BlockState state) {
        super(BeeSXI.BEESXI_WEATHER_REPORTER_BLOCK_ENTITY.get(), pos, state);
    }

    public void processScheduledTick(Level level, BlockPos pos, BlockState state, int elapsedTicks) {
        if (level.isClientSide) {
            return;
        }

        ItemStack stack = this.items.get(0);
        if (stack.isEmpty() || !stack.is(Items.PAPER)) {
            this.progressTicks = 0;
            this.pendingBiome = null;
            return;
        }

        if (isWeatherReport(stack)) {
            this.progressTicks = 0;
            this.pendingBiome = null;
            return;
        }

        ResourceLocation biomeAtPosition = level.getBiome(pos)
            .unwrapKey()
            .map(ResourceKey::location)
            .orElse(null);
        if (biomeAtPosition == null) {
            this.progressTicks = 0;
            this.pendingBiome = null;
            return;
        }

        if (!biomeAtPosition.equals(this.pendingBiome)) {
            this.pendingBiome = biomeAtPosition;
        }

        this.progressTicks += Math.max(1, elapsedTicks);
        if (this.progressTicks < REPORT_DURATION_TICKS) {
            setChanged();
            return;
        }

        ItemStack output = new ItemStack(Items.PAPER);
        CompoundTag tag = new CompoundTag();
        tag.putString(WEATHER_BIOME_KEY, this.pendingBiome.toString());
        output.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        output.set(DataComponents.CUSTOM_NAME, Component.literal("Weather Report: " + this.pendingBiome.getPath()));
        this.items.set(0, output);

        this.progressTicks = 0;
        this.pendingBiome = null;
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
        tag.putInt("ProgressTicks", this.progressTicks);
        tag.putString("PendingBiome", this.pendingBiome == null ? "" : this.pendingBiome.toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items, provider);
        this.progressTicks = Math.max(0, tag.getInt("ProgressTicks"));
        this.pendingBiome = ResourceLocation.tryParse(tag.getString("PendingBiome"));
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
        return this.items.get(0).isEmpty();
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
        if (stack.getCount() > 1) {
            stack.setCount(1);
        }
        this.items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 && stack.is(Items.PAPER);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.items.clear();
        setChanged();
    }
}
