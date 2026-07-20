package com.jaysin.beesxi.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Inventory;

import com.jaysin.beesxi.server.BeeSXIControllerBlockEntity;
import com.jaysin.beesxi.server.BeeSXIServerMenu;

import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.core.IProduct;
import forestry.core.utils.SpeciesUtil;

public class BeeSXIServerScreen extends AbstractContainerScreen<BeeSXIServerMenu> {
    private static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath("beesxi", "textures/gui/beesxi_server.png");

    private static final int VISIBLE_LINES = 6;
    private static final int LINE_HEIGHT = 18;
    private static final int ROW_BASE_Y = 47;

    private final List<Button> lineButtons = new ArrayList<>();
    private final List<Button> tabButtons = new ArrayList<>();
    private Button analysisTabButton;
    private Button virtualTabButton;
    private Button inventoryTabButton;
    private Button prevPageButton;
    private Button nextPageButton;
    private Button analyzeSlotButton;

    private ItemStack hoveredStack = ItemStack.EMPTY;
    private int linePage;

    public BeeSXIServerScreen(BeeSXIServerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 248;
        this.imageHeight = 230;
        this.inventoryLabelY = 138;
        this.titleLabelX = 10;
        this.titleLabelY = 9;
    }

    @Override
    protected void init() {
        super.init();

        int x = this.leftPos;
        int y = this.topPos;

        this.analysisTabButton = this.addRenderableWidget(Button.builder(Component.literal("Analysis"), b -> pressMenuButton(BeeSXIControllerBlockEntity.TAB_ANALYSIS))
            .bounds(x + 10, y + 20, 74, 20)
            .build());
        this.tabButtons.add(this.analysisTabButton);

        this.virtualTabButton = this.addRenderableWidget(Button.builder(Component.literal("Virtual Hives"), b -> pressMenuButton(BeeSXIControllerBlockEntity.TAB_VIRTUAL_HIVES))
            .bounds(x + 88, y + 20, 74, 20)
            .build());
        this.tabButtons.add(this.virtualTabButton);

        this.inventoryTabButton = this.addRenderableWidget(Button.builder(Component.literal("Inventory"), b -> pressMenuButton(BeeSXIControllerBlockEntity.TAB_INVENTORY))
            .bounds(x + 166, y + 20, 74, 20)
            .build());
        this.tabButtons.add(this.inventoryTabButton);

        this.prevPageButton = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> linePage = Math.max(0, linePage - 1))
            .bounds(x + 214, y + 20, 14, 20)
            .build());
        this.nextPageButton = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> linePage++)
            .bounds(x + 228, y + 20, 14, 20)
            .build());

        this.analyzeSlotButton = this.addRenderableWidget(Button.builder(Component.literal("Analyze"), b -> pressMenuButton(9000))
            .bounds(x + 47, y + 58, 76, 20)
            .build());

        for (int i = 0; i < VISIBLE_LINES; i++) {
            int rowY = y + ROW_BASE_Y + i * LINE_HEIGHT;
            int line = i;
            this.lineButtons.add(this.addRenderableWidget(Button.builder(Component.literal("<"), b -> pressLineButton(line, 0)).bounds(x + 10, rowY, 16, 16).build()));
            this.lineButtons.add(this.addRenderableWidget(Button.builder(Component.literal(">"), b -> pressLineButton(line, 1)).bounds(x + 28, rowY, 16, 16).build()));
            this.lineButtons.add(this.addRenderableWidget(Button.builder(Component.literal("-"), b -> pressLineButton(line, 2)).bounds(x + 212, rowY, 16, 16).build()));
            this.lineButtons.add(this.addRenderableWidget(Button.builder(Component.literal("+"), b -> pressLineButton(line, 3)).bounds(x + 230, rowY, 16, 16).build()));
        }

        updateWidgetVisibility();
    }

    private void pressMenuButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    private void pressLineButton(int visibleLine, int action) {
        int absoluteLine = this.linePage * VISIBLE_LINES + visibleLine;
        int id = 1000 + absoluteLine * 10 + action;
        pressMenuButton(id);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.hoveredStack = ItemStack.EMPTY;
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (!this.hoveredStack.isEmpty()) {
            guiGraphics.renderTooltip(this.font, this.hoveredStack, mouseX, mouseY);
        }
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        updateWidgetVisibility();

        guiGraphics.blit(BG, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);

        int tab = this.menu.getActiveTab();
        int stateColor = this.menu.isFormed() ? 0xA7E8B6 : 0xF0A0A0;
        guiGraphics.drawString(this.font, Component.literal(this.menu.isFormed() ? "Structure: Formed" : "Structure: Incomplete"), this.leftPos + 10, this.topPos + this.imageHeight - 12, stateColor, false);

        if (tab == BeeSXIControllerBlockEntity.TAB_ANALYSIS) {
            renderAnalysisTab(guiGraphics, mouseX, mouseY);
        } else if (tab == BeeSXIControllerBlockEntity.TAB_VIRTUAL_HIVES) {
            renderVirtualTab(guiGraphics, mouseX, mouseY);
        } else {
            renderInventoryTab(guiGraphics);
        }
    }

    private void renderAnalysisTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!this.menu.hasAnalyzerTab()) {
            guiGraphics.drawString(this.font, "No Molecular Analyzer in multiblock", this.leftPos + 10, this.topPos + 114, 0xFF6666, false);
            return;
        }

        guiGraphics.drawString(this.font, "Insert bee in analyzer slot", this.leftPos + 47, this.topPos + 46, 0xD2DBE8, false);
        guiGraphics.drawString(this.font, "Analyzed species: " + this.menu.getAnalyzedSpeciesIds().size(), this.leftPos + 10, this.topPos + 114, 0xA0E0A0, false);
        int maxLines = 6;
        var analyzed = this.menu.getAnalyzedSpeciesIds();
        for (int i = 0; i < Math.min(maxLines, analyzed.size()); i++) {
            guiGraphics.drawString(this.font, trim(analyzed.get(i).toString(), 42), this.leftPos + 10, this.topPos + 126 + i * 10, 0xFFFFFF, false);
        }

        ItemStack input = this.menu.slots.get(0).getItem();
        if (!input.isEmpty()) {
            guiGraphics.renderItem(input, this.leftPos + 22, this.topPos + 58);
            captureHoveredStack(input, this.leftPos + 22, this.topPos + 58, mouseX, mouseY);
        }
    }

    private void renderVirtualTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int start = this.linePage * VISIBLE_LINES;
        int cpuCount = this.menu.getCpuCount();
        int ramCount = this.menu.getRamCount();
        int maxPage = Math.max(0, (cpuCount - 1) / VISIBLE_LINES);
        if (this.linePage > maxPage) {
            this.linePage = maxPage;
            start = this.linePage * VISIBLE_LINES;
        }

        guiGraphics.drawString(this.font, "CPU lines: " + cpuCount + "  RAM cap: " + ramCount, this.leftPos + 56, this.topPos + 49, 0xA0E0A0, false);

        int rowBackground = 0x55262f3b;

        for (int row = 0; row < VISIBLE_LINES; row++) {
            int absoluteLine = start + row;
            if (absoluteLine >= cpuCount) {
                continue;
            }

            ResourceLocation species = this.menu.getSpeciesForLine(absoluteLine);
            int instances = this.menu.getInstancesForLine(absoluteLine);
            int y = this.topPos + ROW_BASE_Y + row * LINE_HEIGHT;

            guiGraphics.fill(this.leftPos + 48, y, this.leftPos + 210, y + 16, rowBackground);
            guiGraphics.drawString(this.font, "CPU " + (absoluteLine + 1), this.leftPos + 50, y + 4, 0xFFFFFF, false);
            guiGraphics.drawString(this.font, "x" + instances, this.leftPos + 188, y + 4, 0xFFFF99, false);

            renderSpeciesRowIcons(guiGraphics, species, y, mouseX, mouseY);
        }
    }

    private void renderSpeciesRowIcons(GuiGraphics guiGraphics, ResourceLocation speciesId, int rowY, int mouseX, int mouseY) {
        ItemStack beeIcon = ItemStack.EMPTY;
        List<ItemStack> productIcons = new ArrayList<>();

        if (speciesId != null) {
            IBeeSpecies beeSpecies = SpeciesUtil.getBeeSpecies(speciesId);
            if (beeSpecies != null) {
                beeIcon = beeSpecies.createStack(BeeLifeStage.DRONE);
                collectProductIcons(productIcons, beeSpecies.getProducts());
                collectProductIcons(productIcons, beeSpecies.getSpecialties());
            }
        }

        if (beeIcon.isEmpty()) {
            beeIcon = new ItemStack(Items.BARRIER);
        }

        int beeX = this.leftPos + 100;
        guiGraphics.renderItem(beeIcon, beeX, rowY);
        captureHoveredStack(beeIcon, beeX, rowY, mouseX, mouseY);

        for (int i = 0; i < Math.min(3, productIcons.size()); i++) {
            ItemStack icon = productIcons.get(i);
            int x = this.leftPos + 120 + i * 18;
            guiGraphics.renderItem(icon, x, rowY);
            captureHoveredStack(icon, x, rowY, mouseX, mouseY);
        }

        if (speciesId != null) {
            guiGraphics.drawString(this.font, trim(speciesId.getPath(), 8), this.leftPos + 50, rowY - 9, 0xB9C8DC, false);
        }
    }

    private static void collectProductIcons(List<ItemStack> output, List<IProduct> products) {
        Set<ResourceLocation> seen = new java.util.HashSet<>();
        for (ItemStack existing : output) {
            ResourceLocation key = keyFor(existing);
            if (key != null) {
                seen.add(key);
            }
        }

        for (IProduct product : products) {
            ItemStack stack = product.createStack();
            if (stack.isEmpty()) {
                continue;
            }

            ResourceLocation key = keyFor(stack);
            if (key != null && !seen.add(key)) {
                continue;
            }

            output.add(stack);
            if (output.size() >= 3) {
                return;
            }
        }
    }

    private static ResourceLocation keyFor(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    private void renderInventoryTab(GuiGraphics guiGraphics) {
        guiGraphics.drawString(this.font, "Storage Inventory", this.leftPos + 10, this.topPos + 49, 0xA0E0A0, false);
        guiGraphics.drawString(this.font, "26 shared slots (output fallback when HDDs are full)", this.leftPos + 10, this.topPos + 62, 0xD2DBE8, false);
    }

    private void updateWidgetVisibility() {
        int tab = this.menu.getActiveTab();
        int start = this.linePage * VISIBLE_LINES;
        int cpuCount = this.menu.getCpuCount();

        if (this.analysisTabButton != null) {
            this.analysisTabButton.visible = true;
            this.analysisTabButton.active = tab != BeeSXIControllerBlockEntity.TAB_ANALYSIS;
        }
        if (this.virtualTabButton != null) {
            this.virtualTabButton.active = tab != BeeSXIControllerBlockEntity.TAB_VIRTUAL_HIVES;
        }
        if (this.inventoryTabButton != null) {
            this.inventoryTabButton.active = tab != BeeSXIControllerBlockEntity.TAB_INVENTORY;
        }

        boolean analysisActive = tab == BeeSXIControllerBlockEntity.TAB_ANALYSIS;
        boolean virtualActive = tab == BeeSXIControllerBlockEntity.TAB_VIRTUAL_HIVES;

        if (this.prevPageButton != null) {
            this.prevPageButton.visible = virtualActive;
            this.prevPageButton.active = this.linePage > 0;
        }
        if (this.nextPageButton != null) {
            this.nextPageButton.visible = virtualActive;
            this.nextPageButton.active = true;
        }
        if (this.analyzeSlotButton != null) {
            this.analyzeSlotButton.visible = analysisActive;
            this.analyzeSlotButton.active = analysisActive && this.menu.hasAnalyzerTab();
        }

        for (int idx = 0; idx < this.lineButtons.size(); idx++) {
            Button button = this.lineButtons.get(idx);
            int visibleLine = idx / 4;
            int absoluteLine = start + visibleLine;
            boolean hasCpuLine = absoluteLine < cpuCount;
            button.visible = virtualActive && hasCpuLine;
            button.active = virtualActive && hasCpuLine;
        }
    }

    private void captureHoveredStack(ItemStack stack, int x, int y, int mouseX, int mouseY) {
        if (!this.hoveredStack.isEmpty()) {
            return;
        }
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            this.hoveredStack = stack;
        }
    }

    private static String trim(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars - 3) + "...";
    }
}
