package com.jaysin.beesxi.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import com.jaysin.beesxi.BeeSXI;

@EventBusSubscriber(modid = BeeSXI.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(BeeSXI.BEESXI_SERVER_MENU.get(), BeeSXIServerScreen::new);
        event.register(BeeSXI.BEESXI_EXPORT_BUS_MENU.get(), BeeSXIExportBusScreen::new);
    }
}
