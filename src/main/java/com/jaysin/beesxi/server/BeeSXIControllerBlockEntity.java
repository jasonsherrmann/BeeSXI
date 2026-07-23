package com.jaysin.beesxi.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.Deque;

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
import forestry.api.apiculture.ForestryActivityTypes;
import forestry.api.core.IProduct;
import forestry.api.genetics.IIndividual;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.BeeChromosomes;
import forestry.core.genetics.ItemGE;
import forestry.core.utils.SpeciesUtil;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

public class BeeSXIControllerBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements Container, net.minecraft.world.MenuProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int TAB_ANALYSIS = 0;
    public static final int TAB_VIRTUAL_HIVES = 1;
    public static final int TAB_INVENTORY = 2;

    private static final int SIZE = 1;
    private static final int NETWORK_SLOT_PAGE_SIZE = 27;
    private static final int MIN_MULTIBLOCK_DIM = 3;
    private static final int MAX_MULTIBLOCK_DIM = 15;
    private static final int VALIDATION_INTERVAL = 20;
    private static final int PRODUCTION_INTERVAL = 200;
    private static final int ANALYZE_DURATION_TICKS = 20 * 60 * 5;
    private static final long ANALYZE_RF_COST = 10_000_000L;
    private static final long RFPerCycle = 1_000L;
    private static final long RF_PER_TICK_CPU = 0L;
    private static final long RF_PER_TICK_RAM = 0L;
    private static final long RF_PER_TICK_CONTROLLER = 0L;
    private static final ResourceLocation DEFAULT_UNLOCKED_SPECIES = ResourceLocation.fromNamespaceAndPath("forestry", "forest");
    private static final Set<ResourceLocation> HALF_RATE_ACTIVITY_TYPES = Set.of(ForestryActivityTypes.DIURNAL, ForestryActivityTypes.NOCTURNAL);
    private static final int[][] CARDINAL_DIRECTIONS = {
        {1, 0, 0}, {-1, 0, 0},
        {0, 1, 0}, {0, -1, 0},
        {0, 0, 1}, {0, 0, -1}
    };

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final Map<ResourceLocation, AnalyzedBeeTraits> analyzedSpecies = new LinkedHashMap<>();
    private final List<VirtualHiveConfig> virtualHives = new ArrayList<>();
    private final List<BlockPos> hddPositions = new ArrayList<>();
    private final List<BlockPos> powerSupplyPositions = new ArrayList<>();
    private final List<BlockPos> batteryPositions = new ArrayList<>();
    private final Set<BlockPos> assembledPositions = new HashSet<>();

    private boolean formed;
    private boolean hasAnalyzer;
    private int cpuCount;
    private int ramCount;
    private int activeTab = TAB_VIRTUAL_HIVES;
    private int inventoryPage;
    private int inventoryMaxPage;
    private boolean analyzing;
    private int analyzeTicksRemaining;
    private ResourceLocation pendingAnalyzeSpeciesId;
    private float pendingAnalyzeSpeed;
    private ResourceLocation pendingAnalyzeActivityId;
    private long lastSyncedPowerStored = Long.MIN_VALUE;
    private long lastSyncedPowerCapacity = Long.MIN_VALUE;
    private int lastSyncedAnalyzeProgress = Integer.MIN_VALUE;
    private boolean lastSyncedAnalyzing;
    private long uiPowerStored;
    private long uiPowerCapacity;
    private int uiAnalyzeProgress = 100;
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
                case 6 -> BeeSXIControllerBlockEntity.this.inventoryPage;
                case 7 -> BeeSXIControllerBlockEntity.this.inventoryMaxPage;
                case 8 -> BeeSXIControllerBlockEntity.this.analyzing ? 1 : 0;
                case 9 -> BeeSXIControllerBlockEntity.this.uiAnalyzeProgress;
                case 10 -> (int) Math.min(Integer.MAX_VALUE, BeeSXIControllerBlockEntity.this.uiPowerStored);
                case 11 -> (int) Math.min(Integer.MAX_VALUE, BeeSXIControllerBlockEntity.this.uiPowerCapacity);
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
                case 6 -> BeeSXIControllerBlockEntity.this.inventoryPage = Math.max(0, value);
                case 7 -> BeeSXIControllerBlockEntity.this.inventoryMaxPage = Math.max(0, value);
                case 8 -> BeeSXIControllerBlockEntity.this.analyzing = value != 0;
                case 9 -> BeeSXIControllerBlockEntity.this.uiAnalyzeProgress = value;
                case 10 -> BeeSXIControllerBlockEntity.this.uiPowerStored = Math.max(0, value);
                case 11 -> BeeSXIControllerBlockEntity.this.uiPowerCapacity = Math.max(0, value);
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 12;
        }
    };

    public BeeSXIControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BeeSXI.BEESXI_CONTROLLER_BLOCK_ENTITY.get(), pos, state);
        ensureDefaultUnlocks();
    }

    private void ensureDefaultUnlocks() {
        this.analyzedSpecies.putIfAbsent(DEFAULT_UNLOCKED_SPECIES, resolveSpeciesDefaults(DEFAULT_UNLOCKED_SPECIES));
    }

    private AnalyzedBeeTraits resolveSpeciesDefaults(ResourceLocation speciesId) {
        IBeeSpecies species = SpeciesUtil.getBeeSpecies(speciesId);
        if (species == null) {
            return new AnalyzedBeeTraits(1.0F, null);
        }

        IGenome genome = species.createIndividual().getGenome();
        float speed = genome.getActiveValue(BeeChromosomes.SPEED);
        ResourceLocation activityId = genome.getActiveValue(BeeChromosomes.ACTIVITY);
        return new AnalyzedBeeTraits(speed, activityId);
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

        long perTickCost = RF_PER_TICK_CONTROLLER
            + (long) this.cpuCount * RF_PER_TICK_CPU
            + (long) this.ramCount * RF_PER_TICK_RAM;
        if (perTickCost > 0 && !consumeEnergy(perTickCost, false)) {
            return;
        }

        chargeBatteriesFromSupplies();

        if (this.analyzing) {
            tickAnalyzeProcess();
        }

        if (gameTime - this.lastProductionTick >= PRODUCTION_INTERVAL) {
            this.lastProductionTick = gameTime;
            produceVirtualHiveDrops();
        }

        syncIfUiChanged();
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
        this.powerSupplyPositions.clear();
        this.powerSupplyPositions.addAll(result.powerSupplyPositions);
        this.batteryPositions.clear();
        this.batteryPositions.addAll(result.batteryPositions);
        this.inventoryMaxPage = getMaxInventoryPage();
        if (this.inventoryPage > this.inventoryMaxPage) {
            this.inventoryPage = this.inventoryMaxPage;
        }

        // Keep prior virtual hive configuration when the structure is temporarily broken.
        // Re-size only while formed so species/instance choices survive rebuilds.
        if (this.formed) {
            resizeVirtualHives();
        }
        applyAssembledState(result.structurePositions, result.valid);

        if (changed) {
            sync();
        }
    }

    private StructureValidationResult findValidStructure(Level level) {
        Set<BlockPos> component = collectConnectedStructureComponent(level);
        if (component.isEmpty()) {
            return StructureValidationResult.invalid();
        }

        BlockPos min = null;
        BlockPos max = null;
        for (BlockPos pos : component) {
            if (min == null) {
                min = pos;
                max = pos;
                continue;
            }
            min = new BlockPos(Math.min(min.getX(), pos.getX()), Math.min(min.getY(), pos.getY()), Math.min(min.getZ(), pos.getZ()));
            max = new BlockPos(Math.max(max.getX(), pos.getX()), Math.max(max.getY(), pos.getY()), Math.max(max.getZ(), pos.getZ()));
        }

        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;
        if (sizeX < MIN_MULTIBLOCK_DIM || sizeX > MAX_MULTIBLOCK_DIM
            || sizeY < MIN_MULTIBLOCK_DIM || sizeY > MAX_MULTIBLOCK_DIM
            || sizeZ < MIN_MULTIBLOCK_DIM || sizeZ > MAX_MULTIBLOCK_DIM) {
            return StructureValidationResult.invalid();
        }

        return validateAt(level, min, sizeX, sizeY, sizeZ);
    }

    private Set<BlockPos> collectConnectedStructureComponent(Level level) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(this.worldPosition);
        visited.add(this.worldPosition);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (int[] direction : CARDINAL_DIRECTIONS) {
                BlockPos next = current.offset(direction[0], direction[1], direction[2]);
                if (visited.contains(next)) {
                    continue;
                }

                int dx = Math.abs(next.getX() - this.worldPosition.getX());
                int dy = Math.abs(next.getY() - this.worldPosition.getY());
                int dz = Math.abs(next.getZ() - this.worldPosition.getZ());
                if (dx > MAX_MULTIBLOCK_DIM || dy > MAX_MULTIBLOCK_DIM || dz > MAX_MULTIBLOCK_DIM) {
                    continue;
                }

                BlockState state = level.getBlockState(next);
                boolean isStructureBlock = state.getBlock() instanceof BeeSXIControllerBlock || state.getBlock() instanceof BeeSXIPartBlock;
                if (!isStructureBlock) {
                    continue;
                }

                visited.add(next);
                queue.addLast(next);
                if (visited.size() > MAX_MULTIBLOCK_DIM * MAX_MULTIBLOCK_DIM * MAX_MULTIBLOCK_DIM) {
                    return Set.of();
                }
            }
        }

        return visited;
    }

    private StructureValidationResult validateAt(Level level, BlockPos minPos, int sizeX, int sizeY, int sizeZ) {
        int cpus = 0;
        int rams = 0;
        int analyzers = 0;
        int controllerCount = 0;

        List<BlockPos> foundHdds = new ArrayList<>();
        List<BlockPos> foundPowerSupplies = new ArrayList<>();
        List<BlockPos> foundBatteries = new ArrayList<>();
        List<BlockPos> structurePositions = new ArrayList<>(sizeX * sizeY * sizeZ);

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
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
                    int boundaryAxes = (x == 0 || x == sizeX - 1 ? 1 : 0)
                        + (y == 0 || y == sizeY - 1 ? 1 : 0)
                        + (z == 0 || z == sizeZ - 1 ? 1 : 0);
                    boolean isEdgeOrCorner = boundaryAxes >= 2;
                    if (isEdgeOrCorner && partType != BeeSXIPartType.CASING) {
                        return StructureValidationResult.invalid();
                    }

                    switch (partType) {
                        case CPU -> cpus++;
                        case RAM -> rams++;
                        case HDD -> foundHdds.add(scanPos.immutable());
                        case POWER_SUPPLY -> foundPowerSupplies.add(scanPos.immutable());
                        case BATTERY -> foundBatteries.add(scanPos.immutable());
                        case MOLECULAR_ANALYZER -> analyzers++;
                        case CASING -> {
                        }
                    }
                }
            }
        }

        int controllerBoundaryAxes = (this.worldPosition.getX() == minPos.getX() || this.worldPosition.getX() == minPos.getX() + sizeX - 1 ? 1 : 0)
            + (this.worldPosition.getY() == minPos.getY() || this.worldPosition.getY() == minPos.getY() + sizeY - 1 ? 1 : 0)
            + (this.worldPosition.getZ() == minPos.getZ() || this.worldPosition.getZ() == minPos.getZ() + sizeZ - 1 ? 1 : 0);
        if (controllerBoundaryAxes != 1) {
            return StructureValidationResult.invalid();
        }

        boolean valid = controllerCount == 1 && cpus >= 1 && rams >= 1 && !foundHdds.isEmpty();
        if (!valid) {
            return StructureValidationResult.invalid();
        }

        if (foundPowerSupplies.isEmpty() || foundBatteries.isEmpty()) {
            return StructureValidationResult.invalid();
        }

        return new StructureValidationResult(true, cpus, rams, analyzers > 0, foundHdds, foundPowerSupplies, foundBatteries, structurePositions);
    }

    private void resizeVirtualHives() {
        while (this.virtualHives.size() < this.cpuCount) {
            int newIndex = this.virtualHives.size();
            this.virtualHives.add(new VirtualHiveConfig(null, newIndex == 0 ? 1 : 0));
        }
        while (this.virtualHives.size() > this.cpuCount) {
            this.virtualHives.remove(this.virtualHives.size() - 1);
        }

        for (VirtualHiveConfig config : this.virtualHives) {
            config.instances = Math.max(0, config.instances);
            if (config.speciesId != null && !this.analyzedSpecies.containsKey(config.speciesId)) {
                config.speciesId = null;
            }
        }

        enforceTotalRamLimit();
    }

    private int getTotalInstances() {
        int total = 0;
        for (VirtualHiveConfig config : this.virtualHives) {
            total += Math.max(0, config.instances);
        }
        return total;
    }

    private void enforceTotalRamLimit() {
        int maxTotal = Math.max(0, this.ramCount);
        int total = getTotalInstances();
        if (total <= maxTotal) {
            return;
        }

        int overflow = total - maxTotal;
        for (int i = this.virtualHives.size() - 1; i >= 0 && overflow > 0; i--) {
            VirtualHiveConfig config = this.virtualHives.get(i);
            int reduce = Math.min(config.instances, overflow);
            if (reduce > 0) {
                config.instances -= reduce;
                overflow -= reduce;
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
            if (id == TAB_INVENTORY) {
                this.inventoryPage = 0;
            }
            sync();
            return true;
        }

        if (id == 9000) {
            startAnalyzeOne();
            return true;
        }

        if (id == 9100) {
            this.inventoryPage = Math.max(0, this.inventoryPage - 1);
            sync();
            return true;
        }

        if (id == 9101) {
            this.inventoryPage = Math.min(getMaxInventoryPage(), this.inventoryPage + 1);
            sync();
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
        VirtualHiveConfig config = this.virtualHives.get(line);
        if (delta < 0) {
            config.instances = Math.max(0, config.instances + delta);
            return;
        }

        if (delta > 0) {
            int maxTotal = Math.max(0, this.ramCount);
            int remaining = Math.max(0, maxTotal - getTotalInstances());
            int add = Math.min(remaining, delta);
            if (add > 0) {
                config.instances += add;
            }
        }
    }

    private void startAnalyzeOne() {
        if (!this.hasAnalyzer || this.analyzing || !this.formed) {
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
        IGenome genome = individual.getGenome();
        float speed = genome == null ? 1.0F : genome.getActiveValue(BeeChromosomes.SPEED);
        ResourceLocation activityId = genome == null ? null : genome.getActiveValue(BeeChromosomes.ACTIVITY);

        if (!consumeEnergy(ANALYZE_RF_COST, true)) {
            return;
        }
        consumeEnergy(ANALYZE_RF_COST, false);

        this.pendingAnalyzeSpeciesId = id;
        this.pendingAnalyzeSpeed = speed;
        this.pendingAnalyzeActivityId = activityId;
        this.analyzing = true;
        this.analyzeTicksRemaining = ANALYZE_DURATION_TICKS;
        sync();
    }

    private void tickAnalyzeProcess() {
        if (!this.analyzing || !this.formed) {
            return;
        }

        this.analyzeTicksRemaining = Math.max(0, this.analyzeTicksRemaining - 1);
        if (this.analyzeTicksRemaining > 0) {
            return;
        }

        if (this.pendingAnalyzeSpeciesId != null) {
            AnalyzedBeeTraits previous = this.analyzedSpecies.put(this.pendingAnalyzeSpeciesId, new AnalyzedBeeTraits(this.pendingAnalyzeSpeed, this.pendingAnalyzeActivityId));
            if (previous == null || previous.speed != this.pendingAnalyzeSpeed || !java.util.Objects.equals(previous.activityTypeId, this.pendingAnalyzeActivityId)) {
                LOGGER.info("Bee analyzed: controller={}, species={}, speed={}, activity={}", this.worldPosition, this.pendingAnalyzeSpeciesId, this.pendingAnalyzeSpeed, this.pendingAnalyzeActivityId);
            }
        }

        this.pendingAnalyzeSpeciesId = null;
        this.pendingAnalyzeActivityId = null;
        this.pendingAnalyzeSpeed = 0.0F;
        this.analyzing = false;
        this.analyzeTicksRemaining = 0;
        sync();
    }

    private void produceVirtualHiveDrops() {
        Level level = this.level;
        if (level == null || !this.formed) {
            return;
        }

        if (!consumeEnergy(RFPerCycle, true)) {
            return;
        }
        consumeEnergy(RFPerCycle, false);

        for (VirtualHiveConfig config : this.virtualHives) {
            if (config.speciesId == null || config.instances <= 0) {
                continue;
            }

            IBeeSpecies species = SpeciesUtil.getBeeSpecies(config.speciesId);
            if (species == null) {
                continue;
            }

            AnalyzedBeeTraits traits = this.analyzedSpecies.get(config.speciesId);
            double activityMultiplier = 1.0D;
            if (traits != null && traits.activityTypeId != null && HALF_RATE_ACTIVITY_TYPES.contains(traits.activityTypeId)) {
                activityMultiplier = 0.5D;
            } //Fix this code to make it more intuative

            for (int i = 0; i < config.instances; i++) {
                produceForSpecies(species, level, activityMultiplier);
            }
        }

        sync();
    }

    private void produceForSpecies(IBeeSpecies species, Level level, double activityMultiplier) {
        for (IProduct product : species.getProducts()) {
            double adjustedChance = product.chance() * activityMultiplier;
            if (level.random.nextDouble() <= adjustedChance) {
                insertProducedStack(product.createStack());
            }
        }
        for (IProduct product : species.getSpecialties()) {
            double adjustedChance = product.chance() * activityMultiplier;
            if (level.random.nextDouble() <= adjustedChance) {
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
                remaining = hdd.insertStack(remaining);
            }
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

    private boolean consumeEnergy(long rf, boolean simulate) {
        if (rf <= 0) {
            return true;
        }

        long remaining = rf;
        if (this.level == null) {
            return false;
        }

        for (BlockPos powerPos : this.batteryPositions) {
            if (!(this.level.getBlockEntity(powerPos) instanceof BeeSXIPowerSupplyBlockEntity power)) {
                continue;
            }
            int removed = power.extractEnergy((int) Math.min(Integer.MAX_VALUE, remaining), simulate);
            remaining -= removed;
            if (remaining <= 0) {
                return true;
            }
        }

        for (BlockPos powerPos : this.powerSupplyPositions) {
            if (!(this.level.getBlockEntity(powerPos) instanceof BeeSXIPowerSupplyBlockEntity power)) {
                continue;
            }
            int removed = power.extractEnergy((int) Math.min(Integer.MAX_VALUE, remaining), simulate);
            remaining -= removed;
            if (remaining <= 0) {
                return true;
            }
        }

        return false;
    }

    private void chargeBatteriesFromSupplies() {
        if (this.level == null || !this.formed) {
            return;
        }

        long transferRemaining = 10_000L;
        if (transferRemaining <= 0) {
            return;
        }

        for (BlockPos batteryPos : this.batteryPositions) {
            if (transferRemaining <= 0) {
                break;
            }
            if (!(this.level.getBlockEntity(batteryPos) instanceof BeeSXIPowerSupplyBlockEntity battery)) {
                continue;
            }

            int batteryRoom = battery.getMaxEnergyStored() - battery.getEnergyStored();
            if (batteryRoom <= 0) {
                continue;
            }

            int toBattery = (int) Math.min(transferRemaining, batteryRoom);
            int movedTotal = 0;
            for (BlockPos supplyPos : this.powerSupplyPositions) {
                if (toBattery <= 0) {
                    break;
                }
                if (!(this.level.getBlockEntity(supplyPos) instanceof BeeSXIPowerSupplyBlockEntity supply)) {
                    continue;
                }

                int pulled = supply.extractEnergy(toBattery, false);
                if (pulled <= 0) {
                    continue;
                }
                int accepted = battery.receiveEnergy(pulled, false);
                if (accepted < pulled) {
                    supply.receiveEnergy(pulled - accepted, false);
                }
                movedTotal += accepted;
                toBattery -= accepted;
            }
            transferRemaining -= movedTotal;
        }
    }

    private long getTotalPowerStored() {
        if (this.level == null) {
            return 0L;
        }
        long total = 0L;
        for (BlockPos powerPos : this.powerSupplyPositions) {
            if (this.level.getBlockEntity(powerPos) instanceof BeeSXIPowerSupplyBlockEntity power) {
                total += power.getEnergyStored();
            }
        }
        for (BlockPos powerPos : this.batteryPositions) {
            if (this.level.getBlockEntity(powerPos) instanceof BeeSXIPowerSupplyBlockEntity power) {
                total += power.getEnergyStored();
            }
        }
        return total;
    }

    private long getTotalPowerCapacity() {
        if (this.level == null) {
            return 0L;
        }
        long total = 0L;
        for (BlockPos powerPos : this.powerSupplyPositions) {
            if (this.level.getBlockEntity(powerPos) instanceof BeeSXIPowerSupplyBlockEntity power) {
                total += power.getMaxEnergyStored();
            }
        }
        for (BlockPos powerPos : this.batteryPositions) {
            if (this.level.getBlockEntity(powerPos) instanceof BeeSXIPowerSupplyBlockEntity power) {
                total += power.getMaxEnergyStored();
            }
        }
        return total;
    }

    private int getAnalyzeProgressPercent() {
        if (!this.analyzing) {
            return 100;
        }
        int elapsed = ANALYZE_DURATION_TICKS - this.analyzeTicksRemaining;
        return Math.max(0, Math.min(100, (int) ((elapsed * 100L) / ANALYZE_DURATION_TICKS)));
    }

    private void sync() {
        this.uiPowerStored = getTotalPowerStored();
        this.uiPowerCapacity = getTotalPowerCapacity();
        this.uiAnalyzeProgress = getAnalyzeProgressPercent();

        this.setChanged();
        if (this.level instanceof ServerLevel serverLevel) {
            BlockState state = this.getBlockState();
            serverLevel.sendBlockUpdated(this.worldPosition, state, state, 3);
        }

        this.lastSyncedPowerStored = this.uiPowerStored;
        this.lastSyncedPowerCapacity = this.uiPowerCapacity;
        this.lastSyncedAnalyzeProgress = this.uiAnalyzeProgress;
        this.lastSyncedAnalyzing = this.analyzing;
    }

    private void syncIfUiChanged() {
        long powerStored = getTotalPowerStored();
        long powerCapacity = getTotalPowerCapacity();
        int analyzeProgress = getAnalyzeProgressPercent();
        if (powerStored != this.lastSyncedPowerStored
            || powerCapacity != this.lastSyncedPowerCapacity
            || analyzeProgress != this.lastSyncedAnalyzeProgress
            || this.lastSyncedAnalyzing != this.analyzing) {
            sync();
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
        return List.copyOf(this.analyzedSpecies.keySet());
    }

    public float getSpeedForSpecies(ResourceLocation speciesId) {
        if (speciesId == null) {
            return 1.0F;
        }
        AnalyzedBeeTraits traits = this.analyzedSpecies.get(speciesId);
        if (traits != null) {
            return traits.speed;
        }
        return resolveSpeciesDefaults(speciesId).speed;
    }

    public ResourceLocation getActivityForSpecies(ResourceLocation speciesId) {
        if (speciesId == null) {
            return null;
        }
        AnalyzedBeeTraits traits = this.analyzedSpecies.get(speciesId);
        if (traits != null) {
            return traits.activityTypeId;
        }
        return resolveSpeciesDefaults(speciesId).activityTypeId;
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

    public int getHddNetworkSlotCount() {
        if (this.level == null) {
            return 0;
        }

        int total = 0;
        for (BlockPos hddPos : this.hddPositions) {
            if (this.level.getBlockEntity(hddPos) instanceof BeeSXIHddBlockEntity hdd) {
                total += hdd.getContainerSize();
            }
        }
        return total;
    }

    public int getInventoryPage() {
        int max = getMaxInventoryPage();
        if (this.inventoryPage > max) {
            this.inventoryPage = max;
        }
        return this.inventoryPage;
    }

    public int getMaxInventoryPage() {
        return Math.max(0, (getHddNetworkSlotCount() - 1) / NETWORK_SLOT_PAGE_SIZE);
    }

    public long getPowerStoredForUi() {
        return this.uiPowerStored;
    }

    public long getPowerCapacityForUi() {
        return this.uiPowerCapacity;
    }

    public boolean isAnalyzing() {
        return this.analyzing;
    }

    public int getAnalyzeProgressForUi() {
        return getAnalyzeProgressPercent();
    }

    public ItemStack getHddNetworkItem(int slot) {
        HddSlotRef ref = resolveHddSlot(slot);
        if (ref == null) {
            return ItemStack.EMPTY;
        }
        return ref.hdd.getItem(ref.slot);
    }

    public ItemStack extractHddNetworkItem(int slot, int amount) {
        HddSlotRef ref = resolveHddSlot(slot);
        if (ref == null) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = ref.hdd.extractStack(ref.slot, amount);
        if (!removed.isEmpty()) {
            sync();
        }
        return removed;
    }

    public int getHddBytesUsed(int slot) {
        HddSlotRef ref = resolveHddSlot(slot);
        if (ref == null) {
            return 0;
        }
        return ref.hdd.getUsedBytes();
    }

    public int getHddBytesTotal(int slot) {
        HddSlotRef ref = resolveHddSlot(slot);
        if (ref == null) {
            return 0;
        }
        return ref.hdd.getTotalCapacityBytes();
    }

    public int getHddTypesUsed(int slot) {
        HddSlotRef ref = resolveHddSlot(slot);
        if (ref == null) {
            return 0;
        }
        return ref.hdd.getUsedTypes();
    }

    public int getHddTypesMax(int slot) {
        HddSlotRef ref = resolveHddSlot(slot);
        if (ref == null) {
            return 0;
        }
        return ref.hdd.getMaxTypes();
    }

    public ItemStack removeHddNetworkItem(int slot, int amount) {
        return extractHddNetworkItem(slot, amount);
    }

    public void setHddNetworkItem(int slot, ItemStack stack) {
        HddSlotRef ref = resolveHddSlot(slot);
        if (ref == null) {
            return;
        }
        ref.hdd.setItem(ref.slot, stack);
        sync();
    }

    public boolean hasHddNetworkSlot(int slot) {
        return resolveHddSlot(slot) != null;
    }

    private HddSlotRef resolveHddSlot(int slot) {
        if (slot < 0 || this.level == null) {
            return null;
        }

        int remaining = slot;
        for (BlockPos hddPos : this.hddPositions) {
            if (!(this.level.getBlockEntity(hddPos) instanceof BeeSXIHddBlockEntity hdd)) {
                continue;
            }

            int size = hdd.getContainerSize();
            if (remaining < size) {
                return new HddSlotRef(hdd, remaining);
            }
            remaining -= size;
        }

        return null;
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
        tag.putInt("InventoryPage", this.inventoryPage);
        tag.putBoolean("Analyzing", this.analyzing);
        tag.putInt("AnalyzeTicksRemaining", this.analyzeTicksRemaining);
        tag.putString("PendingAnalyzeSpecies", this.pendingAnalyzeSpeciesId == null ? "" : this.pendingAnalyzeSpeciesId.toString());
        tag.putFloat("PendingAnalyzeSpeed", this.pendingAnalyzeSpeed);
        tag.putString("PendingAnalyzeActivity", this.pendingAnalyzeActivityId == null ? "" : this.pendingAnalyzeActivityId.toString());
        tag.putLong("UiPowerStored", this.uiPowerStored);
        tag.putLong("UiPowerCapacity", this.uiPowerCapacity);
        tag.putInt("UiAnalyzeProgress", this.uiAnalyzeProgress);

        ListTag analyzed = new ListTag();
        for (Map.Entry<ResourceLocation, AnalyzedBeeTraits> entry : this.analyzedSpecies.entrySet()) {
            CompoundTag speciesTag = new CompoundTag();
            speciesTag.putString("Species", entry.getKey().toString());
            speciesTag.putFloat("Speed", entry.getValue().speed);
            speciesTag.putString("Activity", entry.getValue().activityTypeId == null ? "" : entry.getValue().activityTypeId.toString());
            analyzed.add(speciesTag);
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

        ListTag powerBlocks = new ListTag();
        for (BlockPos powerPos : this.powerSupplyPositions) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", powerPos.getX());
            posTag.putInt("Y", powerPos.getY());
            posTag.putInt("Z", powerPos.getZ());
            powerBlocks.add(posTag);
        }
        tag.put("PowerSupplyPositions", powerBlocks);

        ListTag batteries = new ListTag();
        for (BlockPos powerPos : this.batteryPositions) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", powerPos.getX());
            posTag.putInt("Y", powerPos.getY());
            posTag.putInt("Z", powerPos.getZ());
            batteries.add(posTag);
        }
        tag.put("BatteryPositions", batteries);
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
        this.inventoryPage = Math.max(0, tag.getInt("InventoryPage"));
        this.analyzing = tag.getBoolean("Analyzing");
        this.analyzeTicksRemaining = Math.max(0, tag.getInt("AnalyzeTicksRemaining"));
        this.pendingAnalyzeSpeciesId = ResourceLocation.tryParse(tag.getString("PendingAnalyzeSpecies"));
        this.pendingAnalyzeSpeed = tag.contains("PendingAnalyzeSpeed", Tag.TAG_FLOAT) ? tag.getFloat("PendingAnalyzeSpeed") : 0.0F;
        this.pendingAnalyzeActivityId = ResourceLocation.tryParse(tag.getString("PendingAnalyzeActivity"));
        this.uiPowerStored = tag.contains("UiPowerStored", Tag.TAG_LONG) ? tag.getLong("UiPowerStored") : 0L;
        this.uiPowerCapacity = tag.contains("UiPowerCapacity", Tag.TAG_LONG) ? tag.getLong("UiPowerCapacity") : 0L;
        this.uiAnalyzeProgress = tag.contains("UiAnalyzeProgress", Tag.TAG_INT) ? tag.getInt("UiAnalyzeProgress") : (this.analyzing ? 0 : 100);

        this.analyzedSpecies.clear();
        ListTag analyzed = tag.getList("AnalyzedSpecies", Tag.TAG_COMPOUND);
        for (int i = 0; i < analyzed.size(); i++) {
            CompoundTag speciesTag = analyzed.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(speciesTag.getString("Species"));
            if (id == null) {
                continue;
            }

            float speed = speciesTag.contains("Speed", Tag.TAG_FLOAT) ? speciesTag.getFloat("Speed") : 1.0F;
            ResourceLocation activityId = ResourceLocation.tryParse(speciesTag.getString("Activity"));
            this.analyzedSpecies.put(id, new AnalyzedBeeTraits(speed, activityId));
        }

        if (this.analyzedSpecies.isEmpty()) {
            ListTag legacyAnalyzed = tag.getList("AnalyzedSpecies", Tag.TAG_STRING);
            for (int i = 0; i < legacyAnalyzed.size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(legacyAnalyzed.getString(i));
                if (id != null) {
                    this.analyzedSpecies.put(id, resolveSpeciesDefaults(id));
                }
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

        this.powerSupplyPositions.clear();
        ListTag powerBlocks = tag.getList("PowerSupplyPositions", Tag.TAG_COMPOUND);
        for (int i = 0; i < powerBlocks.size(); i++) {
            CompoundTag posTag = powerBlocks.getCompound(i);
            this.powerSupplyPositions.add(new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z")));
        }

        this.batteryPositions.clear();
        ListTag batteries = tag.getList("BatteryPositions", Tag.TAG_COMPOUND);
        for (int i = 0; i < batteries.size(); i++) {
            CompoundTag posTag = batteries.getCompound(i);
            this.batteryPositions.add(new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z")));
        }

        this.assembledPositions.clear();
        resizeVirtualHives();
        this.inventoryMaxPage = getMaxInventoryPage();
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

    private static final class HddSlotRef {
        final BeeSXIHddBlockEntity hdd;
        final int slot;

        private HddSlotRef(BeeSXIHddBlockEntity hdd, int slot) {
            this.hdd = hdd;
            this.slot = slot;
        }
    }

    private static final class StructureValidationResult {
        final boolean valid;
        final int cpus;
        final int rams;
        final boolean hasAnalyzer;
        final List<BlockPos> hddPositions;
        final List<BlockPos> powerSupplyPositions;
        final List<BlockPos> batteryPositions;
        final List<BlockPos> structurePositions;

        private StructureValidationResult(boolean valid, int cpus, int rams, boolean hasAnalyzer, List<BlockPos> hddPositions, List<BlockPos> powerSupplyPositions, List<BlockPos> batteryPositions, List<BlockPos> structurePositions) {
            this.valid = valid;
            this.cpus = cpus;
            this.rams = rams;
            this.hasAnalyzer = hasAnalyzer;
            this.hddPositions = hddPositions;
            this.powerSupplyPositions = powerSupplyPositions;
            this.batteryPositions = batteryPositions;
            this.structurePositions = structurePositions;
        }

        static StructureValidationResult invalid() {
            return new StructureValidationResult(false, 0, 0, false, List.of(), List.of(), List.of(), List.of());
        }
    }

    private static final class AnalyzedBeeTraits {
        final float speed;
        final ResourceLocation activityTypeId;

        private AnalyzedBeeTraits(float speed, ResourceLocation activityTypeId) {
            this.speed = speed;
            this.activityTypeId = activityTypeId;
        }
    }
}
