package com.jaysin.beesxi.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import com.jaysin.beesxi.server.BeeSXIWeatherReporterMenu;

public class BeeSXIWeatherReporterScreen extends AbstractContainerScreen<BeeSXIWeatherReporterMenu> {
    private static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath("beesxi", "textures/gui/beesxi_weather_reporter_gui.png");

    public BeeSXIWeatherReporterScreen(BeeSXIWeatherReporterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 199;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(BG, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 8, 6, 0x404040, false);
        ResourceLocation currentBiome = this.menu.getCurrentBiome();
        Component biomeLine = Component.literal("Biome: " + (currentBiome == null ? "unknown" : currentBiome.getPath()));
        guiGraphics.drawString(this.font, biomeLine, 8, 16, 0x404040, false);

        Component status = this.menu.isProcessing()
            ? Component.translatable("gui.beesxi.weather_reporter.status_processing", this.menu.getProgressPercent())
            : Component.translatable("gui.beesxi.weather_reporter.status_idle");
        guiGraphics.drawString(this.font, status, 8, 28, 0x404040, false);

        Component power = Component.translatable("gui.beesxi.weather_reporter.power", this.menu.getEnergyStored(), this.menu.getEnergyCapacity());
        guiGraphics.drawString(this.font, power, 8, 40, 0x404040, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, 8, 100, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
