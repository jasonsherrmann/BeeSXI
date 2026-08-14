package com.jaysin.beesxi;

import javax.annotation.Nonnull;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.item.Rarity;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.jaysin.beesxi.blockentity.BeeSXIControllerBlockEntity;
import com.jaysin.beesxi.blockentity.BeeSXIExportBusBlockEntity;
import com.jaysin.beesxi.blockentity.BeeSXIHddBlockEntity;
import com.jaysin.beesxi.blockentity.BeeSXIPowerSupplyBlockEntity;
import com.jaysin.beesxi.blockentity.BeeSXIWeatherReporterBlockEntity;
import com.jaysin.beesxi.blocks.BeeSXIControllerBlock;
import com.jaysin.beesxi.blocks.BeeSXIExportBusBlock;
import com.jaysin.beesxi.blocks.BeeSXIHddBlock;
import com.jaysin.beesxi.blocks.BeeSXIPartBlock;
import com.jaysin.beesxi.blocks.BeeSXIPowerBlock;
import com.jaysin.beesxi.blocks.BeeSXIWeatherReporterBlock;
import com.jaysin.beesxi.command.BeeSXICommands;
import com.jaysin.beesxi.server.BeeSXIExportBusMenu;
import com.jaysin.beesxi.server.BeeSXIPartType;
import com.jaysin.beesxi.server.BeeSXIServerMenu;
import com.jaysin.beesxi.server.BeeSXIWeatherReporterMenu;

