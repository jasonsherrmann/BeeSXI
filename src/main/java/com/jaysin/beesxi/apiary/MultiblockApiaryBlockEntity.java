package com.jaysin.beesxi.apiary;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.jaysin.beesxi.BeeSXI;

import forestry.api.apiculture.IBeeHousingInventory;
import forestry.api.apiculture.IBeeListener;
import forestry.api.apiculture.IBeeModifier;
import forestry.api.genetics.IGenome;
import forestry.apiculture.BeehouseBeeModifier;
import forestry.apiculture.InventoryBeeHousing;
import forestry.apiculture.gui.ContainerBeeHousing;
import forestry.apiculture.gui.GuiBeeHousing;
import forestry.apiculture.tiles.TileBeeHousingBase;
import forestry.core.utils.NetworkUtil;

public class MultiblockApiaryBlockEntity extends TileBeeHousingBase {
    private static final int MULTIBLOCK_HALF_SIZE = 2;
    private static final int VALIDATION_INTERVAL_TICKS = 20;
    private static final float MAX_SPEED_MULTIPLIER = 16.0F;

    private static final IBeeModifier BEE_HOUSE_MODIFIER = new BeehouseBeeModifier();

    private final InventoryBeeHousing inventory;
    private final IBeeModifier speedModifier;

    private boolean multiblockFormed;
    private float speedMultiplier;
    private long lastValidationTick;

    public MultiblockApiaryBlockEntity(BlockPos pos, BlockState state) {
        super(BeeSXI.MULTIBLOCK_APIARY_BLOCK_ENTITY.get(), pos, state, "multiblock_apiary");
        this.inventory = new InventoryBeeHousing(12);
        this.speedModifier = new MultiblockProductionSpeedModifier(this);
        this.speedMultiplier = 1.0F;
        this.inventory.disableAutomation();
        setInternalInventory(this.inventory);
    }

    @Override
    public IBeeHousingInventory getBeeInventory() {
        return this.inventory;
    }

    @Override
    public Collection<IBeeModifier> getBeeModifiers() {
        return List.of(BEE_HOUSE_MODIFIER, this.speedModifier);
    }

    @Override
    public Iterable<IBeeListener> getBeeListeners() {
        return Collections.emptyList();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ContainerBeeHousing(containerId, player.getInventory(), this, false, GuiBeeHousing.Icon.BEE_HOUSE);
    }

    @Override
    public void openGui(ServerPlayer player, InteractionHand hand, BlockPos pos) {
        player.openMenu(this, buffer -> {
            buffer.writeBlockPos(pos);
            buffer.writeBoolean(false);
            NetworkUtil.writeEnum(buffer, GuiBeeHousing.Icon.BEE_HOUSE);
        });
    }

    @Override
    public void serverTick(Level level, BlockPos pos, BlockState state) {
        validateMultiblockIfNeeded(level, pos);
        if (this.multiblockFormed) {
            super.serverTick(level, pos, state);
        }
    }

