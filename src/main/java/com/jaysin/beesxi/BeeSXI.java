package com.jaysin.beesxi;

import javax.annotation.Nonnull;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.jaysin.beesxi.apiary.ApiaryMachinePartBlock;
import com.jaysin.beesxi.apiary.ApiaryMachinePartType;
import com.jaysin.beesxi.apiary.MultiblockApiaryBlockEntity;
import com.jaysin.beesxi.apiary.MultiblockApiaryControllerBlock;

@Mod(BeeSXI.MODID)
public class BeeSXI {
    public static final String MODID = "beesxi";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final DeferredBlock<Block> APIARY_CONTROLLER = BLOCKS.register("apiary_controller",
        () -> new MultiblockApiaryControllerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 8.0F)));
    public static final DeferredBlock<Block> APIARY_CASING = BLOCKS.register("apiary_casing",
        () -> new ApiaryMachinePartBlock(ApiaryMachinePartType.CASING, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 8.0F)));
    public static final DeferredBlock<Block> APIARY_ACCELERATOR = BLOCKS.register("apiary_accelerator",
        () -> new ApiaryMachinePartBlock(ApiaryMachinePartType.ACCELERATOR, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 8.0F)));
    public static final DeferredBlock<Block> APIARY_HYPER_ACCELERATOR = BLOCKS.register("apiary_hyper_accelerator",
        () -> new ApiaryMachinePartBlock(ApiaryMachinePartType.HYPER_ACCELERATOR, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 8.0F)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiblockApiaryBlockEntity>> MULTIBLOCK_APIARY_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("apiary_controller",
            () -> BlockEntityType.Builder.of(MultiblockApiaryBlockEntity::new, APIARY_CONTROLLER.get()).build(null));

    public BeeSXI(@Nonnull IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);

        ITEMS.register("apiary_controller", () -> new BlockItem(APIARY_CONTROLLER.get(), new Item.Properties()));
        ITEMS.register("apiary_casing", () -> new BlockItem(APIARY_CASING.get(), new Item.Properties()));
        ITEMS.register("apiary_accelerator", () -> new BlockItem(APIARY_ACCELERATOR.get(), new Item.Properties()));
        ITEMS.register("apiary_hyper_accelerator", () -> new BlockItem(APIARY_HYPER_ACCELERATOR.get(), new Item.Properties()));

        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(@Nonnull FMLCommonSetupEvent event) {
    }
}