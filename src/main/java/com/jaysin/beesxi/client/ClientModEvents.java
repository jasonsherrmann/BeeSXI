package com.jaysin.beesxi.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import com.jaysin.beesxi.BeeSXI;
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
            return 0xFFFFFF;
        }, 
        BeeSXI.ACIDIC_COMB.get(),
        BeeSXI.COOLANT_COMB.get());
    }
    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(BeeSXI.BEESXI_SERVER_MENU.get(), BeeSXIServerScreen::new);
        event.register(BeeSXI.BEESXI_EXPORT_BUS_MENU.get(), BeeSXIExportBusScreen::new);
        event.register(BeeSXI.BEESXI_WEATHER_REPORTER_MENU.get(), BeeSXIWeatherReporterScreen::new);
    }
}
