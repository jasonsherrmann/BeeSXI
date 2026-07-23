package com.jaysin.beesxi;

import javax.annotation.Nonnull;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.jaysin.beesxi.server.BeeSXIControllerBlock;
import com.jaysin.beesxi.server.BeeSXIControllerBlockEntity;
import com.jaysin.beesxi.server.BeeSXIHddBlock;
import com.jaysin.beesxi.server.BeeSXIHddBlockEntity;
import com.jaysin.beesxi.server.BeeSXIPartBlock;
import com.jaysin.beesxi.server.BeeSXIPartType;
import com.jaysin.beesxi.server.BeeSXIPowerBlock;
import com.jaysin.beesxi.server.BeeSXIPowerSupplyBlockEntity;
import com.jaysin.beesxi.server.BeeSXIServerMenu;

@Mod(BeeSXI.MODID)
public class BeeSXI {
    public static final String MODID = "beesxi";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);

    public static final DeferredBlock<Block> BEESXI_CONTROLLER = BLOCKS.register("beesxi_controller",
        () -> new BeeSXIControllerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F)));
    public static final DeferredBlock<Block> BEESXI_CPU = BLOCKS.register("beesxi_cpu",
        () -> new BeeSXIPartBlock(BeeSXIPartType.CPU, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 8.0F)));
    public static final DeferredBlock<Block> BEESXI_RAM = BLOCKS.register("beesxi_ram",
        () -> new BeeSXIPartBlock(BeeSXIPartType.RAM, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 8.0F)));
    public static final DeferredBlock<Block> BEESXI_HDD = BLOCKS.register("beesxi_hdd",
        () -> new BeeSXIHddBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 8.0F)));
    public static final DeferredBlock<Block> BEESXI_CASING = BLOCKS.register("beesxi_casing",
        () -> new BeeSXIPartBlock(BeeSXIPartType.CASING, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 8.0F)));
    public static final DeferredBlock<Block> MOLECULAR_ANALYZER = BLOCKS.register("molecular_analyzer",
        () -> new BeeSXIPartBlock(BeeSXIPartType.MOLECULAR_ANALYZER, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 8.0F)));
    public static final DeferredBlock<Block> BEESXI_POWER_SUPPLY = BLOCKS.register("beesxi_power_supply",
        () -> new BeeSXIPowerBlock(BeeSXIPartType.POWER_SUPPLY, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 8.0F)));
    public static final DeferredBlock<Block> BEESXI_BATTERY = BLOCKS.register("beesxi_battery",
        () -> new BeeSXIPowerBlock(BeeSXIPartType.BATTERY, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 8.0F)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeeSXIControllerBlockEntity>> BEESXI_CONTROLLER_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("beesxi_controller",
            () -> BlockEntityType.Builder.of(BeeSXIControllerBlockEntity::new, BEESXI_CONTROLLER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeeSXIHddBlockEntity>> BEESXI_HDD_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("beesxi_hdd",
            () -> BlockEntityType.Builder.of(BeeSXIHddBlockEntity::new, BEESXI_HDD.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeeSXIPowerSupplyBlockEntity>> BEESXI_POWER_SUPPLY_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("beesxi_power_supply",
            () -> BlockEntityType.Builder.of(BeeSXIPowerSupplyBlockEntity::new, BEESXI_POWER_SUPPLY.get(), BEESXI_BATTERY.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<BeeSXIServerMenu>> BEESXI_SERVER_MENU =
        MENUS.register("beesxi_server", () -> IMenuTypeExtension.create(BeeSXIServerMenu::fromNetwork));

    public BeeSXI(@Nonnull IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);

        ITEMS.register("beesxi_controller", () -> new BlockItem(BEESXI_CONTROLLER.get(), new Item.Properties()));
        ITEMS.register("beesxi_cpu", () -> new BlockItem(BEESXI_CPU.get(), new Item.Properties()));
        ITEMS.register("beesxi_ram", () -> new BlockItem(BEESXI_RAM.get(), new Item.Properties()));
        ITEMS.register("beesxi_hdd", () -> new BlockItem(BEESXI_HDD.get(), new Item.Properties()));
        ITEMS.register("beesxi_casing", () -> new BlockItem(BEESXI_CASING.get(), new Item.Properties()));
        ITEMS.register("molecular_analyzer", () -> new BlockItem(MOLECULAR_ANALYZER.get(), new Item.Properties()));
        ITEMS.register("beesxi_power_supply", () -> new BlockItem(BEESXI_POWER_SUPPLY.get(), new Item.Properties()));
        ITEMS.register("beesxi_battery", () -> new BlockItem(BEESXI_BATTERY.get(), new Item.Properties()));

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
    }

    private void commonSetup(@Nonnull FMLCommonSetupEvent event) {
    }

    private void registerCapabilities(@Nonnull RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.EnergyStorage.BLOCK,
            BEESXI_POWER_SUPPLY_BLOCK_ENTITY.get(),
            (blockEntity, context) -> blockEntity.getEnergyStorage()
        );
    }
}