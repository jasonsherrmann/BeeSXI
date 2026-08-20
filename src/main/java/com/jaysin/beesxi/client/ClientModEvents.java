package com.jaysin.beesxi.client;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import com.jaysin.beesxi.BeeSXI;
import com.jaysin.beesxi.blocks.BeeSXIBlockHoneyComb;
import com.jaysin.beesxi.items.ModHoneycombItem;
import com.jaysin.beesxi.screen.BeeSXIExportBusScreen;
import com.jaysin.beesxi.screen.BeeSXIServerScreen;
import com.jaysin.beesxi.screen.BeeSXIWeatherReporterScreen;

@EventBusSubscriber(modid = BeeSXI.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }
     @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> {
            if (stack.getItem() instanceof ModHoneycombItem comb) {
                return comb.getColor(stack, tint);
            }
            if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof BeeSXIBlockHoneyComb comb) {
                return comb.getColor(stack, tint);
            }
            return 0xFFFFFF;
        }, 
        BeeSXI.ACIDIC_COMB.get(),
        BeeSXI.COOLANT_COMB.get(),
        BeeSXI.ACID_COMB_BLOCK_ITEM.get(),
        BeeSXI.COOLANT_COMB_BLOCK_ITEM.get());
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> {
            if (state.getBlock() instanceof BeeSXIBlockHoneyComb comb) {
                return comb.getColor(ItemStack.EMPTY, tint);
            }
            return 0xFFFFFF;
        }, BeeSXI.ACID_COMB_BLOCK.get(), BeeSXI.COOLANT_COMB_BLOCK.get());
    }
    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(BeeSXI.BEESXI_SERVER_MENU.get(), BeeSXIServerScreen::new);
        event.register(BeeSXI.BEESXI_EXPORT_BUS_MENU.get(), BeeSXIExportBusScreen::new);
        event.register(BeeSXI.BEESXI_WEATHER_REPORTER_MENU.get(), BeeSXIWeatherReporterScreen::new);
    }
}
