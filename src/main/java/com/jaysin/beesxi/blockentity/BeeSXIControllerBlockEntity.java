package com.jaysin.beesxi.blockentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.jaysin.beesxi.BeeSXI;
import com.jaysin.beesxi.blocks.BeeSXIControllerBlock;
import com.jaysin.beesxi.blocks.BeeSXIPartBlock;
import com.jaysin.beesxi.command.BeeSXICommands;
import com.jaysin.beesxi.server.BeeSXIPartType;
import com.jaysin.beesxi.server.BeeSXIServerMenu;

import forestry.api.IForestryApi;
import forestry.api.apiculture.genetics.IBee;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.ForestryActivityTypes;
import forestry.api.core.climate.IClimateManager;
import forestry.api.core.HumidityType;
import forestry.api.core.IProduct;
import forestry.api.core.TemperatureType;
import forestry.api.core.genetics.ClimateHelper;
import forestry.api.core.genetics.IIndividual;
import forestry.api.core.genetics.IGenome;
import forestry.api.core.genetics.alleles.BeeChromosomes;
import forestry.core.engine.genetics.ItemGE;
import forestry.core.platform.util.SpeciesUtil;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

public class BeeSXIControllerBlockEntity extends BlockEntity implements Container, net.minecraft.world.MenuProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static void debugLog(String message, Object... args) {
        if (BeeSXICommands.isDebugModeEnabled()) {
            LOGGER.info(message, args);
        }
    }

    public static final int TAB_ANALYSIS = 0;
    public static final int TAB_VIRTUAL_HIVES = 1;
    public static final int TAB_INVENTORY = 2;
    public static final int TAB_INFO = 3;
    public static final int TAB_BEE_SPECIES = 4;
    public static final int TAB_FLOWERS = 5;
    public static final int TAB_BIOMES = 6;

    public static final int INVENTORY_SLOT_COUNT = 54;
    private static final int SIZE = 1 + INVENTORY_SLOT_COUNT;
    private static final int MIN_MULTIBLOCK_DIM = 3;
    private static final int MAX_MULTIBLOCK_DIM = 15;
    private static final int VALIDATION_INTERVAL = 20;
    private static final int PRODUCTION_INTERVAL = 200;
    private static final int ANALYZE_DURATION_TICKS = 20 * 60 * 5;
    private static final long ANALYZE_RF_COST = 0; //10_000_000L;
    private static final String PAPER_SPECIES_KEY = "BeeSXIAnalyzedSpecies";
    private static final String PAPER_SPECIMENS_KEY = "BeeSXISpecimens";
    private static final String PAPER_BIOMES_KEY = "BeeSXIUnlockedBiomes";
    private static final String PAPER_FLOWERS_KEY = "BeeSXIUnlockedFlowers";
    private static final String PAPER_ALLELES_KEY = "BeeSXIAlleles";
    private static final String WEATHER_BIOME_KEY = "BeeSXIWeatherReportBiome";
    private static final TagKey<Block> FORESTRY_FLOWERS_ROOT_TAG = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("forestry", "flowers"));
    private static final long RF_PER_TICK_INSTANCE = 100L;
    private static final long RF_PER_TICK_CPU = 0L;
    private static final long RF_PER_TICK_RAM = 0L;
    private static final long RF_PER_TICK_CONTROLLER = 0L;
    private static final Set<ResourceLocation> HALF_RATE_ACTIVITY_TYPES = Set.of(ForestryActivityTypes.DIURNAL, ForestryActivityTypes.NOCTURNAL, ForestryActivityTypes.CATHEMERAL);
    private static final Set<ResourceLocation> FULL_RATE_ACTIVITY_TYPES = Set.of(ForestryActivityTypes.METATURNAL);
    private static final Set<ResourceLocation> ONE_TWELFTH_ACTIVITY_TYPES = Set.of(ForestryActivityTypes.CREPUSCULAR);
    private static final int[][] CARDINAL_DIRECTIONS = {
        {1, 0, 0}, {-1, 0, 0},
        {0, 1, 0}, {0, -1, 0},
        {0, 0, 1}, {0, 0, -1}
    };

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final Map<String, AnalyzedBeeTraits> analyzedSpecies = new LinkedHashMap<>();
    private final Set<ResourceLocation> unlockedBiomes = new HashSet<>();
    private final Set<ResourceLocation> unlockedFlowers = new HashSet<>();
    private final List<VirtualHiveConfig> virtualHives = new ArrayList<>();
    private final List<BlockPos> powerSupplyPositions = new ArrayList<>();
    private final List<BlockPos> batteryPositions = new ArrayList<>();
    private final List<BlockPos> exportBusPositions = new ArrayList<>();
    private final Set<BlockPos> assembledPositions = new HashSet<>();

    private boolean formed;
    private boolean hasAnalyzer;
    private int cpuCount;
    private int ramCount;
    private int activeTab = TAB_VIRTUAL_HIVES;
    private boolean analyzing;
    private int analyzeTicksRemaining;
    private long analyzeEnergyRemaining;
    private ResourceLocation pendingAnalyzeSpeciesId;
    private float pendingAnalyzeSpeed;
    private ResourceLocation pendingAnalyzeActivityId;
    private ResourceLocation pendingAnalyzeBiomeId;
    private ResourceLocation pendingAnalyzeFlowerId;
    private Map<String, String> pendingAnalyzeAlleles = Map.of();
    private IGenome pendingAnalyzeGenome;
    private ItemStack pendingAnalyzeBeeStack = ItemStack.EMPTY;
    private long lastSyncedPowerStored = Long.MIN_VALUE;
    private long lastSyncedPowerCapacity = Long.MIN_VALUE;
    private int lastSyncedAnalyzeProgress = Integer.MIN_VALUE;
    private boolean lastSyncedAnalyzing;
    private long uiPowerStored;
    private long uiPowerCapacity;
    private int uiAnalyzeProgress = 100;
    private long uiInstanceRfPerTick;
    private long uiAnalyzeRfPerTick;
    private long uiTotalRfPerTick;
    private int uiDimX;
    private int uiDimY;
    private int uiDimZ;
    private int uiControllerCount;
    private int uiCasingCount;
    private int uiCpuCount;
    private int uiRamCount;
    private int uiAnalyzerCount;
    private int uiPowerSupplyCount;
    private int uiBatteryCount;
    private int uiExportBusCount;
    private int uiInvalidCount;
    private long lastValidationTick;
    private long lastProductionTick;
    private boolean structureDirty = true;
    private boolean machineStoppedForInventoryFull;

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
                case 8 -> BeeSXIControllerBlockEntity.this.analyzing ? 1 : 0;
                case 9 -> BeeSXIControllerBlockEntity.this.uiAnalyzeProgress;
                case 10 -> (int) Math.min(Integer.MAX_VALUE, BeeSXIControllerBlockEntity.this.uiPowerStored);
                case 11 -> (int) Math.min(Integer.MAX_VALUE, BeeSXIControllerBlockEntity.this.uiPowerCapacity);
                case 12 -> (int) Math.min(Integer.MAX_VALUE, BeeSXIControllerBlockEntity.this.uiTotalRfPerTick);
                case 13 -> (int) Math.min(Integer.MAX_VALUE, BeeSXIControllerBlockEntity.this.uiInstanceRfPerTick);
                case 14 -> (int) Math.min(Integer.MAX_VALUE, BeeSXIControllerBlockEntity.this.uiAnalyzeRfPerTick);
                case 15 -> BeeSXIControllerBlockEntity.this.uiDimX;
                case 16 -> BeeSXIControllerBlockEntity.this.uiDimY;
                case 17 -> BeeSXIControllerBlockEntity.this.uiDimZ;
                case 18 -> BeeSXIControllerBlockEntity.this.uiControllerCount;
                case 19 -> BeeSXIControllerBlockEntity.this.uiCasingCount;
                case 20 -> BeeSXIControllerBlockEntity.this.uiCpuCount;
                case 21 -> BeeSXIControllerBlockEntity.this.uiRamCount;
                case 22 -> BeeSXIControllerBlockEntity.this.uiAnalyzerCount;
                case 24 -> BeeSXIControllerBlockEntity.this.uiPowerSupplyCount;
                case 25 -> BeeSXIControllerBlockEntity.this.uiBatteryCount;
                case 26 -> BeeSXIControllerBlockEntity.this.uiInvalidCount;
                case 27 -> BeeSXIControllerBlockEntity.this.uiExportBusCount;
                case 28 -> BeeSXIControllerBlockEntity.this.machineStoppedForInventoryFull ? 1 : 0;
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
                case 8 -> BeeSXIControllerBlockEntity.this.analyzing = value != 0;
                case 9 -> BeeSXIControllerBlockEntity.this.uiAnalyzeProgress = value;
                case 10 -> BeeSXIControllerBlockEntity.this.uiPowerStored = Math.max(0, value);
                case 11 -> BeeSXIControllerBlockEntity.this.uiPowerCapacity = Math.max(0, value);
                case 12 -> BeeSXIControllerBlockEntity.this.uiTotalRfPerTick = Math.max(0, value);
                case 13 -> BeeSXIControllerBlockEntity.this.uiInstanceRfPerTick = Math.max(0, value);
                case 14 -> BeeSXIControllerBlockEntity.this.uiAnalyzeRfPerTick = Math.max(0, value);
                case 15 -> BeeSXIControllerBlockEntity.this.uiDimX = Math.max(0, value);
                case 16 -> BeeSXIControllerBlockEntity.this.uiDimY = Math.max(0, value);
                case 17 -> BeeSXIControllerBlockEntity.this.uiDimZ = Math.max(0, value);
                case 18 -> BeeSXIControllerBlockEntity.this.uiControllerCount = Math.max(0, value);
                case 19 -> BeeSXIControllerBlockEntity.this.uiCasingCount = Math.max(0, value);
                case 20 -> BeeSXIControllerBlockEntity.this.uiCpuCount = Math.max(0, value);
                case 21 -> BeeSXIControllerBlockEntity.this.uiRamCount = Math.max(0, value);
                case 22 -> BeeSXIControllerBlockEntity.this.uiAnalyzerCount = Math.max(0, value);
                case 24 -> BeeSXIControllerBlockEntity.this.uiPowerSupplyCount = Math.max(0, value);
                case 25 -> BeeSXIControllerBlockEntity.this.uiBatteryCount = Math.max(0, value);
                case 26 -> BeeSXIControllerBlockEntity.this.uiInvalidCount = Math.max(0, value);
                case 27 -> BeeSXIControllerBlockEntity.this.uiExportBusCount = Math.max(0, value);
                case 28 -> BeeSXIControllerBlockEntity.this.machineStoppedForInventoryFull = value != 0;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 29;
        }
    };

    public BeeSXIControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BeeSXI.BEESXI_CONTROLLER_BLOCK_ENTITY.get(), pos, state);
    }

    private boolean isFlowerItem(ItemStack stack) {
        return getForestryFlowerIdFromStack(stack) != null;
    }

    private ResourceLocation getForestryFlowerIdFromStack(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }

        BlockState state = blockItem.getBlock().defaultBlockState();
        boolean isForestryFlower = state.is(FORESTRY_FLOWERS_ROOT_TAG)
            || state.getTags().anyMatch(tag -> tag.location().getNamespace().equals("forestry") && tag.location().getPath().startsWith("flowers/"));
        if (!isForestryFlower) {
            return null;
        }

        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
    }

    private ResourceLocation getCurrentBiomeId() {
        if (this.level == null) {
            return null;
        }
        return this.level.getBiome(this.worldPosition)
            .unwrapKey()
            .map(ResourceKey::location)
            .orElse(null);
    }

    private Map<String, String> extractAlleles(IGenome genome) {
        if (genome == null) {
            return Map.of();
        }

        Map<String, String> alleles = new HashMap<>();
        for (java.lang.reflect.Field field : BeeChromosomes.class.getFields()) {
            Object chromosome;
            try {
                chromosome = field.get(null);
            } catch (IllegalAccessException ignored) {
                continue;
            }

            if (chromosome == null) {
                continue;
            }

            Object allele = null;
            try {
                allele = genome.getClass().getMethod("getActiveAllele", chromosome.getClass()).invoke(genome, chromosome);
            } catch (ReflectiveOperationException ignored) {
                // Fall back to the old active-value route when a chromosome does not expose an allele object.
            }

            if (allele == null) {
                allele = invokeGenomeActiveValue(genome, chromosome);
            }
            if (allele == null) {
                continue;
            }

            String alleleId = resolveAlleleIdString(allele);
            if (alleleId != null) {
                alleles.put(field.getName().toLowerCase(java.util.Locale.ROOT), alleleId);
            } else {
                alleles.put(field.getName().toLowerCase(java.util.Locale.ROOT), allele.toString());
            }
        }
        return alleles;
    }

    private Object invokeGenomeActiveValue(IGenome genome, Object chromosome) {
        for (java.lang.reflect.Method method : genome.getClass().getMethods()) {
            if (!"getActiveValue".equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            if (!method.getParameterTypes()[0].isAssignableFrom(chromosome.getClass())) {
                continue;
            }
            try {
                return method.invoke(genome, chromosome);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    private String resolveAlleleIdString(Object value) {
        if (value == null) {
            return null;
        }

        try {
            java.lang.reflect.Method alleleIdMethod = value.getClass().getMethod("alleleId");
            Object alleleId = alleleIdMethod.invoke(value);
            if (alleleId != null) {
                return alleleId.toString();
            }
        } catch (ReflectiveOperationException ignored) {
            // Not every value is an allele instance, so fall back to the original string form.
        }
        return null;
    }

    private Map<String, String> parseAlleles(CompoundTag allelesTag) {
        if (allelesTag == null || allelesTag.isEmpty()) {
            return Map.of();
        }

        Map<String, String> alleles = new HashMap<>();
        for (String key : allelesTag.getAllKeys()) {
            alleles.put(key, allelesTag.getString(key));
        }
        return alleles;
    }

    private ResourceLocation findFlowerFromAlleles(Map<String, String> alleles) {
        for (Map.Entry<String, String> entry : alleles.entrySet()) {
            if (!entry.getKey().contains("flower")) {
                continue;
            }
            ResourceLocation parsed = ResourceLocation.tryParse(entry.getValue());
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private ResourceLocation findFlowerFromGenome(IGenome genome) {
        if (genome == null) {
            return null;
        }

        Object flowerType = invokeGenomeActiveValue(genome, BeeChromosomes.FLOWER_TYPE);
        if (flowerType instanceof ResourceLocation resourceLocation) {
            return resourceLocation;
        }
        if (flowerType instanceof String string) {
            return ResourceLocation.tryParse(string);
        }

        return null;
    }

    private boolean isCompatibleFlower(@javax.annotation.Nullable ResourceLocation selectedFlowerId, @javax.annotation.Nullable ResourceLocation requiredFlowerId) {
        if (selectedFlowerId == null || requiredFlowerId == null) {
            debugLog("Flower compatibility skipped because one of the IDs was null: selected={}, required={}", selectedFlowerId, requiredFlowerId);
            return false;
        }

        ResourceLocation resolvedRequiredFlowerId = resolveFlowerTypeTag(requiredFlowerId);

        if (selectedFlowerId.equals(resolvedRequiredFlowerId)) {
            return true;
        }

        boolean matched = isFlowerInTag(selectedFlowerId, resolvedRequiredFlowerId);
        if (!matched) {
            Set<ResourceLocation> selectedTags = getFlowerTagLocations(selectedFlowerId);
            Set<ResourceLocation> requiredTags = getFlowerTagLocations(resolvedRequiredFlowerId);
            matched = !Collections.disjoint(selectedTags, requiredTags);
        }

        debugLog(
            "Flower compatibility selected={} required={} resolvedRequired={} matched={} selectedTags={} requiredTags={}",
            selectedFlowerId,
            requiredFlowerId,
            resolvedRequiredFlowerId,
            matched,
            getFlowerTagLocations(selectedFlowerId),
            getFlowerTagLocations(resolvedRequiredFlowerId)
        );
        return matched;
    }

    private ResourceLocation resolveFlowerTypeTag(ResourceLocation flowerId) {
        if (flowerId == null) {
            return null;
        }

        if (flowerId.getNamespace().equals("forestry") && flowerId.getPath().startsWith("flower_type_")) {
            return ResourceLocation.fromNamespaceAndPath(flowerId.getNamespace(), "flowers/" + flowerId.getPath().substring("flower_type_".length()));
        }

        return flowerId;
    }

    private boolean isFlowerInTag(ResourceLocation selectedFlowerId, ResourceLocation requiredFlowerId) {
        if (selectedFlowerId == null || requiredFlowerId == null) {
            return false;
        }

        Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(selectedFlowerId).orElse(null);
        if (block != null) {
            TagKey<Block> blockTagKey = TagKey.create(Registries.BLOCK, requiredFlowerId);
            if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getTag(blockTagKey)
                .map(tag -> tag.contains(block.builtInRegistryHolder()))
                .orElse(false)) {
                return true;
            }

            if (block.builtInRegistryHolder().tags().anyMatch(tag -> tag.location().equals(requiredFlowerId))) {
                return true;
            }
        }

        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(selectedFlowerId).orElse(null);
        if (item != null) {
            ItemStack stack = item.getDefaultInstance();
            BlockState state = Block.byItem(item).defaultBlockState();
            if (state != null) {
                Block itemBlock = state.getBlock();
                TagKey<Block> blockTagKey = TagKey.create(Registries.BLOCK, requiredFlowerId);
                if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getTag(blockTagKey)
                    .map(tag -> tag.contains(itemBlock.builtInRegistryHolder()))
                    .orElse(false)) {
                    return true;
                }
            }

            if (item.builtInRegistryHolder().tags().anyMatch(tag -> tag.location().equals(requiredFlowerId))) {
                return true;
            }
        }

        return false;
    }

    private static Set<ResourceLocation> getFlowerTagLocations(ResourceLocation id) {
        Set<ResourceLocation> tags = new HashSet<>();
        if (id == null) {
            return tags;
        }

        tags.add(id);

        if (!id.getPath().startsWith("flowers/")) {
            tags.add(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "flowers/" + id.getPath()));
        }

        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item != null) {
            item.builtInRegistryHolder().tags().forEach(tag -> tags.add(tag.location()));
        }

        Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block != null) {
            block.builtInRegistryHolder().tags().forEach(tag -> tags.add(tag.location()));
        }

        return tags;
    }

    private boolean isBiomeCompatibleWithSpecies(ResourceLocation biomeId, IBeeSpecies species) {
        if (biomeId == null || species == null) {
            debugLog("Biome compatibility skipped because species or biome was null: species={}, biome={}", species, biomeId);
            return false;
        }

        IClimateManager climateManager = IForestryApi.INSTANCE.getClimateManager();
        if (climateManager == null) {
            debugLog("Biome compatibility skipped because the Forestry climate manager was unavailable: species={}, biome={}", species.id(), biomeId);
            return false;
        }

        ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME, biomeId);
        TemperatureType selectedTemperature = climateManager.getTemperature(biomeKey);
        HumidityType selectedHumidity = climateManager.getHumidity(biomeKey);

        IGenome genome = species.createIndividual().getGenome();
        if (genome == null) {
            debugLog("Biome compatibility skipped because the species genome was unavailable: species={}, biome={}", species.id(), biomeId);
            return false;
        }

        boolean compatible = ClimateHelper.isWithinLimits(
            selectedTemperature,
            selectedHumidity,
            species.getTemperature(),
            genome.getActiveValue(BeeChromosomes.TEMPERATURE_TOLERANCE),
            species.getHumidity(),
            genome.getActiveValue(BeeChromosomes.HUMIDITY_TOLERANCE)
        );

        debugLog(
            "Biome compatibility species={} selectedBiome={} selectedClimate=({}/{}) speciesClimate=({}/{}) compatibility={}",
            species.id(),
            biomeId,
            selectedTemperature,
            selectedHumidity,
            species.getTemperature(),
            species.getHumidity(),
            compatible
        );
        return compatible;
    }

    private static boolean isValidSpeciesId(@Nullable ResourceLocation speciesId) {
        return speciesId != null && !speciesId.getNamespace().isEmpty() && !speciesId.getPath().isEmpty();
    }

    private static @Nullable ResourceLocation safeParseSpeciesId(@Nullable String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(rawId);
        return isValidSpeciesId(parsed) ? parsed : null;
    }

    private static @Nullable String normalizeSpecimenKey(@Nullable String specimenKey, @Nullable ResourceLocation speciesId) {
        if (speciesId != null) {
            return speciesId.toString();
        }
        if (specimenKey != null && !specimenKey.isBlank()) {
            return specimenKey;
        }
        return null;
    }

    private static @Nullable IBeeSpecies safeGetBeeSpecies(@Nullable ResourceLocation speciesId) {
        if (!isValidSpeciesId(speciesId)) {
            return null;
        }

        try {
            return SpeciesUtil.getBeeSpecies(speciesId);
        } catch (RuntimeException e) {
            LOGGER.warn("Skipping invalid bee species lookup for {}: {}", speciesId, e.getMessage());
            return null;
        }
    }

    private boolean canRunVirtualHive(VirtualHiveConfig config) {
        if (config == null) {
            debugLog("Virtual hive evaluation skipped: config was null");
            return false;
        }

        AnalyzedBeeTraits specimenTraits = config.specimenKey == null ? null : this.analyzedSpecies.get(config.specimenKey);
        ResourceLocation speciesId = specimenTraits != null ? specimenTraits.speciesId : config.speciesId;

        if (speciesId == null) {
            debugLog("Virtual hive evaluation skipped: species missing for hive instances={} biome={} flower={}", config.instances, config.biomeId, config.flowerItemId);
            return false;
        }
        if (config.instances <= 0) {
            debugLog("Virtual hive evaluation skipped: zero instances for species={} biome={} flower={}", speciesId, config.biomeId, config.flowerItemId);
            return false;
        }
        if (config.biomeId == null) {
            debugLog("Virtual hive evaluation skipped: biome missing for species={} instances={} flower={}", speciesId, config.instances, config.flowerItemId);
            return false;
        }
        if (config.flowerItemId == null) {
            debugLog("Virtual hive evaluation skipped: flower missing for species={} instances={} biome={}", speciesId, config.instances, config.biomeId);
            return false;
        }

        IBeeSpecies species = safeGetBeeSpecies(speciesId);
        if (species == null) {
            debugLog("Virtual hive evaluation skipped: species not found {}", speciesId);
            return false;
        }

        AnalyzedBeeTraits traits = specimenTraits != null ? specimenTraits : getAnalyzedTraitsForSpecies(speciesId);
        if (traits == null) {
            debugLog("Virtual hive evaluation skipped: species {} has no analyzed traits", speciesId);
            return false;
        }

        if (!isBiomeCompatibleWithSpecies(config.biomeId, species)) {
            debugLog("Virtual hive biome compatibility failed for species={} selectedBiome={}", speciesId, config.biomeId);
            return false;
        }

        if (traits.requiredFlowerId != null && !isCompatibleFlower(config.flowerItemId, traits.requiredFlowerId)) {
            debugLog("Virtual hive flower compatibility failed for species={} selectedFlower={} requiredFlower={}", speciesId, config.flowerItemId, traits.requiredFlowerId);
            return false;
        }

        debugLog("Virtual hive compatibility passed for species={} selectedBiome={} selectedFlower={} requiredFlower={}", speciesId, config.biomeId, config.flowerItemId, traits.requiredFlowerId);
        return true;
    }

    private int getRunningInstances() {
        int total = 0;
        for (VirtualHiveConfig config : this.virtualHives) {
            if (canRunVirtualHive(config)) {
                total += Math.max(0, config.instances);
            }
        }
        return total;
    }

    private @Nullable IBee getSavedBeeForSpecies(ResourceLocation speciesId) {
        if (speciesId == null) {
            return null;
        }

        ItemStack beeStack = getBeeStackForSpecies(speciesId);
        if (beeStack.isEmpty()) {
            return null;
        }

        IIndividual individual = ItemGE.getIndividual(beeStack);
        if (individual instanceof IBee bee) {
            return bee;
        }
        return null;
    }

    private boolean hasOutputCapacityForSpecies(ResourceLocation speciesId) {
        IBeeSpecies species = safeGetBeeSpecies(speciesId);
        if (species == null) {
            return false;
        }

        IBee savedBee = getSavedBeeForSpecies(speciesId);
        if (savedBee != null) {
            for (ItemStack productStack : savedBee.getProduceList()) {
                if (!productStack.isEmpty() && canAcceptProducedStack(productStack)) {
                    return true;
                }
            }
            for (ItemStack specialtyStack : savedBee.getSpecialtyList()) {
                if (!specialtyStack.isEmpty() && canAcceptProducedStack(specialtyStack)) {
                    return true;
                }
            }
            return false;
        }

        for (IProduct product : species.getProducts()) {
            if (canAcceptProducedStack(product.createStack())) {
                return true;
            }
        }
        for (IProduct product : species.getSpecialties()) {
            if (canAcceptProducedStack(product.createStack())) {
                return true;
            }
        }
        return false;
    }

    private boolean canAcceptProducedStack(ItemStack stack) {
        if (stack.isEmpty() || this.level == null) {
            return false;
        }

        ItemStack single = stack.copyWithCount(1);
        for (BlockPos exportBusPos : this.exportBusPositions) {
            if (this.level.getBlockEntity(exportBusPos) instanceof BeeSXIExportBusBlockEntity exportBus
                && exportBus.canAcceptIncomingStack(single)) {
                return true;
            }
        }

        for (int i = 1; i < this.items.size(); i++) {
            ItemStack existing = this.items.get(i);
            if (existing.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameComponents(existing, single) && existing.getCount() < existing.getMaxStackSize()) {
                return true;
            }
        }

        return false;
    }

    private AnalyzedBeeTraits resolveSpeciesDefaults(ResourceLocation speciesId) {
        if (!isValidSpeciesId(speciesId)) {
            return new AnalyzedBeeTraits(speciesId, 1.0F, null, null, null, Map.of(), null);
        }

        IBeeSpecies species = safeGetBeeSpecies(speciesId);
        if (species == null) {
            return new AnalyzedBeeTraits(speciesId, 1.0F, null, null, null, Map.of(), null);
        }

        IGenome genome = species.createIndividual().getGenome();
        float speed = genome.getActiveValue(BeeChromosomes.SPEED);
        ResourceLocation activityId = genome.getActiveValue(BeeChromosomes.ACTIVITY);
        Map<String, String> alleles = extractAlleles(genome);
        ResourceLocation flowerId = findFlowerFromAlleles(alleles);
        return new AnalyzedBeeTraits(speciesId, speed, activityId, null, flowerId, alleles, genome);
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) {
            return;
        }

        long gameTime = level.getGameTime();
        if (this.structureDirty || gameTime - this.lastValidationTick >= VALIDATION_INTERVAL) {
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
        updateMachineStoppedForInventoryFull();

        boolean hasRunnableHives = getRunningInstances() > 0;
        boolean hasInstancePower = !hasRunnableHives || consumeInstanceEnergyPerTick();

        if (this.analyzing) {
            tickAnalyzeProcess();
        }

        if (!this.machineStoppedForInventoryFull && hasInstancePower && gameTime - this.lastProductionTick >= PRODUCTION_INTERVAL) {
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

        this.structureDirty = false;

        StructureDiagnostics diagnostics = collectStructureDiagnostics(level);
        this.uiDimX = diagnostics.dimX;
        this.uiDimY = diagnostics.dimY;
        this.uiDimZ = diagnostics.dimZ;
        this.uiControllerCount = diagnostics.controllerCount;
        this.uiCasingCount = diagnostics.casingCount;
        this.uiCpuCount = diagnostics.cpuCount;
        this.uiRamCount = diagnostics.ramCount;
        this.uiAnalyzerCount = diagnostics.analyzerCount;
        this.uiPowerSupplyCount = diagnostics.powerSupplyCount;
        this.uiBatteryCount = diagnostics.batteryCount;
        this.uiExportBusCount = diagnostics.exportBusCount;
        this.uiInvalidCount = diagnostics.invalidCount;

        StructureValidationResult result = diagnostics.result;

        boolean changed = this.formed != result.valid
            || this.cpuCount != result.cpus
            || this.ramCount != result.rams
            || this.hasAnalyzer != result.hasAnalyzer;

        this.formed = result.valid;
        this.cpuCount = result.cpus;
        this.ramCount = result.rams;
        this.hasAnalyzer = result.hasAnalyzer;

        this.powerSupplyPositions.clear();
        this.powerSupplyPositions.addAll(result.powerSupplyPositions);
        this.batteryPositions.clear();
        this.batteryPositions.addAll(result.batteryPositions);
        this.exportBusPositions.clear();
        this.exportBusPositions.addAll(result.exportBusPositions);

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

    private StructureDiagnostics collectStructureDiagnostics(Level level) {
        Set<BlockPos> component = collectConnectedStructureComponent(level);
        if (component.isEmpty()) {
            return new StructureDiagnostics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, StructureValidationResult.invalid(), List.of("No connected multiblock blocks found"));
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
            List<String> issues = new ArrayList<>();
            issues.add("Dimensions out of bounds: " + sizeX + "x" + sizeY + "x" + sizeZ + " (allowed " + MIN_MULTIBLOCK_DIM + "-" + MAX_MULTIBLOCK_DIM + ")");
            return new StructureDiagnostics(sizeX, sizeY, sizeZ, 0, 0, 0, 0, 0, 0, 0, 0, 0, StructureValidationResult.invalid(), issues);
        }

        return validateWithDiagnostics(level, min, sizeX, sizeY, sizeZ);
    }

    private StructureDiagnostics validateWithDiagnostics(Level level, BlockPos minPos, int sizeX, int sizeY, int sizeZ) {
        int cpus = 0;
        int rams = 0;
        int analyzers = 0;
        int controllerCount = 0;
        int casings = 0;
        int powerSupplies = 0;
        int batteries = 0;
        int exportBuses = 0;
        int invalidBlocks = 0;

        List<String> issues = new ArrayList<>();
        List<BlockPos> foundPowerSupplies = new ArrayList<>();
        List<BlockPos> foundBatteries = new ArrayList<>();
        List<BlockPos> foundExportBuses = new ArrayList<>();
        List<BlockPos> structurePositions = new ArrayList<>(sizeX * sizeY * sizeZ);

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    BlockPos scanPos = minPos.offset(x, y, z);
                    BlockState scanState = level.getBlockState(scanPos);
                    structurePositions.add(scanPos.immutable());

                    if (scanPos.equals(this.worldPosition)) {
                        if (!(scanState.getBlock() instanceof BeeSXIControllerBlock)) {
                            issues.add("Controller position is not a controller block");
                            invalidBlocks++;
                            continue;
                        }
                        controllerCount++;
                        continue;
                    }

                    if (!(scanState.getBlock() instanceof BeeSXIPartBlock partBlock)) {
                        issues.add("Missing or invalid block at " + posToString(scanPos));
                        invalidBlocks++;
                        continue;
                    }

                    BeeSXIPartType partType = partBlock.getPartType();
                    int boundaryAxes = (x == 0 || x == sizeX - 1 ? 1 : 0)
                        + (y == 0 || y == sizeY - 1 ? 1 : 0)
                        + (z == 0 || z == sizeZ - 1 ? 1 : 0);
                    boolean isEdgeOrCorner = boundaryAxes >= 2;
                    if (isEdgeOrCorner && partType != BeeSXIPartType.CASING) {
                        issues.add("Edge/corner at " + posToString(scanPos) + " must be casing");
                        invalidBlocks++;
                    }

                    switch (partType) {
                        case CPU -> cpus++;
                        case RAM -> rams++;
                        case POWER_SUPPLY -> {
                            powerSupplies++;
                            foundPowerSupplies.add(scanPos.immutable());
                        }
                        case BATTERY -> {
                            batteries++;
                            foundBatteries.add(scanPos.immutable());
                        }
                        case EXPORT_BUS -> {
                            exportBuses++;
                            foundExportBuses.add(scanPos.immutable());
                        }
                        case MOLECULAR_ANALYZER -> analyzers++;
                        case CASING -> casings++;
                    }
                }
            }
        }

        int controllerBoundaryAxes = (this.worldPosition.getX() == minPos.getX() || this.worldPosition.getX() == minPos.getX() + sizeX - 1 ? 1 : 0)
            + (this.worldPosition.getY() == minPos.getY() || this.worldPosition.getY() == minPos.getY() + sizeY - 1 ? 1 : 0)
            + (this.worldPosition.getZ() == minPos.getZ() || this.worldPosition.getZ() == minPos.getZ() + sizeZ - 1 ? 1 : 0);
        if (controllerBoundaryAxes != 1) {
            issues.add("Controller must be on a non-edge exterior face");
        }
        if (controllerCount != 1) {
            issues.add("Expected exactly 1 controller, found " + controllerCount);
        }
        if (cpus < 1) {
            issues.add("Missing required block type: CPU");
        }
        if (rams < 1) {
            issues.add("Missing required block type: RAM");
        }
        if (powerSupplies < 1) {
            issues.add("Missing required block type: POWER_SUPPLY");
        }
        if (batteries < 1) {
            issues.add("No BATTERY detected (optional, but recommended for energy buffering)");
        }

        boolean valid = issues.stream().noneMatch(issue -> !issue.startsWith("No BATTERY detected"));
        StructureValidationResult result = valid
            ? new StructureValidationResult(true, cpus, rams, analyzers > 0, foundPowerSupplies, foundBatteries, foundExportBuses, structurePositions)
            : StructureValidationResult.invalid();

        return new StructureDiagnostics(
            sizeX,
            sizeY,
            sizeZ,
            controllerCount,
            casings,
            cpus,
            rams,
            analyzers,
            powerSupplies,
            batteries,
            exportBuses,
            invalidBlocks,
            result,
            issues
        );
    }

    public void sendStructureDiagnosticsTo(Player player) {
        StructureDiagnostics diagnostics = collectStructureDiagnostics(this.level);
        player.sendSystemMessage(Component.literal("BeeSXI structure diagnostics:"));
        player.sendSystemMessage(Component.literal("Dimensions: " + diagnostics.dimX + "x" + diagnostics.dimY + "x" + diagnostics.dimZ));
        player.sendSystemMessage(Component.literal(
            "Counts: controller=" + diagnostics.controllerCount
                + " casing=" + diagnostics.casingCount
                + " cpu=" + diagnostics.cpuCount
                + " ram=" + diagnostics.ramCount
                + " analyzer=" + diagnostics.analyzerCount
                + " power_supply=" + diagnostics.powerSupplyCount
                + " battery=" + diagnostics.batteryCount
                + " export_bus=" + diagnostics.exportBusCount
                + " invalid=" + diagnostics.invalidCount
        ));

        if (diagnostics.issues.isEmpty()) {
            player.sendSystemMessage(Component.literal("No missing/invalid blocks found."));
            return;
        }

        int maxLines = Math.min(8, diagnostics.issues.size());
        for (int i = 0; i < maxLines; i++) {
            player.sendSystemMessage(Component.literal("- " + diagnostics.issues.get(i)));
        }
        if (diagnostics.issues.size() > maxLines) {
            player.sendSystemMessage(Component.literal("...and " + (diagnostics.issues.size() - maxLines) + " more issues"));
        }
    }

    private static String posToString(BlockPos pos) {
        return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
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

    private void resizeVirtualHives() {
        boolean changed = false;

        while (this.virtualHives.size() < this.cpuCount) {
            int newIndex = this.virtualHives.size();
            this.virtualHives.add(new VirtualHiveConfig(null, null, null, newIndex == 0 ? 1 : 0));
            changed = true;
        }
        while (this.virtualHives.size() > this.cpuCount) {
            this.virtualHives.remove(this.virtualHives.size() - 1);
            changed = true;
        }

        for (VirtualHiveConfig config : this.virtualHives) {
            int previousInstances = config.instances;
            config.instances = Math.max(0, config.instances);
            if (config.instances != previousInstances) {
                changed = true;
            }

            if (config.speciesId != null) {
                String previousSpecimen = config.specimenKey;
                config.specimenKey = config.speciesId.toString();
                if (!java.util.Objects.equals(previousSpecimen, config.specimenKey)) {
                    changed = true;
                }
            } else if (config.specimenKey != null) {
                AnalyzedBeeTraits traits = this.analyzedSpecies.get(config.specimenKey);
                if (traits != null && traits.speciesId != null) {
                    config.speciesId = traits.speciesId;
                    String previousSpecimen = config.specimenKey;
                    config.specimenKey = traits.speciesId.toString();
                    if (!java.util.Objects.equals(previousSpecimen, config.specimenKey)) {
                        changed = true;
                    }
                } else {
                    config.specimenKey = null;
                    config.speciesId = null;
                    changed = true;
                }
            }
            if (config.specimenKey != null && config.speciesId != null && !config.specimenKey.equals(config.speciesId.toString())) {
                config.specimenKey = config.speciesId.toString();
                changed = true;
            }
            if (config.specimenKey != null && !this.analyzedSpecies.containsKey(config.specimenKey)) {
                config.specimenKey = null;
                config.speciesId = null;
                changed = true;
            }
            if (config.biomeId != null && !this.unlockedBiomes.contains(config.biomeId)) {
                config.biomeId = null;
                changed = true;
            }
            if (config.flowerItemId != null && !this.unlockedFlowers.contains(config.flowerItemId)) {
                config.flowerItemId = null;
                changed = true;
            }
        }

        enforceTotalRamLimit();
        if (changed) {
            this.setChanged();
        }
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

    public void clearAssembledStateForConnectedStructure() {
        Level level = this.level;
        if (level == null) {
            return;
        }

        Set<BlockPos> connected = collectConnectedStructureComponent(level);
        for (BlockPos pos : connected) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BeeSXIControllerBlock && state.getValue(BeeSXIControllerBlock.ASSEMBLED)) {
                level.setBlock(pos, state.setValue(BeeSXIControllerBlock.ASSEMBLED, false), 3);
            } else if (state.getBlock() instanceof BeeSXIPartBlock && state.getValue(BeeSXIPartBlock.ASSEMBLED)) {
                level.setBlock(pos, state.setValue(BeeSXIPartBlock.ASSEMBLED, false), 3);
            }
        }

        this.assembledPositions.clear();
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
        debugLog("BeeSXI button press: player={}, controller={}, buttonId={}", player.getGameProfile().getName(), this.worldPosition, id);

        if (id >= 0 && id <= TAB_BIOMES) {
            this.activeTab = id;
            sync();
            return true;
        }

        if (id == 9000) {
            startAnalyzeOne();
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
                case 4 -> cycleBiome(line, -1);
                case 5 -> cycleBiome(line, 1);
                case 6 -> cycleFlower(line, -1);
                case 7 -> cycleFlower(line, 1);
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
        VirtualHiveConfig config = this.virtualHives.get(line);
        List<String> specimenKeys = getAnalyzedSpecimenKeys();
        if (specimenKeys.isEmpty()) {
            config.specimenKey = null;
            config.speciesId = null;
            config.flowerItemId = null;
            return;
        }

        int currentIndex = config.specimenKey == null ? -1 : specimenKeys.indexOf(config.specimenKey);
        int nextIndex;
        if (currentIndex < 0) {
            nextIndex = direction < 0 ? specimenKeys.size() - 1 : 0;
        } else {
            nextIndex = (currentIndex + direction + specimenKeys.size()) % specimenKeys.size();
        }

        config.specimenKey = specimenKeys.get(nextIndex);
        AnalyzedBeeTraits traits = this.analyzedSpecies.get(config.specimenKey);
        config.speciesId = traits == null ? null : traits.speciesId;

        List<ResourceLocation> compatibleFlowers = getSelectableFlowerIds(config);
        debugLog("Virtual hive specimen changed: line={} specimenKey={} species={} compatibleFlowers={}", line, config.specimenKey, config.speciesId, compatibleFlowers);
        if (compatibleFlowers.isEmpty()) {
            config.flowerItemId = null;
            return;
        }

        if (config.flowerItemId == null || !compatibleFlowers.contains(config.flowerItemId)) {
            config.flowerItemId = compatibleFlowers.get(0);
        }
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

    private void cycleBiome(int line, int direction) {
        VirtualHiveConfig config = this.virtualHives.get(line);
        List<ResourceLocation> biomes = getUnlockedBiomeIds();
        if (biomes.isEmpty()) {
            config.biomeId = null;
            return;
        }

        int index;
        if (config.biomeId == null) {
            index = direction < 0 ? biomes.size() - 1 : 0;
        } else {
            index = biomes.indexOf(config.biomeId);
            if (index < 0) {
                index = 0;
            } else {
                index = (index + direction + biomes.size()) % biomes.size();
            }
        }
        config.biomeId = biomes.get(index);
    }

    private void cycleFlower(int line, int direction) {
        VirtualHiveConfig config = this.virtualHives.get(line);
        List<ResourceLocation> flowers = getSelectableFlowerIds(config);
        debugLog("Virtual hive flower selection changed: line={} currentFlower={} selectableFlowers={}", line, config.flowerItemId, flowers);
        if (flowers.isEmpty()) {
            config.flowerItemId = null;
            return;
        }

        int index;
        if (config.flowerItemId == null) {
            index = direction < 0 ? flowers.size() - 1 : 0;
        } else {
            index = flowers.indexOf(config.flowerItemId);
            if (index < 0) {
                index = 0;
            } else {
                index = (index + direction + flowers.size()) % flowers.size();
            }
        }
        config.flowerItemId = flowers.get(index);
    }

    private List<ResourceLocation> getSelectableFlowerIds(VirtualHiveConfig config) {
        List<ResourceLocation> unlockedFlowers = getUnlockedFlowerIds();
        if (config == null) {
            return unlockedFlowers;
        }

        ResourceLocation resolvedSpeciesId = config.speciesId;
        if (config.specimenKey != null) {
            AnalyzedBeeTraits specimenTraits = this.analyzedSpecies.get(config.specimenKey);
            if (specimenTraits != null) {
                resolvedSpeciesId = specimenTraits.speciesId;
            }
        }
        if (resolvedSpeciesId == null) {
            return unlockedFlowers;
        }

        AnalyzedBeeTraits traits = getAnalyzedTraitsForSpecies(resolvedSpeciesId);
        if (traits == null || traits.requiredFlowerId == null) {
            debugLog("Virtual hive flower selection has no species flower requirement: species={} unlockedFlowers={}", resolvedSpeciesId, unlockedFlowers);
            return unlockedFlowers;
        }

        List<ResourceLocation> compatibleFlowers = new ArrayList<>();
        for (ResourceLocation flowerId : unlockedFlowers) {
            if (isCompatibleFlower(flowerId, traits.requiredFlowerId)) {
                compatibleFlowers.add(flowerId);
            }
        }
        debugLog("Virtual hive compatible flowers for species={} requiredFlower={} -> {}", resolvedSpeciesId, traits.requiredFlowerId, compatibleFlowers);
        return compatibleFlowers;
    }

    private void startAnalyzeOne() {
        if (!this.hasAnalyzer || this.analyzing || !this.formed) {
            return;
        }

        ItemStack stack = this.items.get(0);
        if (stack.isEmpty()) {
            return;
        }

        if (stack.is(Items.PAPER)) {
            if (handlePaperAnalysis(stack)) {
                sync();
            }
            return;
        }

        if (isFlowerItem(stack)) {
            ResourceLocation flowerId = getForestryFlowerIdFromStack(stack);
            if (flowerId == null) {
                return;
            }
            this.pendingAnalyzeSpeciesId = null;
            this.pendingAnalyzeSpeed = 0.0F;
            this.pendingAnalyzeActivityId = null;
            this.pendingAnalyzeBiomeId = null;
            this.pendingAnalyzeFlowerId = flowerId;
            this.pendingAnalyzeAlleles = Map.of();
            this.analyzing = true;
            this.analyzeTicksRemaining = ANALYZE_DURATION_TICKS;
            this.analyzeEnergyRemaining = ANALYZE_RF_COST;
            sync();
            return;
        }

        if (!ItemGE.isIndividual(stack)) {
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
        ResourceLocation biomeId = getCurrentBiomeId();
        ResourceLocation flowerId = findFlowerFromGenome(genome);
        if (flowerId == null) {
            flowerId = findFlowerFromAlleles(extractAlleles(genome));
        }

        this.pendingAnalyzeSpeciesId = id;
        this.pendingAnalyzeSpeed = speed;
        this.pendingAnalyzeActivityId = activityId;
        this.pendingAnalyzeBiomeId = biomeId;
        this.pendingAnalyzeFlowerId = flowerId;
        this.pendingAnalyzeAlleles = extractAlleles(genome);
        this.pendingAnalyzeGenome = genome;
        this.pendingAnalyzeBeeStack = stack.copy();
        this.analyzing = true;
        this.analyzeTicksRemaining = ANALYZE_DURATION_TICKS;
        this.analyzeEnergyRemaining = ANALYZE_RF_COST;
        sync();
    }

    private boolean handlePaperAnalysis(ItemStack stack) {
        HolderLookup.Provider provider = this.level != null ? this.level.registryAccess() : null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag customTag = data == null ? new CompoundTag() : data.copyTag();

        if (customTag.contains(WEATHER_BIOME_KEY, Tag.TAG_STRING)) {
            ResourceLocation weatherBiome = ResourceLocation.tryParse(customTag.getString(WEATHER_BIOME_KEY));
            if (weatherBiome != null && this.unlockedBiomes.add(weatherBiome)) {
                LOGGER.info("Analyzer imported weather report: controller={}, biome={}", this.worldPosition, weatherBiome);
                return true;
            }
            return false;
        }

        if (customTag.contains(PAPER_SPECIMENS_KEY, Tag.TAG_LIST)) {
            ListTag specimenList = customTag.getList(PAPER_SPECIMENS_KEY, Tag.TAG_COMPOUND);
            ListTag biomeList = customTag.getList(PAPER_BIOMES_KEY, Tag.TAG_STRING);
            ListTag flowerList = customTag.getList(PAPER_FLOWERS_KEY, Tag.TAG_STRING);
            int unlocked = 0;
            boolean changed = false;
            for (int i = 0; i < specimenList.size(); i++) {
                CompoundTag specimenTag = specimenList.getCompound(i);
                ResourceLocation id = safeParseSpeciesId(specimenTag.getString("Species"));
                if (id == null) {
                    continue;
                }

                String key = specimenTag.contains("Key", Tag.TAG_STRING) && !specimenTag.getString("Key").isEmpty()
                    ? specimenTag.getString("Key")
                    : id.toString();

                if (!this.analyzedSpecies.containsKey(key)) {
                    float speed = specimenTag.contains("Speed", Tag.TAG_FLOAT) ? specimenTag.getFloat("Speed") : resolveSpeciesDefaults(id).speed;
                    ResourceLocation activityId = ResourceLocation.tryParse(specimenTag.getString("Activity"));
                    ResourceLocation biomeId = ResourceLocation.tryParse(specimenTag.getString("Biome"));
                    ResourceLocation flowerId = ResourceLocation.tryParse(specimenTag.getString("Flower"));
                    Map<String, String> alleles = specimenTag.contains(PAPER_ALLELES_KEY, Tag.TAG_COMPOUND)
                        ? parseAlleles(specimenTag.getCompound(PAPER_ALLELES_KEY))
                        : resolveSpeciesDefaults(id).alleles;
                    ItemStack beeStack = specimenTag.contains("BeeStack", Tag.TAG_COMPOUND)
                        ? ItemStack.parseOptional(provider, specimenTag.getCompound("BeeStack"))
                        : ItemStack.EMPTY;
                    this.analyzedSpecies.put(key, new AnalyzedBeeTraits(id, speed, activityId, biomeId, flowerId, alleles, null, beeStack));
                    unlocked++;
                    changed = true;
                }
            }

            for (int i = 0; i < biomeList.size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(biomeList.getString(i));
                if (id != null && this.unlockedBiomes.add(id)) {
                    changed = true;
                }
            }
            for (int i = 0; i < flowerList.size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(flowerList.getString(i));
                if (id != null && this.unlockedFlowers.add(id)) {
                    changed = true;
                }
            }

            if (changed) {
                LOGGER.info("Analyzer imported specimen card: controller={}, unlocked={}", this.worldPosition, unlocked);
                this.setChanged();
                return true;
            }
            return false;
        }

        if (customTag.contains(PAPER_SPECIES_KEY, Tag.TAG_LIST)) {
            ListTag speciesList = customTag.getList(PAPER_SPECIES_KEY, Tag.TAG_STRING);
            ListTag biomeList = customTag.getList(PAPER_BIOMES_KEY, Tag.TAG_STRING);
            ListTag flowerList = customTag.getList(PAPER_FLOWERS_KEY, Tag.TAG_STRING);
            int unlocked = 0;
            boolean changed = false;
            for (int i = 0; i < speciesList.size(); i++) {
                ResourceLocation id = safeParseSpeciesId(speciesList.getString(i));
                if (id == null) {
                    continue;
                }

                String legacyKey = id.toString();
                if (this.analyzedSpecies.values().stream().noneMatch(traits -> id.equals(traits.speciesId))) {
                    this.analyzedSpecies.put(legacyKey, resolveSpeciesDefaults(id));
                    unlocked++;
                    changed = true;
                }
            }

            for (int i = 0; i < biomeList.size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(biomeList.getString(i));
                if (id != null && this.unlockedBiomes.add(id)) {
                    changed = true;
                }
            }
            for (int i = 0; i < flowerList.size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(flowerList.getString(i));
                if (id != null && this.unlockedFlowers.add(id)) {
                    changed = true;
                }
            }

            if (changed) {
                LOGGER.info("Analyzer imported species card: controller={}, unlocked={}", this.worldPosition, unlocked);
                return true;
            }
            return false;
        }

        ListTag speciesList = new ListTag();
        for (AnalyzedBeeTraits traits : this.analyzedSpecies.values()) {
            if (traits.speciesId != null) {
                boolean alreadyPresent = false;
                for (Tag tagValue : speciesList) {
                    if (tagValue instanceof StringTag stringTag && stringTag.getAsString().equals(traits.speciesId.toString())) {
                        alreadyPresent = true;
                        break;
                    }
                }
                if (!alreadyPresent) {
                    speciesList.add(StringTag.valueOf(traits.speciesId.toString()));
                }
            }
        }
        customTag.put(PAPER_SPECIES_KEY, speciesList);

        ListTag specimenList = new ListTag();
        for (Map.Entry<String, AnalyzedBeeTraits> entry : this.analyzedSpecies.entrySet()) {
            AnalyzedBeeTraits traits = entry.getValue();
            if (traits == null || traits.speciesId == null) {
                continue;
            }

            CompoundTag specimenTag = new CompoundTag();
            specimenTag.putString("Key", entry.getKey());
            specimenTag.putString("Species", traits.speciesId.toString());
            specimenTag.putFloat("Speed", traits.speed);
            specimenTag.putString("Activity", traits.activityTypeId == null ? "" : traits.activityTypeId.toString());
            specimenTag.putString("Biome", traits.requiredBiomeId == null ? "" : traits.requiredBiomeId.toString());
            specimenTag.putString("Flower", traits.requiredFlowerId == null ? "" : traits.requiredFlowerId.toString());

            CompoundTag allelesTag = new CompoundTag();
            for (Map.Entry<String, String> allele : traits.alleles.entrySet()) {
                allelesTag.putString(allele.getKey(), allele.getValue());
            }
            specimenTag.put(PAPER_ALLELES_KEY, allelesTag);
            specimenTag.put("BeeStack", traits.beeStack.saveOptional(provider));
            specimenList.add(specimenTag);
        }
        customTag.put(PAPER_SPECIMENS_KEY, specimenList);

        ListTag biomeList = new ListTag();
        for (ResourceLocation biomeId : getUnlockedBiomeIds()) {
            biomeList.add(StringTag.valueOf(biomeId.toString()));
        }
        customTag.put(PAPER_BIOMES_KEY, biomeList);

        ListTag flowerList = new ListTag();
        for (ResourceLocation flowerId : getUnlockedFlowerIds()) {
            flowerList.add(StringTag.valueOf(flowerId.toString()));
        }
        customTag.put(PAPER_FLOWERS_KEY, flowerList);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        stack.set(DataComponents.RARITY, Rarity.RARE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Export Report"));

        LOGGER.info("Analyzer exported species card: controller={}, speciesCount={}", this.worldPosition, this.analyzedSpecies.size());
        return true;
    }

    private void tickAnalyzeProcess() {
        if (!this.analyzing || !this.formed) {
            return;
        }

        if (this.analyzeTicksRemaining <= 0) {
            finishAnalyzeProcess();
            return;
        }

        long perTickCost = getAnalyzePerTickCost();
        if (perTickCost > 0 && !consumeEnergy(perTickCost, true)) {
            return;
        }
        if (perTickCost > 0) {
            consumeEnergy(perTickCost, false);
            this.analyzeEnergyRemaining = Math.max(0L, this.analyzeEnergyRemaining - perTickCost);
        }

        this.analyzeTicksRemaining = Math.max(0, this.analyzeTicksRemaining - 1);
        if (this.analyzeTicksRemaining > 0) {
            return;
        }

        finishAnalyzeProcess();
    }

    private long getAnalyzePerTickCost() {
        if (this.analyzeEnergyRemaining <= 0L) {
            return 0L;
        }
        int ticks = Math.max(1, this.analyzeTicksRemaining);
        return Math.max(1L, (this.analyzeEnergyRemaining + ticks - 1L) / ticks);
    }

    private void finishAnalyzeProcess() {
        if (!this.analyzing) {
            return;
        }

        if (this.pendingAnalyzeSpeciesId != null && isValidSpeciesId(this.pendingAnalyzeSpeciesId)) {
            String key = this.pendingAnalyzeSpeciesId.toString();
            AnalyzedBeeTraits previous = this.analyzedSpecies.put(key, new AnalyzedBeeTraits(this.pendingAnalyzeSpeciesId, this.pendingAnalyzeSpeed, this.pendingAnalyzeActivityId, this.pendingAnalyzeBiomeId, this.pendingAnalyzeFlowerId, this.pendingAnalyzeAlleles, this.pendingAnalyzeGenome, this.pendingAnalyzeBeeStack.copy()));
            if (previous == null || previous.speed != this.pendingAnalyzeSpeed || !java.util.Objects.equals(previous.activityTypeId, this.pendingAnalyzeActivityId) || !java.util.Objects.equals(previous.requiredBiomeId, this.pendingAnalyzeBiomeId) || !java.util.Objects.equals(previous.requiredFlowerId, this.pendingAnalyzeFlowerId)) {
                LOGGER.info("Bee analyzed: controller={}, species={}, key={}, speed={}, activity={}", this.worldPosition, this.pendingAnalyzeSpeciesId, key, this.pendingAnalyzeSpeed, this.pendingAnalyzeActivityId);
            }
            if (this.pendingAnalyzeBiomeId != null) {
                this.unlockedBiomes.add(this.pendingAnalyzeBiomeId);
            }
        } else if (this.pendingAnalyzeFlowerId != null) {
            this.unlockedFlowers.add(this.pendingAnalyzeFlowerId);
        }

        this.pendingAnalyzeSpeciesId = null;
        this.pendingAnalyzeActivityId = null;
        this.pendingAnalyzeBiomeId = null;
        this.pendingAnalyzeFlowerId = null;
        this.pendingAnalyzeAlleles = Map.of();
        this.pendingAnalyzeGenome = null;
        this.pendingAnalyzeBeeStack = ItemStack.EMPTY;
        this.pendingAnalyzeSpeed = 0.0F;
        this.analyzing = false;
        this.analyzeTicksRemaining = 0;
        this.analyzeEnergyRemaining = 0L;
        sync();
    }

    private double getActivityMultiplier(AnalyzedBeeTraits traits) {
        if (traits == null || traits.activityTypeId == null) {
            return 1.0D;
        }
        if (HALF_RATE_ACTIVITY_TYPES.contains(traits.activityTypeId)) {
            return 0.5D;
        }
        if (FULL_RATE_ACTIVITY_TYPES.contains(traits.activityTypeId)) {
            return 1.0D;
        }
        if (ONE_TWELFTH_ACTIVITY_TYPES.contains(traits.activityTypeId)) {
            return 1.0D / 12.0D;
        }
        return 1.0D;
    }

    private void updateMachineStoppedForInventoryFull() {
        boolean stopped = false;
        for (VirtualHiveConfig config : this.virtualHives) {
            if (!canRunVirtualHive(config)) {
                continue;
            }

            IBeeSpecies species = safeGetBeeSpecies(config.speciesId);
            if (species == null) {
                continue;
            }

            if (!hasOutputCapacityForSpecies(config.speciesId)) {
                stopped = true;
                break;
            }
        }

        if (this.machineStoppedForInventoryFull != stopped) {
            this.machineStoppedForInventoryFull = stopped;
            sync();
        }
    }

    private void produceVirtualHiveDrops() {
        Level level = this.level;
        if (level == null || !this.formed) {
            return;
        }

        for (VirtualHiveConfig config : this.virtualHives) {
            if (!canRunVirtualHive(config)) {
                continue;
            }

            IBeeSpecies species = safeGetBeeSpecies(config.speciesId);
            if (species == null) {
                continue;
            }

            if (!hasOutputCapacityForSpecies(config.speciesId)) {
                continue;
            }

            AnalyzedBeeTraits traits = config.specimenKey == null ? this.analyzedSpecies.get(config.speciesId) : this.analyzedSpecies.get(config.specimenKey);
            double activityMultiplier = getActivityMultiplier(traits);

            for (int i = 0; i < config.instances; i++) {
                produceForSpecies(config.speciesId, level, activityMultiplier);
            }
        }

        sync();
    }

    private boolean consumeInstanceEnergyPerTick() {
        long perTick = getInstancesPerTickCost();
        if (perTick <= 0L) {
            return true;
        }

        if (!consumeEnergy(perTick, true)) {
            return false;
        }

        consumeEnergy(perTick, false);
        return true;
    }

    private long getInstancesPerTickCost() {
        return (long) getRunningInstances() * RF_PER_TICK_INSTANCE;
    }

    private void produceForSpecies(ResourceLocation speciesId, Level level, double activityMultiplier) {
        IBee savedBee = getSavedBeeForSpecies(speciesId);
        if (savedBee != null) {
            for (ItemStack stack : savedBee.getProduceList()) {
                if (stack.isEmpty()) {
                    continue;
                }
                double adjustedChance = 1.0D * activityMultiplier;
                if (level.random.nextDouble() <= adjustedChance) {
                    insertProducedStack(stack.copy());
                }
            }
            for (ItemStack stack : savedBee.getSpecialtyList()) {
                if (stack.isEmpty()) {
                    continue;
                }
                double adjustedChance = 1.0D * activityMultiplier;
                if (level.random.nextDouble() <= adjustedChance) {
                    insertProducedStack(stack.copy());
                }
            }
            return;
        }

        IBeeSpecies species = safeGetBeeSpecies(speciesId);
        if (species == null) {
            return;
        }

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

        for (BlockPos exportBusPos : this.exportBusPositions) {
            if (remaining.isEmpty()) {
                break;
            }
            if (this.level.getBlockEntity(exportBusPos) instanceof BeeSXIExportBusBlockEntity exportBus) {
                remaining = exportBus.routeIncomingStack(remaining);
            }
        }

        ItemStack originalRemaining = remaining.copy();
        for (int i = 1; i < this.items.size() && !remaining.isEmpty(); i++) {
            ItemStack existing = this.items.get(i);
            if (existing.isEmpty()) {
                this.items.set(i, remaining.copy());
                remaining = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(existing, remaining)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                if (space > 0) {
                    int transfer = Math.min(space, remaining.getCount());
                    existing.grow(transfer);
                    remaining.shrink(transfer);
                }
            }
        }
        if (remaining.getCount() != originalRemaining.getCount()) {
            setChanged();
        }

        if (!remaining.isEmpty() && this.level instanceof ServerLevel serverLevel) {
            net.minecraft.world.Containers.dropItemStack(serverLevel, this.worldPosition.getX(), this.worldPosition.getY() + 1, this.worldPosition.getZ(), remaining);
        }
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
        this.uiInstanceRfPerTick = getInstancesPerTickCost();
        this.uiAnalyzeRfPerTick = this.analyzing ? getAnalyzePerTickCost() : 0L;
        this.uiTotalRfPerTick = this.uiInstanceRfPerTick + this.uiAnalyzeRfPerTick;

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
        List<ResourceLocation> ids = new ArrayList<>();
        for (AnalyzedBeeTraits traits : this.analyzedSpecies.values()) {
            if (traits != null && traits.speciesId != null && !ids.contains(traits.speciesId)) {
                ids.add(traits.speciesId);
            }
        }
        return ids;
    }

    public List<ResourceLocation> getUnlockedBiomeIds() {
        List<ResourceLocation> list = new ArrayList<>(this.unlockedBiomes);
        list.sort(Comparator.comparing(ResourceLocation::toString));
        return list;
    }

    public List<ResourceLocation> getUnlockedFlowerIds() {
        List<ResourceLocation> list = new ArrayList<>(this.unlockedFlowers);
        list.sort(Comparator.comparing(ResourceLocation::toString));
        return list;
    }

    private @Nullable AnalyzedBeeTraits getAnalyzedTraitsForSpecies(ResourceLocation speciesId) {
        if (!isValidSpeciesId(speciesId)) {
            return null;
        }
        for (AnalyzedBeeTraits traits : this.analyzedSpecies.values()) {
            if (speciesId.equals(traits.speciesId)) {
                return traits;
            }
        }
        return null;
    }

    public float getSpeedForSpecies(ResourceLocation speciesId) {
        if (!isValidSpeciesId(speciesId)) {
            return 1.0F;
        }
        AnalyzedBeeTraits traits = getAnalyzedTraitsForSpecies(speciesId);
        if (traits != null) {
            return traits.speed;
        }
        return resolveSpeciesDefaults(speciesId).speed;
    }

    public float getSpeedForSpecimenKey(String specimenKey) {
        if (specimenKey == null) {
            return 0.0F;
        }
        AnalyzedBeeTraits traits = this.analyzedSpecies.get(specimenKey);
        if (traits != null) {
            return traits.speed;
        }
        return 0.0F;
    }

    public Map<String, String> getAllelesForSpecies(ResourceLocation speciesId) {
        if (!isValidSpeciesId(speciesId)) {
            return Map.of();
        }
        AnalyzedBeeTraits traits = getAnalyzedTraitsForSpecies(speciesId);
        if (traits == null) {
            return Map.of();
        }
        return traits.alleles;
    }

    public ItemStack getBeeStackForSpecies(ResourceLocation speciesId) {
        if (!isValidSpeciesId(speciesId)) {
            return ItemStack.EMPTY;
        }
        AnalyzedBeeTraits traits = getAnalyzedTraitsForSpecies(speciesId);
        if (traits == null) {
            return ItemStack.EMPTY;
        }
        if (!traits.beeStack.isEmpty()) {
            return traits.beeStack.copy();
        }
        return ItemStack.EMPTY;
    }

    public ResourceLocation getActivityForSpecies(ResourceLocation speciesId) {
        if (speciesId == null) {
            return null;
        }
        AnalyzedBeeTraits traits = getAnalyzedTraitsForSpecies(speciesId);
        if (traits != null) {
            return traits.activityTypeId;
        }
        return resolveSpeciesDefaults(speciesId).activityTypeId;
    }

    public List<String> getAnalyzedSpecimenKeys() {
        return new ArrayList<>(this.analyzedSpecies.keySet());
    }

    public ResourceLocation getSpeciesForSpecimenKey(String specimenKey) {
        if (specimenKey == null) {
            return null;
        }
        AnalyzedBeeTraits traits = this.analyzedSpecies.get(specimenKey);
        return traits == null ? null : traits.speciesId;
    }

    public Map<String, String> getAllelesForSpecimenKey(String specimenKey) {
        if (specimenKey == null) {
            return Map.of();
        }
        AnalyzedBeeTraits traits = this.analyzedSpecies.get(specimenKey);
        return traits == null ? Map.of() : traits.alleles;
    }

    public ItemStack getBeeStackForSpecimenKey(String specimenKey) {
        if (specimenKey == null) {
            return ItemStack.EMPTY;
        }
        AnalyzedBeeTraits traits = this.analyzedSpecies.get(specimenKey);
        if (traits == null) {
            return ItemStack.EMPTY;
        }
        return traits.beeStack.copy();
    }

    public List<VirtualHiveConfig> getVirtualHives() {
        List<VirtualHiveConfig> copy = new ArrayList<>(this.virtualHives.size());
        for (VirtualHiveConfig config : this.virtualHives) {
            copy.add(new VirtualHiveConfig(config.specimenKey, config.speciesId, config.biomeId, config.flowerItemId, config.instances));
        }
        return copy;
    }

    public ContainerData getContainerData() {
        return this.containerData;
    }

    public int getStructureDimX() {
        return this.uiDimX;
    }

    public int getStructureDimY() {
        return this.uiDimY;
    }

    public int getStructureDimZ() {
        return this.uiDimZ;
    }

    public int getStructureControllerCount() {
        return this.uiControllerCount;
    }

    public int getStructureCasingCount() {
        return this.uiCasingCount;
    }

    public int getStructureCpuCount() {
        return this.uiCpuCount;
    }

    public int getStructureRamCount() {
        return this.uiRamCount;
    }

    public int getStructureAnalyzerCount() {
        return this.uiAnalyzerCount;
    }

    public int getStructurePowerSupplyCount() {
        return this.uiPowerSupplyCount;
    }

    public int getStructureBatteryCount() {
        return this.uiBatteryCount;
    }

    public int getStructureExportBusCount() {
        return this.uiExportBusCount;
    }

    public int getStructureInvalidCount() {
        return this.uiInvalidCount;
    }

    public long getPowerStoredForUi() {
        return this.uiPowerStored;
    }

    public long getPowerCapacityForUi() {
        return this.uiPowerCapacity;
    }

    public boolean isPartOfCurrentStructure(BlockPos pos) {
        return this.assembledPositions.contains(pos);
    }

    public void markStructureDirty() {
        this.structureDirty = true;
    }

    public static void requestValidationNear(Level level, BlockPos origin) {
        if (level == null || level.isClientSide) {
            return;
        }

        int radius = MAX_MULTIBLOCK_DIM;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            for (int y = origin.getY() - radius; y <= origin.getY() + radius; y++) {
                for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                    cursor.set(x, y, z);
                    if (level.getBlockEntity(cursor) instanceof BeeSXIControllerBlockEntity controller) {
                        controller.markStructureDirty();
                    }
                }
            }
        }
    }

    public boolean isAnalyzing() {
        return this.analyzing;
    }

    public int getAnalyzeProgressForUi() {
        return getAnalyzeProgressPercent();
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
        tag.putBoolean("Analyzing", this.analyzing);
        tag.putInt("AnalyzeTicksRemaining", this.analyzeTicksRemaining);
        tag.putLong("AnalyzeEnergyRemaining", this.analyzeEnergyRemaining);
        tag.putString("PendingAnalyzeSpecies", this.pendingAnalyzeSpeciesId == null ? "" : this.pendingAnalyzeSpeciesId.toString());
        tag.putFloat("PendingAnalyzeSpeed", this.pendingAnalyzeSpeed);
        tag.putString("PendingAnalyzeActivity", this.pendingAnalyzeActivityId == null ? "" : this.pendingAnalyzeActivityId.toString());
        tag.putString("PendingAnalyzeBiome", this.pendingAnalyzeBiomeId == null ? "" : this.pendingAnalyzeBiomeId.toString());
        tag.putString("PendingAnalyzeFlower", this.pendingAnalyzeFlowerId == null ? "" : this.pendingAnalyzeFlowerId.toString());
        CompoundTag pendingAlleles = new CompoundTag();
        for (Map.Entry<String, String> entry : this.pendingAnalyzeAlleles.entrySet()) {
            pendingAlleles.putString(entry.getKey(), entry.getValue());
        }
        tag.put("PendingAnalyzeAlleles", pendingAlleles);
        tag.put("PendingAnalyzeStack", this.pendingAnalyzeBeeStack.saveOptional(provider));
        tag.putLong("UiPowerStored", this.uiPowerStored);
        tag.putLong("UiPowerCapacity", this.uiPowerCapacity);
        tag.putInt("UiAnalyzeProgress", this.uiAnalyzeProgress);
        tag.putLong("UiInstanceRfPerTick", this.uiInstanceRfPerTick);
        tag.putLong("UiAnalyzeRfPerTick", this.uiAnalyzeRfPerTick);
        tag.putLong("UiTotalRfPerTick", this.uiTotalRfPerTick);
        tag.putInt("UiDimX", this.uiDimX);
        tag.putInt("UiDimY", this.uiDimY);
        tag.putInt("UiDimZ", this.uiDimZ);
        tag.putInt("UiControllerCount", this.uiControllerCount);
        tag.putInt("UiCasingCount", this.uiCasingCount);
        tag.putInt("UiCpuCount", this.uiCpuCount);
        tag.putInt("UiRamCount", this.uiRamCount);
        tag.putInt("UiAnalyzerCount", this.uiAnalyzerCount);
        tag.putInt("UiPowerSupplyCount", this.uiPowerSupplyCount);
        tag.putInt("UiBatteryCount", this.uiBatteryCount);
        tag.putInt("UiExportBusCount", this.uiExportBusCount);
        tag.putInt("UiInvalidCount", this.uiInvalidCount);

        ListTag analyzed = new ListTag();
        for (Map.Entry<String, AnalyzedBeeTraits> entry : this.analyzedSpecies.entrySet()) {
            CompoundTag speciesTag = new CompoundTag();
            speciesTag.putString("Key", entry.getKey());
            speciesTag.putString("Species", entry.getValue().speciesId == null ? "" : entry.getValue().speciesId.toString());
            speciesTag.putFloat("Speed", entry.getValue().speed);
            speciesTag.putString("Activity", entry.getValue().activityTypeId == null ? "" : entry.getValue().activityTypeId.toString());
            speciesTag.putString("Biome", entry.getValue().requiredBiomeId == null ? "" : entry.getValue().requiredBiomeId.toString());
            speciesTag.putString("Flower", entry.getValue().requiredFlowerId == null ? "" : entry.getValue().requiredFlowerId.toString());

            CompoundTag allelesTag = new CompoundTag();
            for (Map.Entry<String, String> allele : entry.getValue().alleles.entrySet()) {
                allelesTag.putString(allele.getKey(), allele.getValue());
            }
            speciesTag.put(PAPER_ALLELES_KEY, allelesTag);
            speciesTag.put("BeeStack", entry.getValue().beeStack.saveOptional(provider));
            analyzed.add(speciesTag);
        }
        tag.put("AnalyzedSpecies", analyzed);

        ListTag unlockedBiomesTag = new ListTag();
        for (ResourceLocation biomeId : getUnlockedBiomeIds()) {
            unlockedBiomesTag.add(StringTag.valueOf(biomeId.toString()));
        }
        tag.put("UnlockedBiomes", unlockedBiomesTag);

        ListTag unlockedFlowersTag = new ListTag();
        for (ResourceLocation flowerId : getUnlockedFlowerIds()) {
            unlockedFlowersTag.add(StringTag.valueOf(flowerId.toString()));
        }
        tag.put("UnlockedFlowers", unlockedFlowersTag);

        ListTag hives = new ListTag();
        for (VirtualHiveConfig config : this.virtualHives) {
            if (config.speciesId != null) {
                config.specimenKey = config.speciesId.toString();
            }
            CompoundTag hiveTag = new CompoundTag();
            hiveTag.putString("SpecimenKey", config.specimenKey == null ? "" : config.specimenKey);
            hiveTag.putString("Species", config.speciesId == null ? "" : config.speciesId.toString());
            hiveTag.putString("Biome", config.biomeId == null ? "" : config.biomeId.toString());
            hiveTag.putString("Flower", config.flowerItemId == null ? "" : config.flowerItemId.toString());
            hiveTag.putInt("Instances", config.instances);
            hives.add(hiveTag);
        }
        tag.put("VirtualHives", hives);

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

        ListTag exportBuses = new ListTag();
        for (BlockPos exportBusPos : this.exportBusPositions) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", exportBusPos.getX());
            posTag.putInt("Y", exportBusPos.getY());
            posTag.putInt("Z", exportBusPos.getZ());
            exportBuses.add(posTag);
        }
        tag.put("ExportBusPositions", exportBuses);
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
        this.analyzing = tag.getBoolean("Analyzing");
        this.analyzeTicksRemaining = Math.max(0, tag.getInt("AnalyzeTicksRemaining"));
        this.analyzeEnergyRemaining = tag.contains("AnalyzeEnergyRemaining", Tag.TAG_LONG) ? Math.max(0L, tag.getLong("AnalyzeEnergyRemaining")) : 0L;
        this.pendingAnalyzeSpeciesId = safeParseSpeciesId(tag.getString("PendingAnalyzeSpecies"));
        this.pendingAnalyzeSpeed = tag.contains("PendingAnalyzeSpeed", Tag.TAG_FLOAT) ? tag.getFloat("PendingAnalyzeSpeed") : 0.0F;
        this.pendingAnalyzeActivityId = ResourceLocation.tryParse(tag.getString("PendingAnalyzeActivity"));
        this.pendingAnalyzeBiomeId = ResourceLocation.tryParse(tag.getString("PendingAnalyzeBiome"));
        this.pendingAnalyzeFlowerId = ResourceLocation.tryParse(tag.getString("PendingAnalyzeFlower"));
        this.pendingAnalyzeAlleles = new HashMap<>();
        if (tag.contains("PendingAnalyzeAlleles", Tag.TAG_COMPOUND)) {
            CompoundTag pendingAlleles = tag.getCompound("PendingAnalyzeAlleles");
            for (String key : pendingAlleles.getAllKeys()) {
                this.pendingAnalyzeAlleles.put(key, pendingAlleles.getString(key));
            }
        }
        this.pendingAnalyzeBeeStack = tag.contains("PendingAnalyzeStack", Tag.TAG_COMPOUND)
            ? ItemStack.parseOptional(provider, tag.getCompound("PendingAnalyzeStack"))
            : ItemStack.EMPTY;
        this.uiPowerStored = tag.contains("UiPowerStored", Tag.TAG_LONG) ? tag.getLong("UiPowerStored") : 0L;
        this.uiPowerCapacity = tag.contains("UiPowerCapacity", Tag.TAG_LONG) ? tag.getLong("UiPowerCapacity") : 0L;
        this.uiAnalyzeProgress = tag.contains("UiAnalyzeProgress", Tag.TAG_INT) ? tag.getInt("UiAnalyzeProgress") : (this.analyzing ? 0 : 100);
        this.uiInstanceRfPerTick = tag.contains("UiInstanceRfPerTick", Tag.TAG_LONG) ? Math.max(0L, tag.getLong("UiInstanceRfPerTick")) : 0L;
        this.uiAnalyzeRfPerTick = tag.contains("UiAnalyzeRfPerTick", Tag.TAG_LONG) ? Math.max(0L, tag.getLong("UiAnalyzeRfPerTick")) : 0L;
        this.uiTotalRfPerTick = tag.contains("UiTotalRfPerTick", Tag.TAG_LONG) ? Math.max(0L, tag.getLong("UiTotalRfPerTick")) : (this.uiInstanceRfPerTick + this.uiAnalyzeRfPerTick);
        this.uiDimX = Math.max(0, tag.getInt("UiDimX"));
        this.uiDimY = Math.max(0, tag.getInt("UiDimY"));
        this.uiDimZ = Math.max(0, tag.getInt("UiDimZ"));
        this.uiControllerCount = Math.max(0, tag.getInt("UiControllerCount"));
        this.uiCasingCount = Math.max(0, tag.getInt("UiCasingCount"));
        this.uiCpuCount = Math.max(0, tag.getInt("UiCpuCount"));
        this.uiRamCount = Math.max(0, tag.getInt("UiRamCount"));
        this.uiAnalyzerCount = Math.max(0, tag.getInt("UiAnalyzerCount"));
        this.uiPowerSupplyCount = Math.max(0, tag.getInt("UiPowerSupplyCount"));
        this.uiBatteryCount = Math.max(0, tag.getInt("UiBatteryCount"));
        this.uiExportBusCount = Math.max(0, tag.getInt("UiExportBusCount"));
        this.uiInvalidCount = Math.max(0, tag.getInt("UiInvalidCount"));

        this.analyzedSpecies.clear();
        ListTag analyzed = tag.getList("AnalyzedSpecies", Tag.TAG_COMPOUND);
        for (int i = 0; i < analyzed.size(); i++) {
            CompoundTag speciesTag = analyzed.getCompound(i);
            ResourceLocation speciesId = safeParseSpeciesId(speciesTag.getString("Species"));
            String key = speciesTag.contains("Key", Tag.TAG_STRING) ? speciesTag.getString("Key") : (speciesId == null ? Integer.toHexString(i) : speciesId.toString());
            if (speciesId == null && key.isEmpty()) {
                continue;
            }

            String normalizedKey = normalizeSpecimenKey(key, speciesId);
            if (normalizedKey == null || normalizedKey.isBlank()) {
                continue;
            }
            key = normalizedKey;

            float speed = speciesTag.contains("Speed", Tag.TAG_FLOAT) ? speciesTag.getFloat("Speed") : 1.0F;
            ResourceLocation activityId = ResourceLocation.tryParse(speciesTag.getString("Activity"));
            ResourceLocation biomeId = ResourceLocation.tryParse(speciesTag.getString("Biome"));
            ResourceLocation flowerId = ResourceLocation.tryParse(speciesTag.getString("Flower"));
            Map<String, String> alleles = new HashMap<>();
            if (speciesTag.contains(PAPER_ALLELES_KEY, Tag.TAG_COMPOUND)) {
                CompoundTag allelesTag = speciesTag.getCompound(PAPER_ALLELES_KEY);
                for (String alleleKey : allelesTag.getAllKeys()) {
                    alleles.put(alleleKey, allelesTag.getString(alleleKey));
                }
            }
            ItemStack beeStack = speciesTag.contains("BeeStack", Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(provider, speciesTag.getCompound("BeeStack"))
                : ItemStack.EMPTY;
            this.analyzedSpecies.put(key, new AnalyzedBeeTraits(speciesId, speed, activityId, biomeId, flowerId, alleles, null, beeStack));
        }

        if (this.analyzedSpecies.isEmpty()) {
            ListTag legacyAnalyzed = tag.getList("AnalyzedSpecies", Tag.TAG_STRING);
            for (int i = 0; i < legacyAnalyzed.size(); i++) {
                ResourceLocation id = safeParseSpeciesId(legacyAnalyzed.getString(i));
                if (id != null) {
                    this.analyzedSpecies.put(id.toString(), new AnalyzedBeeTraits(id, resolveSpeciesDefaults(id).speed, resolveSpeciesDefaults(id).activityTypeId, resolveSpeciesDefaults(id).requiredBiomeId, resolveSpeciesDefaults(id).requiredFlowerId, resolveSpeciesDefaults(id).alleles, null, ItemStack.EMPTY));
                }
            }
        }

        this.unlockedBiomes.clear();
        ListTag unlockedBiomesTag = tag.getList("UnlockedBiomes", Tag.TAG_STRING);
        for (int i = 0; i < unlockedBiomesTag.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(unlockedBiomesTag.getString(i));
            if (id != null) {
                this.unlockedBiomes.add(id);
            }
        }

        this.unlockedFlowers.clear();
        ListTag unlockedFlowersTag = tag.getList("UnlockedFlowers", Tag.TAG_STRING);
        for (int i = 0; i < unlockedFlowersTag.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(unlockedFlowersTag.getString(i));
            if (id != null) {
                this.unlockedFlowers.add(id);
            }
        }

        this.virtualHives.clear();
        ListTag hives = tag.getList("VirtualHives", Tag.TAG_COMPOUND);
        for (int i = 0; i < hives.size(); i++) {
            CompoundTag hiveTag = hives.getCompound(i);
            String specimenKey = hiveTag.contains("SpecimenKey", Tag.TAG_STRING) ? hiveTag.getString("SpecimenKey") : null;
            ResourceLocation speciesId = ResourceLocation.tryParse(hiveTag.getString("Species"));
            ResourceLocation biomeId = ResourceLocation.tryParse(hiveTag.getString("Biome"));
            ResourceLocation flowerId = ResourceLocation.tryParse(hiveTag.getString("Flower"));
            int instances = Math.max(0, hiveTag.getInt("Instances"));
            if (specimenKey != null && specimenKey.isEmpty()) {
                specimenKey = null;
            }
            specimenKey = normalizeSpecimenKey(specimenKey, speciesId);
            this.virtualHives.add(new VirtualHiveConfig(specimenKey, speciesId, biomeId, flowerId, instances));
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

        this.exportBusPositions.clear();
        ListTag exportBuses = tag.getList("ExportBusPositions", Tag.TAG_COMPOUND);
        for (int i = 0; i < exportBuses.size(); i++) {
            CompoundTag posTag = exportBuses.getCompound(i);
            this.exportBusPositions.add(new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z")));
        }

        this.assembledPositions.clear();
        resizeVirtualHives();
        this.setChanged();
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

    public ItemStack extractStoredItem(ResourceLocation itemId, int amount) {
        if (amount <= 0 || itemId == null) {
            return ItemStack.EMPTY;
        }

        ItemStack extracted = ItemStack.EMPTY;
        int remaining = amount;

        for (int slot = 1; slot < this.items.size() && remaining > 0; slot++) {
            ItemStack stack = this.items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }

            ResourceLocation stackId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (stackId == null || !stackId.equals(itemId)) {
                continue;
            }

            int take = Math.min(remaining, stack.getCount());
            ItemStack taken = stack.copyWithCount(take);
            if (extracted.isEmpty()) {
                extracted = taken;
            } else {
                extracted.grow(take);
            }

            stack.shrink(take);
            if (stack.isEmpty()) {
                this.items.set(slot, ItemStack.EMPTY);
            }
            remaining -= take;
        }

        if (!extracted.isEmpty()) {
            setChanged();
            sync();
        }
        return extracted;
    }

    public static final class VirtualHiveConfig {
        public String specimenKey;
        public ResourceLocation speciesId;
        public ResourceLocation biomeId;
        public ResourceLocation flowerItemId;
        public int instances;

        public VirtualHiveConfig(ResourceLocation speciesId, ResourceLocation biomeId, ResourceLocation flowerItemId, int instances) {
            this(null, speciesId, biomeId, flowerItemId, instances);
        }

        public VirtualHiveConfig(String specimenKey, ResourceLocation speciesId, ResourceLocation biomeId, ResourceLocation flowerItemId, int instances) {
            this.specimenKey = specimenKey;
            this.speciesId = speciesId;
            this.biomeId = biomeId;
            this.flowerItemId = flowerItemId;
            this.instances = instances;
        }
    }

    private static final class StructureValidationResult {
        final boolean valid;
        final int cpus;
        final int rams;
        final boolean hasAnalyzer;
        final List<BlockPos> powerSupplyPositions;
        final List<BlockPos> batteryPositions;
        final List<BlockPos> exportBusPositions;
        final List<BlockPos> structurePositions;

        private StructureValidationResult(boolean valid, int cpus, int rams, boolean hasAnalyzer, List<BlockPos> powerSupplyPositions, List<BlockPos> batteryPositions, List<BlockPos> exportBusPositions, List<BlockPos> structurePositions) {
            this.valid = valid;
            this.cpus = cpus;
            this.rams = rams;
            this.hasAnalyzer = hasAnalyzer;
            this.powerSupplyPositions = powerSupplyPositions;
            this.batteryPositions = batteryPositions;
            this.exportBusPositions = exportBusPositions;
            this.structurePositions = structurePositions;
        }

        static StructureValidationResult invalid() {
            return new StructureValidationResult(false, 0, 0, false, List.of(), List.of(), List.of(), List.of());
        }
    }

    private static final class StructureDiagnostics {
        final int dimX;
        final int dimY;
        final int dimZ;
        final int controllerCount;
        final int casingCount;
        final int cpuCount;
        final int ramCount;
        final int analyzerCount;
        final int powerSupplyCount;
        final int batteryCount;
        final int exportBusCount;
        final int invalidCount;
        final StructureValidationResult result;
        final List<String> issues;

        private StructureDiagnostics(int dimX, int dimY, int dimZ, int controllerCount, int casingCount, int cpuCount, int ramCount, int analyzerCount, int powerSupplyCount, int batteryCount, int exportBusCount, int invalidCount, StructureValidationResult result, List<String> issues) {
            this.dimX = dimX;
            this.dimY = dimY;
            this.dimZ = dimZ;
            this.controllerCount = controllerCount;
            this.casingCount = casingCount;
            this.cpuCount = cpuCount;
            this.ramCount = ramCount;
            this.analyzerCount = analyzerCount;
            this.powerSupplyCount = powerSupplyCount;
            this.batteryCount = batteryCount;
            this.exportBusCount = exportBusCount;
            this.invalidCount = invalidCount;
            this.result = result;
            this.issues = issues;
        }
    }

    private static final class AnalyzedBeeTraits {
        final ResourceLocation speciesId;
        final float speed;
        final ResourceLocation activityTypeId;
        final ResourceLocation requiredBiomeId;
        final ResourceLocation requiredFlowerId;
        final Map<String, String> alleles;
        final IGenome genome;
        final ItemStack beeStack;

        private AnalyzedBeeTraits(ResourceLocation speciesId, float speed, ResourceLocation activityTypeId, ResourceLocation requiredBiomeId, ResourceLocation requiredFlowerId, Map<String, String> alleles) {
            this(speciesId, speed, activityTypeId, requiredBiomeId, requiredFlowerId, alleles, null, ItemStack.EMPTY);
        }

        private AnalyzedBeeTraits(ResourceLocation speciesId, float speed, ResourceLocation activityTypeId, ResourceLocation requiredBiomeId, ResourceLocation requiredFlowerId, Map<String, String> alleles, IGenome genome) {
            this(speciesId, speed, activityTypeId, requiredBiomeId, requiredFlowerId, alleles, genome, ItemStack.EMPTY);
        }

        private AnalyzedBeeTraits(ResourceLocation speciesId, float speed, ResourceLocation activityTypeId, ResourceLocation requiredBiomeId, ResourceLocation requiredFlowerId, Map<String, String> alleles, IGenome genome, ItemStack beeStack) {
            this.speciesId = speciesId;
            this.speed = speed;
            this.activityTypeId = activityTypeId;
            this.requiredBiomeId = requiredBiomeId;
            this.requiredFlowerId = requiredFlowerId;
            this.alleles = alleles == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(alleles));
            this.genome = genome;
            this.beeStack = beeStack == null ? ItemStack.EMPTY : beeStack.copy();
        }
    }
}