    @Override
    public void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putBoolean("MultiblockFormed", this.multiblockFormed);
        tag.putFloat("ProductionMultiplier", this.speedMultiplier);
    }

    @Override
    public void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.multiblockFormed = tag.getBoolean("MultiblockFormed");
        this.speedMultiplier = tag.contains("ProductionMultiplier") ? tag.getFloat("ProductionMultiplier") : 1.0F;
    }

    @Override
    public void writeGuiData(net.minecraft.network.FriendlyByteBuf data) {
        super.writeGuiData(data);
        data.writeBoolean(this.multiblockFormed);
        data.writeFloat(this.speedMultiplier);
    }

    @Override
    public void readGuiData(net.minecraft.network.FriendlyByteBuf data) {
        super.readGuiData(data);
        this.multiblockFormed = data.readBoolean();
        this.speedMultiplier = data.readFloat();
    }

    public boolean isMultiblockFormed() {
        return this.multiblockFormed;
    }

    public float getSpeedMultiplier() {
        return this.speedMultiplier;
    }

    private void validateMultiblockIfNeeded(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime - this.lastValidationTick < VALIDATION_INTERVAL_TICKS) {
            return;
        }

        this.lastValidationTick = gameTime;

        StructureValidationResult result = validateStructure(level, pos);
        boolean changed = this.multiblockFormed != result.formed || Float.compare(this.speedMultiplier, result.speedMultiplier) != 0;

        this.multiblockFormed = result.formed;
        this.speedMultiplier = result.speedMultiplier;

        if (changed) {
            setChanged();
            applyAssembledState(level, pos, this.multiblockFormed);
            getBeekeepingLogic().syncToClient();
            getBeekeepingLogic().clearCachedValues();
        }
    }

    public boolean isPositionInMultiblock(BlockPos pos) {
        BlockPos center = getBlockPos();
        return Math.abs(pos.getX() - center.getX()) <= MULTIBLOCK_HALF_SIZE
            && Math.abs(pos.getY() - center.getY()) <= MULTIBLOCK_HALF_SIZE
            && Math.abs(pos.getZ() - center.getZ()) <= MULTIBLOCK_HALF_SIZE;
    }

    public static MultiblockApiaryBlockEntity findControllerForPart(Level level, BlockPos partPos) {
        for (int x = -MULTIBLOCK_HALF_SIZE; x <= MULTIBLOCK_HALF_SIZE; x++) {
            for (int y = -MULTIBLOCK_HALF_SIZE; y <= MULTIBLOCK_HALF_SIZE; y++) {
                for (int z = -MULTIBLOCK_HALF_SIZE; z <= MULTIBLOCK_HALF_SIZE; z++) {
                    BlockPos candidate = partPos.offset(x, y, z);
                    if (level.getBlockEntity(candidate) instanceof MultiblockApiaryBlockEntity controller
                        && controller.isPositionInMultiblock(partPos)) {
                        return controller;
                    }
                }
            }
        }
        return null;
    }

    private void applyAssembledState(Level level, BlockPos center, boolean assembled) {
        for (int x = -MULTIBLOCK_HALF_SIZE; x <= MULTIBLOCK_HALF_SIZE; x++) {
            for (int y = -MULTIBLOCK_HALF_SIZE; y <= MULTIBLOCK_HALF_SIZE; y++) {
                for (int z = -MULTIBLOCK_HALF_SIZE; z <= MULTIBLOCK_HALF_SIZE; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    if (state.getBlock() instanceof MultiblockApiaryControllerBlock
                        && state.getValue(MultiblockApiaryControllerBlock.ASSEMBLED) != assembled) {
                        level.setBlock(pos, state.setValue(MultiblockApiaryControllerBlock.ASSEMBLED, assembled), 3);
                    }

                    if (state.getBlock() instanceof ApiaryMachinePartBlock
                        && state.getValue(ApiaryMachinePartBlock.ASSEMBLED) != assembled) {
                        level.setBlock(pos, state.setValue(ApiaryMachinePartBlock.ASSEMBLED, assembled), 3);
                    }
                }
            }
        }
    }

    private StructureValidationResult validateStructure(Level level, BlockPos center) {
        float speedBonus = 0.0F;

        for (int x = -MULTIBLOCK_HALF_SIZE; x <= MULTIBLOCK_HALF_SIZE; x++) {
            for (int y = -MULTIBLOCK_HALF_SIZE; y <= MULTIBLOCK_HALF_SIZE; y++) {
                for (int z = -MULTIBLOCK_HALF_SIZE; z <= MULTIBLOCK_HALF_SIZE; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }

                    boolean isShellPosition =
                        Math.abs(x) == MULTIBLOCK_HALF_SIZE ||
                        Math.abs(y) == MULTIBLOCK_HALF_SIZE ||
                        Math.abs(z) == MULTIBLOCK_HALF_SIZE;

                    BlockState state = level.getBlockState(center.offset(x, y, z));
                    if (state.getBlock() instanceof ApiaryMachinePartBlock partBlock) {
                        speedBonus += partBlock.getPartType().speedBonus();
                    } else if (isShellPosition) {
                        // The 5x5x5 outer shell must be complete for the machine to form.
                        return new StructureValidationResult(false, 1.0F);
                    }
                }
            }
        }

        float speed = Math.min(MAX_SPEED_MULTIPLIER, 1.0F + speedBonus);
        return new StructureValidationResult(true, speed);
    }

    private static final class MultiblockProductionSpeedModifier implements IBeeModifier {
        private final MultiblockApiaryBlockEntity blockEntity;

        private MultiblockProductionSpeedModifier(MultiblockApiaryBlockEntity blockEntity) {
            this.blockEntity = blockEntity;
        }

        @Override
        public float modifyProductionSpeed(IGenome genome, float currentModifier) {
            if (!this.blockEntity.multiblockFormed) {
                return 0.0F;
            }
            return currentModifier * this.blockEntity.speedMultiplier;
        }

        @Override
        public boolean isAlwaysActive(IGenome genome) {
            return this.blockEntity.multiblockFormed;
        }

        @Override
        public boolean isSunlightSimulated() {
            return this.blockEntity.multiblockFormed;
        }

        @Override
        public boolean isSealed() {
            return this.blockEntity.multiblockFormed;
        }
    }

    private record StructureValidationResult(boolean formed, float speedMultiplier) {
    }
}
