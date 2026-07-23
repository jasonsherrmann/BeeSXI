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
    private static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath("beesxi", "textures/gui/beesxi_controller_menu.png");

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
    private Button analyzedPrevButton;
    private Button analyzedNextButton;

    private ItemStack hoveredStack = ItemStack.EMPTY;
    private int linePage;
    private int analyzedPage;

    public BeeSXIServerScreen(BeeSXIServerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 400;
        this.imageHeight = 267;
        this.inventoryLabelY = 155;
        this.titleLabelX = 10;
        this.titleLabelY = 5;
    }

    @Override
    protected void init() {
        super.init();

        int x = this.leftPos;
        int y = this.topPos;

        this.analysisTabButton = this.addRenderableWidget(Button.builder(Component.literal("Analysis"), b -> pressMenuButton(BeeSXIControllerBlockEntity.TAB_ANALYSIS))
            .bounds(x + 8, y + 42, 68, 20)
            .build());
        this.tabButtons.add(this.analysisTabButton);

        this.virtualTabButton = this.addRenderableWidget(Button.builder(Component.literal("Virtual Hives"), b -> pressMenuButton(BeeSXIControllerBlockEntity.TAB_VIRTUAL_HIVES))
            .bounds(x + 8, y + 66, 68, 20)
            .build());
        this.tabButtons.add(this.virtualTabButton);

        this.inventoryTabButton = this.addRenderableWidget(Button.builder(Component.literal("Inventory"), b -> pressMenuButton(BeeSXIControllerBlockEntity.TAB_INVENTORY))
            .bounds(x + 8, y + 90, 68, 20)
            .build());
        this.tabButtons.add(this.inventoryTabButton);

        this.prevPageButton = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (this.menu.getActiveTab() == BeeSXIControllerBlockEntity.TAB_VIRTUAL_HIVES) {
                linePage = Math.max(0, linePage - 1);
            } else if (this.menu.getActiveTab() == BeeSXIControllerBlockEntity.TAB_INVENTORY) {
                pressMenuButton(9100);
            }
        })
            .bounds(x + 332, y + 20, 14, 20)
            .build());
        this.nextPageButton = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            if (this.menu.getActiveTab() == BeeSXIControllerBlockEntity.TAB_VIRTUAL_HIVES) {
                linePage++;
            } else if (this.menu.getActiveTab() == BeeSXIControllerBlockEntity.TAB_INVENTORY) {
                pressMenuButton(9101);
            }
        })
            .bounds(x + 346, y + 20, 14, 20)
            .build());

        this.analyzeSlotButton = this.addRenderableWidget(Button.builder(Component.literal("Analyze"), b -> pressMenuButton(9000))
            .bounds(x + 108, y + 8, 76, 20)
            .build());

        this.analyzedPrevButton = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> analyzedPage = Math.max(0, analyzedPage - 1))
            .bounds(x + 308, y + 54, 14, 16)
            .build());
        this.analyzedNextButton = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> analyzedPage++)
            .bounds(x + 324, y + 54, 14, 16)
            .build());

        for (int i = 0; i < VISIBLE_LINES; i++) {
            int rowY = y + ROW_BASE_Y + i * LINE_HEIGHT;
            int line = i;
            this.lineButtons.add(this.addRenderableWidget(Button.builder(Component.literal("<"), b -> pressLineButton(line, 0)).bounds(x + 90, rowY, 16, 16).build()));
            this.lineButtons.add(this.addRenderableWidget(Button.builder(Component.literal(">"), b -> pressLineButton(line, 1)).bounds(x + 108, rowY, 16, 16).build()));
            this.lineButtons.add(this.addRenderableWidget(Button.builder(Component.literal("-"), b -> pressLineButton(line, 2)).bounds(x + 342, rowY, 16, 16).build()));
            this.lineButtons.add(this.addRenderableWidget(Button.builder(Component.literal("+"), b -> pressLineButton(line, 3)).bounds(x + 360, rowY, 16, 16).build()));
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
        if (this.menu.getActiveTab() == BeeSXIControllerBlockEntity.TAB_INVENTORY) {
            renderInventoryCountOverlays(guiGraphics);
        }
        if (!this.hoveredStack.isEmpty()) {
            guiGraphics.renderTooltip(this.font, this.hoveredStack, mouseX, mouseY);
        }
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        updateWidgetVisibility();

        guiGraphics.blit(BG, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);

        int tab = this.menu.getActiveTab();
        int stateColor = this.menu.isFormed() ? 0x000000 : 0xF0A0A0;
        String stateText = this.menu.isFormed() ? "Structure: Formed" : "Structure: Incomplete";
        int stateX = this.leftPos + this.imageWidth - this.font.width(stateText) - 8;
        guiGraphics.drawString(this.font, stateText, stateX, this.topPos + 5, stateColor, false);

        long powerStored = this.menu.getPowerStoredForUi();
        long powerCap = this.menu.getPowerCapacityForUi();
        String powerText = "Power: " + powerStored + " / " + powerCap + " RF";
        guiGraphics.drawString(this.font, powerText, this.leftPos + 108, this.topPos + 34, 0x000000, false);

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

        guiGraphics.drawString(this.font, "Insert bee in analyzer slot", this.leftPos + 108, this.topPos + 46, 0xD2DBE8, false);
        guiGraphics.drawString(this.font, "Species analyzed: " + this.menu.getAnalyzedSpeciesIds().size(), this.leftPos + 108, this.topPos + 54, 0x000000, false);
        String analyzeProgressText = this.menu.isAnalyzing() ? "Analysis Progress: " + this.menu.getAnalyzeProgressPercent() + "%" : "Analysis Progress: Ready";
        guiGraphics.drawString(this.font, analyzeProgressText, this.leftPos + 108, this.topPos + 64, 0x000000, false);
        int slotX = this.leftPos + 102;
        int slotY = this.topPos + 8;
        guiGraphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF8A96A8);
        guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF202A38);
        int maxLines = 4;
        var analyzed = this.menu.getAnalyzedSpeciesIds();
        int maxPage = Math.max(0, (analyzed.size() - 1) / maxLines);
        if (this.analyzedPage > maxPage) {
            this.analyzedPage = maxPage;
        }
        int start = this.analyzedPage * maxLines;
        for (int i = 0; i < maxLines; i++) {
            int index = start + i;
            if (index >= analyzed.size()) {
                break;
            }
            ResourceLocation speciesId = analyzed.get(index);
            float speed = this.menu.getSpeedForSpecies(speciesId);
            ResourceLocation activityId = this.menu.getActivityForSpecies(speciesId);
            String activity = activityId == null ? "unknown" : activityId.getPath();
            String traitLine = trim(speciesId.toString(), 24) + " spd:" + String.format(java.util.Locale.ROOT, "%.2f", speed) + " act:" + trim(activity, 10);
            guiGraphics.drawString(this.font, traitLine, this.leftPos + 108, this.topPos + 76 + i * 10, 0x000000, false);
        }

        ItemStack input = this.menu.slots.get(0).getItem();
        if (!input.isEmpty()) {
            guiGraphics.renderItem(input, this.leftPos + 102, this.topPos + 8);
            captureHoveredStack(input, this.leftPos + 102, this.topPos + 8, mouseX, mouseY);
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

        guiGraphics.drawString(this.font, "CPU Cores: " + cpuCount + "  RAM " + ramCount*512 + "Tb ", this.leftPos + 108, this.topPos + 20, 0xA0E0A0, false);

        int rowBackground = 0x55262f3b;

        for (int row = 0; row < VISIBLE_LINES; row++) {
            int absoluteLine = start + row;
            if (absoluteLine >= cpuCount) {
                continue;
            }

            ResourceLocation species = this.menu.getSpeciesForLine(absoluteLine);
            int instances = this.menu.getInstancesForLine(absoluteLine);
            float speed = this.menu.getSpeedForLine(absoluteLine);
            int y = this.topPos + ROW_BASE_Y + row * LINE_HEIGHT;

            guiGraphics.fill(this.leftPos + 130, y, this.leftPos + 398, y + 16, rowBackground);
            guiGraphics.drawString(this.font, "CPU " + (absoluteLine + 1), this.leftPos + 190, y + 4, 0x000000, false);
            if (species != null) {
                guiGraphics.drawString(this.font, trim(species.getPath(), 10), this.leftPos + 228, y + 4, 0x000000, false);
            }
            guiGraphics.drawString(this.font, "SPD " + String.format(java.util.Locale.ROOT, "%.2f", speed), this.leftPos + 270, y + 4, 0xA7E8B6, false);
            guiGraphics.drawString(this.font, "x" + instances, this.leftPos + 387, y + 4, 0xFFFF99, false);

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

        int beeX = this.leftPos + 130;
        guiGraphics.renderItem(beeIcon, beeX, rowY);
        captureHoveredStack(beeIcon, beeX, rowY, mouseX, mouseY);

        for (int i = 0; i < Math.min(3, productIcons.size()); i++) {
            ItemStack icon = productIcons.get(i);
            int x = this.leftPos + 148 + i * 18;
            guiGraphics.renderItem(icon, x, rowY);
            captureHoveredStack(icon, x, rowY, mouseX, mouseY);
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
        guiGraphics.drawString(this.font, "HDD Network Inventory", this.leftPos + 108, this.topPos + 20, 0xA0E0A0, false);
        guiGraphics.drawString(this.font, "Slots are linked to HDD blocks in this multiblock", this.leftPos + 108, this.topPos + 98, 0x000000, false);
        int page = this.menu.getInventoryPage() + 1;
        int maxPage = this.menu.getInventoryMaxPage() + 1;
        guiGraphics.drawString(this.font, "Page " + page + " / " + maxPage, this.leftPos + 250, this.topPos + 25, 0x000000, false);
    }

    private void renderInventoryCountOverlays(GuiGraphics guiGraphics) {
        BeeSXIServerMenu serverMenu = (BeeSXIServerMenu) this.menu;
        int page = this.menu.getInventoryPage();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = page * 27 + row * 9 + col;
                ItemStack stack = serverMenu.getHddNetworkItem(slotIndex);
                if (stack.isEmpty() || stack.getCount() <= 1) {
                    continue;
                }

                int x = this.leftPos + 123 + col * 18;
                int y = this.topPos + 45 + row * 18;
                String countText = Integer.toString(stack.getCount());
                int textX = x + 18 - this.font.width(countText);
                int textY = y + 9;
                guiGraphics.drawString(this.font, countText, textX, textY, 0x000000, false);
            }
        }
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
            boolean inventoryActive = tab == BeeSXIControllerBlockEntity.TAB_INVENTORY;
            this.prevPageButton.visible = virtualActive || inventoryActive;
            this.prevPageButton.active = virtualActive ? this.linePage > 0 : this.menu.getInventoryPage() > 0;
        }
        if (this.nextPageButton != null) {
            boolean inventoryActive = tab == BeeSXIControllerBlockEntity.TAB_INVENTORY;
            this.nextPageButton.visible = virtualActive || inventoryActive;
            this.nextPageButton.active = virtualActive ? true : this.menu.getInventoryPage() < this.menu.getInventoryMaxPage();
        }
        if (this.analyzeSlotButton != null) {
            this.analyzeSlotButton.visible = analysisActive;
            this.analyzeSlotButton.active = analysisActive && this.menu.hasAnalyzerTab();
        }
        if (this.analyzedPrevButton != null && this.analyzedNextButton != null) {
            int analyzedCount = this.menu.getAnalyzedSpeciesIds().size();
            int maxAnalyzedPage = Math.max(0, (analyzedCount - 1) / 4);
            this.analyzedPrevButton.visible = analysisActive;
            this.analyzedPrevButton.active = analysisActive && this.analyzedPage > 0;
            this.analyzedNextButton.visible = analysisActive;
            this.analyzedNextButton.active = analysisActive && this.analyzedPage < maxAnalyzedPage;
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