@Mod(BeeSXI.MODID)
public class BeeSXI {
    public static final String MODID = "beesxi";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);

    
    public static final DeferredBlock<Block> BEESXI_CONTROLLER = BLOCKS.register("beesxi_controller",
        () -> new BeeSXIControllerBlock(BlockBehaviour.Properties.of()
        .mapColor(MapColor.METAL)
        .strength(4.0F, 3600000.0F)
        .lightLevel(state -> state.getValue(BeeSXIControllerBlock.ASSEMBLED) ? 9 : 0)
        .isValidSpawn((state, getter, pos, entityType) -> false)));
    public static final DeferredBlock<Block> BEESXI_CPU = BLOCKS.register("beesxi_cpu",
        () -> new BeeSXIPartBlock(BeeSXIPartType.CPU, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 8.0F)));
    public static final DeferredBlock<Block> BEESXI_RAM = BLOCKS.register("beesxi_ram",
        () -> new BeeSXIPartBlock(BeeSXIPartType.RAM, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 8.0F)));
    public static final DeferredBlock<Block> BEESXI_HDD = BLOCKS.register("beesxi_hdd",
        () -> new BeeSXIHddBlock(BlockBehaviour.Properties.of()
        .mapColor(MapColor.METAL)
        .strength(3.0F, 3600000.0F)
        .lightLevel(state -> state.getValue(BeeSXIHddBlock.ASSEMBLED) ? 9 : 0)
        .isValidSpawn((state, getter, pos, entityType) -> false)));
    public static final DeferredBlock<Block> BEESXI_CASING = BLOCKS.register("beesxi_casing",
        () -> new BeeSXIPartBlock(BeeSXIPartType.CASING, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 8.0F)));
    public static final DeferredBlock<Block> MOLECULAR_ANALYZER = BLOCKS.register("molecular_analyzer",
        () -> new BeeSXIPartBlock(BeeSXIPartType.MOLECULAR_ANALYZER, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 8.0F)));
    public static final DeferredBlock<Block> BEESXI_POWER_SUPPLY = BLOCKS.register("beesxi_power_supply",
        () -> new BeeSXIPowerBlock(BeeSXIPartType.POWER_SUPPLY, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 8.0F)));
    public static final DeferredBlock<Block> BEESXI_BATTERY = BLOCKS.register("beesxi_battery",
        () -> new BeeSXIPowerBlock(BeeSXIPartType.BATTERY, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 8.0F)));
    public static final DeferredBlock<Block> BEESXI_EXPORT_BUS = BLOCKS.register("beesxi_export_bus",
        () -> new BeeSXIExportBusBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 8.0F)));
    public static final DeferredBlock<Block> BEESXI_WEATHER_REPORTER = BLOCKS.register("beesxi_weather_reporter",
        () -> new BeeSXIWeatherReporterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 8.0F)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeeSXIControllerBlockEntity>> BEESXI_CONTROLLER_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("beesxi_controller",
            () -> BlockEntityType.Builder.of(BeeSXIControllerBlockEntity::new, BEESXI_CONTROLLER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeeSXIHddBlockEntity>> BEESXI_HDD_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("beesxi_hdd",
            () -> BlockEntityType.Builder.of(BeeSXIHddBlockEntity::new, BEESXI_HDD.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeeSXIPowerSupplyBlockEntity>> BEESXI_POWER_SUPPLY_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("beesxi_power_supply",
            () -> BlockEntityType.Builder.of(BeeSXIPowerSupplyBlockEntity::new, BEESXI_POWER_SUPPLY.get(), BEESXI_BATTERY.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeeSXIExportBusBlockEntity>> BEESXI_EXPORT_BUS_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("beesxi_export_bus",
            () -> BlockEntityType.Builder.of(BeeSXIExportBusBlockEntity::new, BEESXI_EXPORT_BUS.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeeSXIWeatherReporterBlockEntity>> BEESXI_WEATHER_REPORTER_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("beesxi_weather_reporter",
            () -> BlockEntityType.Builder.of(BeeSXIWeatherReporterBlockEntity::new, BEESXI_WEATHER_REPORTER.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<BeeSXIServerMenu>> BEESXI_SERVER_MENU =
        MENUS.register("beesxi_server", () -> IMenuTypeExtension.create(BeeSXIServerMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<BeeSXIExportBusMenu>> BEESXI_EXPORT_BUS_MENU =
        MENUS.register("beesxi_export_bus", () -> IMenuTypeExtension.create(BeeSXIExportBusMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<BeeSXIWeatherReporterMenu>> BEESXI_WEATHER_REPORTER_MENU =
        MENUS.register("beesxi_weather_reporter", () -> IMenuTypeExtension.create(BeeSXIWeatherReporterMenu::fromNetwork));

    public static final DeferredHolder<Item, Item> BEESXI_CONTROLLER_ITEM = ITEMS.register("beesxi_controller", () -> new BlockItem(BEESXI_CONTROLLER.get(), new Item.Properties().rarity(Rarity.EPIC)));
    public static final DeferredHolder<Item, Item> BEESXI_CPU_ITEM = ITEMS.register("beesxi_cpu", () -> new BlockItem(BEESXI_CPU.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredHolder<Item, Item> BEESXI_RAM_ITEM = ITEMS.register("beesxi_ram", () -> new BlockItem(BEESXI_RAM.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredHolder<Item, Item> BEESXI_HDD_ITEM = ITEMS.register("beesxi_hdd", () -> new BlockItem(BEESXI_HDD.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredHolder<Item, Item> BEESXI_CASING_ITEM = ITEMS.register("beesxi_casing", () -> new BlockItem(BEESXI_CASING.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredHolder<Item, Item> MOLECULAR_ANALYZER_ITEM = ITEMS.register("molecular_analyzer", () -> new BlockItem(MOLECULAR_ANALYZER.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredHolder<Item, Item> BEESXI_POWER_SUPPLY_ITEM = ITEMS.register("beesxi_power_supply", () -> new BlockItem(BEESXI_POWER_SUPPLY.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredHolder<Item, Item> BEESXI_BATTERY_ITEM = ITEMS.register("beesxi_battery", () -> new BlockItem(BEESXI_BATTERY.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> BEESXI_EXPORT_BUS_ITEM = ITEMS.register("beesxi_export_bus", () -> new BlockItem(BEESXI_EXPORT_BUS.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredHolder<Item, Item> BEESXI_WEATHER_REPORTER_ITEM = ITEMS.register("beesxi_weather_reporter", () -> new BlockItem(BEESXI_WEATHER_REPORTER.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredHolder<Item, Item> CAPACITOR_EMPTY = ITEMS.register("capacitor_empty", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> SILICON_WAFER = ITEMS.register("silicon_wafer", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredHolder<Item, Item> CAPACITOR_FILLED = ITEMS.register("capacitor_filled",
        () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)){
            @Override
            public boolean isFoil(ItemStack stack) {
                return true;
            }
        });
    


    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BEESXI_CREATIVE_TAB = CREATIVE_MODE_TABS.register("beesxi",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.beesxi"))
            .icon(() -> new ItemStack(BEESXI_CONTROLLER_ITEM.get()))
            .displayItems((params, output) -> {
                output.accept(BEESXI_CONTROLLER_ITEM.get());
                output.accept(BEESXI_CPU_ITEM.get());
                output.accept(BEESXI_RAM_ITEM.get());
                output.accept(BEESXI_HDD_ITEM.get());
                output.accept(BEESXI_CASING_ITEM.get());
                output.accept(MOLECULAR_ANALYZER_ITEM.get());
                output.accept(BEESXI_POWER_SUPPLY_ITEM.get());
                output.accept(BEESXI_BATTERY_ITEM.get());
                output.accept(BEESXI_EXPORT_BUS_ITEM.get());
                output.accept(BEESXI_WEATHER_REPORTER_ITEM.get());
                output.accept(CAPACITOR_EMPTY.get());
                output.accept(CAPACITOR_FILLED.get());
                output.accept(SILICON_WAFER.get());
                 
            })
            .build());

    public BeeSXI(@Nonnull IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(BeeSXICommands::register);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
    }

    private void commonSetup(@Nonnull FMLCommonSetupEvent event) {  }

    private void registerCapabilities(@Nonnull RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.EnergyStorage.BLOCK,
            BEESXI_POWER_SUPPLY_BLOCK_ENTITY.get(),
            (blockEntity, context) -> blockEntity.getEnergyStorage()
        );
        event.registerBlockEntity(
            Capabilities.EnergyStorage.BLOCK,
            BEESXI_WEATHER_REPORTER_BLOCK_ENTITY.get(),
            (blockEntity, context) -> blockEntity.getEnergyStorage()
        );
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            BEESXI_EXPORT_BUS_BLOCK_ENTITY.get(),
            (blockEntity, context) -> blockEntity.getOutputItemHandler()
        );
    }
}