package com.jaysin.beesxi.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.jaysin.beesxi.BeeSXI;

import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.core.IProduct;
import forestry.api.genetics.IIndividual;
import forestry.core.genetics.ItemGE;
import forestry.core.utils.SpeciesUtil;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

public class BeeSXIControllerBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements Container, net.minecraft.world.MenuProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int TAB_ANALYSIS = 0;
    public static final int TAB_VIRTUAL_HIVES = 1;
    public static final int TAB_INVENTORY = 2;

    private static final int SIZE = 27;
    private static final int MULTIBLOCK_SIZE = 5;
    private static final int LAST_INDEX = MULTIBLOCK_SIZE - 1;
    private static final int VALIDATION_INTERVAL = 20;
    private static final int PRODUCTION_INTERVAL = 200;
    private static final ResourceLocation DEFAULT_UNLOCKED_SPECIES = ResourceLocation.fromNamespaceAndPath("forestry", "forest");

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final Set<ResourceLocation> analyzedSpecies = new LinkedHashSet<>();
    private final List<VirtualHiveConfig> virtualHives = new ArrayList<>();
    private final List<BlockPos> hddPositions = new ArrayList<>();
    private final Set<BlockPos> assembledPositions = new HashSet<>();

    private boolean formed;
    private boolean hasAnalyzer;
    private int cpuCount;
    private int ramCount;
    private int activeTab = TAB_VIRTUAL_HIVES;
    private long lastValidationTick;
    private long lastProductionTick;

    private final ContainerData containerData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> BeeSXIControllerBlockEntity.this.formed ? 1 : 0;
                case 1 -> BeeSXIControllerBlockEntity.this.hasAnalyzer ? 1 : 0;
                case 2 -> BeeSXIControllerBlockEntity.this.cpuCount;
                case 3 -> BeeSXIControllerBlockEntity.this.ramCount;
                case 4 -> BeeSXIControllerBlockEntity.this.activeTab;
                case 5 -> BeeSXIControllerBlockEntity.this.analyzedSpecies.size();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> BeeSXIControllerBlockEntity.this.formed = value != 0;
                case 1 -> BeeSXIControllerBlockEntity.this.hasAnalyzer = value != 0;
                case 2 -> BeeSXIControllerBlockEntity.this.cpuCount = value;
                case 3 -> BeeSXIControllerBlockEntity.this.ramCount = value;
                case 4 -> BeeSXIControllerBlockEntity.this.activeTab = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 6;
        }
    };

    public BeeSXIControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BeeSXI.BEESXI_CONTROLLER_BLOCK_ENTITY.get(), pos, state);
        ensureDefaultUnlocks();
    }

    private void ensureDefaultUnlocks() {
        this.analyzedSpecies.add(DEFAULT_UNLOCKED_SPECIES);
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) {
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime - this.lastValidationTick >= VALIDATION_INTERVAL) {
            this.lastValidationTick = gameTime;
            validateStructure();
        }

        if (!this.formed) {
            return;
        }

        if (gameTime - this.lastProductionTick >= PRODUCTION_INTERVAL) {
            this.lastProductionTick = gameTime;
            produceVirtualHiveDrops();
        }
    }

    private void validateStructure() {
        Level level = this.level;
        if (level == null) {
            return;
        }

        StructureValidationResult result = findValidStructure(level);

        boolean changed = this.formed != result.valid
            || this.cpuCount != result.cpus
            || this.ramCount != result.rams
            || this.hasAnalyzer != result.hasAnalyzer;

        this.formed = result.valid;
        this.cpuCount = result.cpus;
        this.ramCount = result.rams;
        this.hasAnalyzer = result.hasAnalyzer;

        this.hddPositions.clear();
        this.hddPositions.addAll(result.hddPositions);

        resizeVirtualHives();
        applyAssembledState(result.structurePositions, result.valid);

        if (changed) {
            sync();
        }
    }

    private StructureValidationResult findValidStructure(Level level) {
        for (int lx = 0; lx < MULTIBLOCK_SIZE; lx++) {
            for (int ly = 0; ly < MULTIBLOCK_SIZE; ly++) {
                for (int lz = 0; lz < MULTIBLOCK_SIZE; lz++) {
                    if (!isNonEdgeOuter(lx, ly, lz)) {
                        continue;
                    }

                    BlockPos minPos = this.worldPosition.offset(-lx, -ly, -lz);
                    StructureValidationResult candidate = validateAt(level, minPos);
                    if (candidate.valid) {
                        return candidate;
                    }
                }
            }
        }

        return StructureValidationResult.invalid();
    }

    private StructureValidationResult validateAt(Level level, BlockPos minPos) {
        int cpus = 0;
        int rams = 0;
        int analyzers = 0;
        int controllerCount = 0;

        List<BlockPos> foundHdds = new ArrayList<>();
        List<BlockPos> structurePositions = new ArrayList<>(MULTIBLOCK_SIZE * MULTIBLOCK_SIZE * MULTIBLOCK_SIZE);

        for (int x = 0; x < MULTIBLOCK_SIZE; x++) {
            for (int y = 0; y < MULTIBLOCK_SIZE; y++) {
                for (int z = 0; z < MULTIBLOCK_SIZE; z++) {
                    BlockPos scanPos = minPos.offset(x, y, z);
                    BlockState scanState = level.getBlockState(scanPos);
                    structurePositions.add(scanPos.immutable());

                    if (scanPos.equals(this.worldPosition)) {
                        if (!(scanState.getBlock() instanceof BeeSXIControllerBlock)) {
                            return StructureValidationResult.invalid();
                        }
                        controllerCount++;
                        continue;
                    }

                    if (!(scanState.getBlock() instanceof BeeSXIPartBlock partBlock)) {
                        return StructureValidationResult.invalid();
                    }

                    BeeSXIPartType partType = partBlock.getPartType();
                    int boundaryAxes = (x == 0 || x == LAST_INDEX ? 1 : 0)
                        + (y == 0 || y == LAST_INDEX ? 1 : 0)
                        + (z == 0 || z == LAST_INDEX ? 1 : 0);
                    boolean isEdgeOrCorner = boundaryAxes >= 2;
                    if (isEdgeOrCorner && partType != BeeSXIPartType.CASING) {
                        return StructureValidationResult.invalid();
                    }

                    switch (partType) {
                        case CPU -> cpus++;
                        case RAM -> rams++;
                        case HDD -> foundHdds.add(scanPos.immutable());
                        case MOLECULAR_ANALYZER -> analyzers++;
                        case CASING -> {
                        }
                    }
                }
            }
        }

        boolean valid = controllerCount == 1 && cpus >= 1 && rams >= 1 && !foundHdds.isEmpty();
        if (!valid) {
            return StructureValidationResult.invalid();
        }

        return new StructureValidationResult(true, cpus, rams, analyzers > 0, foundHdds, structurePositions);
    }

    private static boolean isNonEdgeOuter(int x, int y, int z) {
        int boundaryAxes = (x == 0 || x == LAST_INDEX ? 1 : 0)
            + (y == 0 || y == LAST_INDEX ? 1 : 0)
            + (z == 0 || z == LAST_INDEX ? 1 : 0);
        return boundaryAxes == 1;
    }

    private void resizeVirtualHives() {
        while (this.virtualHives.size() < this.cpuCount) {
            int newIndex = this.virtualHives.size();
            this.virtualHives.add(new VirtualHiveConfig(null, newIndex == 0 ? 1 : 0));
        }
        while (this.virtualHives.size() > this.cpuCount) {
            this.virtualHives.remove(this.virtualHives.size() - 1);
        }

        int maxInstances = Math.max(0, this.ramCount);
        for (VirtualHiveConfig config : this.virtualHives) {
            config.instances = Math.max(0, Math.min(config.instances, maxInstances));
            if (config.speciesId != null && !this.analyzedSpecies.contains(config.speciesId)) {
                config.speciesId = null;
            }
        }
    }

    private void applyAssembledState(List<BlockPos> structurePositions, boolean assembled) {
        Level level = this.level;
        if (level == null) {
            return;
        }

        Set<BlockPos> currentPositions = assembled ? new HashSet<>(structurePositions) : Set.of();

        for (BlockPos pos : this.assembledPositions) {
            if (!currentPositions.contains(pos)) {
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof BeeSXIControllerBlock && state.getValue(BeeSXIControllerBlock.ASSEMBLED)) {
                    level.setBlock(pos, state.setValue(BeeSXIControllerBlock.ASSEMBLED, false), 3);
                } else if (state.getBlock() instanceof BeeSXIPartBlock && state.getValue(BeeSXIPartBlock.ASSEMBLED)) {
                    level.setBlock(pos, state.setValue(BeeSXIPartBlock.ASSEMBLED, false), 3);
                }
            }
        }

        if (assembled) {
            for (BlockPos pos : structurePositions) {
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof BeeSXIControllerBlock && !state.getValue(BeeSXIControllerBlock.ASSEMBLED)) {
                    level.setBlock(pos, state.setValue(BeeSXIControllerBlock.ASSEMBLED, true), 3);
                } else if (state.getBlock() instanceof BeeSXIPartBlock && !state.getValue(BeeSXIPartBlock.ASSEMBLED)) {
                    level.setBlock(pos, state.setValue(BeeSXIPartBlock.ASSEMBLED, true), 3);
                }
            }
        }

        this.assembledPositions.clear();
        this.assembledPositions.addAll(currentPositions);
    }

    public void writeMenuData(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.worldPosition);
    }

    public boolean clickMenuButton(Player player, int id) {
        LOGGER.info("BeeSXI button press: player={}, controller={}, buttonId={}", player.getGameProfile().getName(), this.worldPosition, id);

        if (id >= 0 && id <= 2) {
            this.activeTab = id;
            sync();
            return true;
        }

        if (id == 9000) {
            tryAnalyzeOne();
            return true;
        }

        if (id >= 1000) {
            int relative = id - 1000;
            int line = relative / 10;
            int action = relative % 10;
            if (line < 0 || line >= this.virtualHives.size()) {
                return false;
            }

            switch (action) {
                case 0 -> cycleSpecies(line, -1);
                case 1 -> cycleSpecies(line, 1);
                case 2 -> changeInstances(line, -1);
                case 3 -> changeInstances(line, 1);
                default -> {
                    return false;
                }
            }
            sync();
            return true;
        }

        return false;
    }

    private void cycleSpecies(int line, int direction) {
        List<ResourceLocation> species = getAnalyzedSpeciesIds();
        if (species.isEmpty()) {
            this.virtualHives.get(line).speciesId = null;
            return;
        }

        ResourceLocation current = this.virtualHives.get(line).speciesId;
        int index;
        if (current == null) {
            index = direction < 0 ? species.size() - 1 : 0;
        } else {
            index = species.indexOf(current);
            if (index < 0) {
                index = 0;
            } else {
                index = (index + direction + species.size()) % species.size();
            }
        }
        this.virtualHives.get(line).speciesId = species.get(index);
    }

    private void changeInstances(int line, int delta) {
        int max = Math.max(0, this.ramCount);
        VirtualHiveConfig config = this.virtualHives.get(line);
        config.instances = Math.max(0, Math.min(max, config.instances + delta));
    }

    private void tryAnalyzeOne() {
        if (!this.hasAnalyzer) {
            return;
        }

        ItemStack stack = this.items.get(0);
        if (stack.isEmpty() || !ItemGE.isIndividual(stack)) {
            return;
        }

        IIndividual individual = ItemGE.getIndividual(stack);
        if (individual == null || individual.getSpecies() == null || individual.getSpecies().id() == null) {
            return;
        }

        ResourceLocation id = individual.getSpecies().id();
        if (this.analyzedSpecies.add(id)) {
            LOGGER.info("Bee analyzed: player action at controller={}, species={}", this.worldPosition, id);
            sync();
        }
    }

    private void produceVirtualHiveDrops() {
        Level level = this.level;
        if (level == null || !this.formed) {
            return;
        }

        for (VirtualHiveConfig config : this.virtualHives) {
            if (config.speciesId == null || config.instances <= 0) {
                continue;
            }

            IBeeSpecies species = SpeciesUtil.getBeeSpecies(config.speciesId);
            if (species == null) {
                continue;
            }

            for (int i = 0; i < config.instances; i++) {
                produceForSpecies(species, level);
            }
        }

        sync();
    }

    private void produceForSpecies(IBeeSpecies species, Level level) {
        for (IProduct product : species.getProducts()) {
            if (level.random.nextFloat() <= product.chance()) {
                insertProducedStack(product.createStack());
            }
        }
        for (IProduct product : species.getSpecialties()) {
            if (level.random.nextFloat() <= product.chance()) {
                insertProducedStack(product.createStack());
            }
        }
    }

    private void insertProducedStack(ItemStack produced) {
        if (produced.isEmpty() || this.level == null) {
            return;
        }

        ItemStack remaining = produced.copy();

        for (BlockPos hddPos : this.hddPositions) {
            if (remaining.isEmpty()) {
                break;
            }
            if (this.level.getBlockEntity(hddPos) instanceof BeeSXIHddBlockEntity hdd) {
                remaining = addToContainer(hdd, remaining, 0);
            }
        }

        if (!remaining.isEmpty()) {
            remaining = addToContainer(this, remaining, 1);
        }

        if (!remaining.isEmpty() && this.level instanceof ServerLevel serverLevel) {
            net.minecraft.world.Containers.dropItemStack(serverLevel, this.worldPosition.getX(), this.worldPosition.getY() + 1, this.worldPosition.getZ(), remaining);
        }
    }

    private static ItemStack addToContainer(Container container, ItemStack stack, int startSlot) {
        ItemStack remaining = stack.copy();

        for (int slot = startSlot; slot < container.getContainerSize(); slot++) {
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }

            ItemStack inSlot = container.getItem(slot);
            if (inSlot.isEmpty()) {
                continue;
            }

            if (!ItemStack.isSameItemSameComponents(inSlot, remaining)) {
                continue;
            }

            int max = Math.min(container.getMaxStackSize(), inSlot.getMaxStackSize());
            int room = max - inSlot.getCount();
            if (room <= 0) {
                continue;
            }

            int move = Math.min(room, remaining.getCount());
            inSlot.grow(move);
            remaining.shrink(move);
            container.setChanged();
        }

        for (int slot = startSlot; slot < container.getContainerSize(); slot++) {
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }

            if (!container.getItem(slot).isEmpty()) {
                continue;
            }

            int move = Math.min(container.getMaxStackSize(), remaining.getCount());
            ItemStack moved = remaining.copyWithCount(move);
            container.setItem(slot, moved);
            remaining.shrink(move);
        }

        return remaining;
    }

    private void sync() {
        this.setChanged();
        if (this.level instanceof ServerLevel serverLevel) {
            BlockState state = this.getBlockState();
            serverLevel.sendBlockUpdated(this.worldPosition, state, state, 3);
        }
    }

    public boolean isFormed() {
        return this.formed;
    }

    public boolean hasAnalyzer() {
        return this.hasAnalyzer;
    }

    public int getCpuCount() {
        return this.cpuCount;
    }

    public int getRamCount() {
        return this.ramCount;
    }

    public int getActiveTab() {
        return this.activeTab;
    }

    public List<ResourceLocation> getAnalyzedSpeciesIds() {
        return List.copyOf(this.analyzedSpecies);
    }

    public List<VirtualHiveConfig> getVirtualHives() {
        List<VirtualHiveConfig> copy = new ArrayList<>(this.virtualHives.size());
        for (VirtualHiveConfig config : this.virtualHives) {
            copy.add(new VirtualHiveConfig(config.speciesId, config.instances));
        }
        return copy;
    }

    public ContainerData getContainerData() {
        return this.containerData;
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable("block.beesxi.beesxi_controller");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BeeSXIServerMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);

        ContainerHelper.saveAllItems(tag, this.items, provider);
        tag.putBoolean("Formed", this.formed);
        tag.putBoolean("HasAnalyzer", this.hasAnalyzer);
        tag.putInt("CpuCount", this.cpuCount);
        tag.putInt("RamCount", this.ramCount);
        tag.putInt("ActiveTab", this.activeTab);

        ListTag analyzed = new ListTag();
        for (ResourceLocation id : this.analyzedSpecies) {
            analyzed.add(StringTag.valueOf(id.toString()));
        }
        tag.put("AnalyzedSpecies", analyzed);

        ListTag hives = new ListTag();
        for (VirtualHiveConfig config : this.virtualHives) {
            CompoundTag hiveTag = new CompoundTag();
            hiveTag.putString("Species", config.speciesId == null ? "" : config.speciesId.toString());
            hiveTag.putInt("Instances", config.instances);
            hives.add(hiveTag);
        }
        tag.put("VirtualHives", hives);

        ListTag hdds = new ListTag();
        for (BlockPos hddPos : this.hddPositions) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", hddPos.getX());
            posTag.putInt("Y", hddPos.getY());
            posTag.putInt("Z", hddPos.getZ());
            hdds.add(posTag);
        }
        tag.put("HddPositions", hdds);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);

        this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items, provider);

        this.formed = tag.getBoolean("Formed");
        this.hasAnalyzer = tag.getBoolean("HasAnalyzer");
        this.cpuCount = tag.getInt("CpuCount");
        this.ramCount = tag.getInt("RamCount");
        this.activeTab = tag.getInt("ActiveTab");

        this.analyzedSpecies.clear();
        ListTag analyzed = tag.getList("AnalyzedSpecies", Tag.TAG_STRING);
        for (int i = 0; i < analyzed.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(analyzed.getString(i));
            if (id != null) {
                this.analyzedSpecies.add(id);
            }
        }
        ensureDefaultUnlocks();

        this.virtualHives.clear();
        ListTag hives = tag.getList("VirtualHives", Tag.TAG_COMPOUND);
        for (int i = 0; i < hives.size(); i++) {
            CompoundTag hiveTag = hives.getCompound(i);
            ResourceLocation speciesId = ResourceLocation.tryParse(hiveTag.getString("Species"));
            int instances = Math.max(0, hiveTag.getInt("Instances"));
            this.virtualHives.add(new VirtualHiveConfig(speciesId, instances));
        }

        this.hddPositions.clear();
        ListTag hdds = tag.getList("HddPositions", Tag.TAG_COMPOUND);
        for (int i = 0; i < hdds.size(); i++) {
            CompoundTag posTag = hdds.getCompound(i);
            this.hddPositions.add(new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z")));
        }

        this.assembledPositions.clear();
        resizeVirtualHives();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        saveAdditional(tag, provider);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider provider) {
        loadAdditional(tag, provider);
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
            sync();
        }
        return stack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }
        sync();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.items.clear();
        sync();
    }

    public static final class VirtualHiveConfig {
        public ResourceLocation speciesId;
        public int instances;

        public VirtualHiveConfig(ResourceLocation speciesId, int instances) {
            this.speciesId = speciesId;
            this.instances = instances;
        }
    }

    private static final class StructureValidationResult {
        final boolean valid;
        final int cpus;
        final int rams;
        final boolean hasAnalyzer;
        final List<BlockPos> hddPositions;
        final List<BlockPos> structurePositions;

        private StructureValidationResult(boolean valid, int cpus, int rams, boolean hasAnalyzer, List<BlockPos> hddPositions, List<BlockPos> structurePositions) {
            this.valid = valid;
            this.cpus = cpus;
            this.rams = rams;
            this.hasAnalyzer = hasAnalyzer;
            this.hddPositions = hddPositions;
            this.structurePositions = structurePositions;
        }

        static StructureValidationResult invalid() {
            return new StructureValidationResult(false, 0, 0, false, List.of(), List.of());
        }
    }
}
