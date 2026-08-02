package com.jaysin.beesxi.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.jaysin.beesxi.blockentity.BeeSXIControllerBlockEntity;
import com.jaysin.beesxi.server.BeeSXIServerMenu;

import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.core.IProduct;
import forestry.core.utils.SpeciesUtil;

public class BeeSXIServerScreen extends AbstractContainerScreen<BeeSXIServerMenu> {
    private static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath("beesxi", "textures/gui/beesxi_controller_menu_new.png");
    private static final Map<ResourceLocation, ItemStack> FLOWER_ICON_OVERRIDES = createFlowerIconOverrides();

    private static final int VISIBLE_LINES = 7;
    private static final int LINE_HEIGHT = 18;
    private static final int ROW_BASE_Y = 47;

    private final List<Button> lineButtons = new ArrayList<>();
    private final List<Button> tabButtons = new ArrayList<>();
    private Button analysisTabButton;
    private Button virtualTabButton;
    private Button inventoryTabButton;
    private Button infoTabButton;
    private Button beeSpeciesTabButton;
    private Button flowersTabButton;
    private Button biomesTabButton;
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
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();

        int x = this.leftPos;
        int y = this.topPos;

        this.analysisTabButton = this.addRenderableWidget(Button.builder(Component.literal("Analysis"), b -> pressMenuButton(BeeSXIControllerBlockEntity.TAB_ANALYSIS))
            .bounds(x + 3, y + 42, 68, 20)
            .build());
        this.tabButtons.add(this.analysisTabButton);

        this.virtualTabButton = this.addRenderableWidget(Button.builder(Component.literal("Virtual Hives"), b -> pressMenuButton(BeeSXIControllerBlockEntity.TAB_VIRTUAL_HIVES))
            .bounds(x + 3, y + 66, 68, 20)
            .build());
        this.tabButtons.add(this.virtualTabButton);

        this.inventoryTabButton = this.addRenderableWidget(Button.builder(Component.literal("Inventory"), b -> pressMenuButton(BeeSXIControllerBlockEntity.TAB_INVENTORY))
            .bounds(x + 3, y + 90, 68, 20)
            .build());
        this.tabButtons.add(this.inventoryTabButton);

        this.infoTabButton = this.addRenderableWidget(Button.builder(Component.literal("Info"), b -> pressMenuButton(BeeSXIControllerBlockEntity.TAB_INFO))
            .bounds(x + 3, y + 114, 68, 20)
            .build());
        this.tabButtons.add(this.infoTabButton);

        this.beeSpeciesTabButton = this.addRenderableWidget(Button.builder(Component.literal("Bee Species"), b -> pressMenuButton(BeeSXIControllerBlockEntity.TAB_BEE_SPECIES))
            .bounds(x + 3, y + 138, 68, 20)
            .build());
        this.tabButtons.add(this.beeSpeciesTabButton);

        this.flowersTabButton = this.addRenderableWidget(Button.builder(Component.literal("Flowers"), b -> pressMenuButton(BeeSXIControllerBlockEntity.TAB_FLOWERS))
            .bounds(x + 3, y + 162, 68, 20)
            .build());
        this.tabButtons.add(this.flowersTabButton);

        this.biomesTabButton = this.addRenderableWidget(Button.builder(Component.literal("Biomes"), b -> pressMenuButton(BeeSXIControllerBlockEntity.TAB_BIOMES))
            .bounds(x + 3, y + 186, 68, 20)
            .build());
        this.tabButtons.add(this.biomesTabButton);

        this.prevPageButton = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (this.menu.getActiveTab() == BeeSXIControllerBlockEntity.TAB_VIRTUAL_HIVES) {
                linePage = Math.max(0, linePage - 1);
            } else if (this.menu.getActiveTab() == BeeSXIControllerBlockEntity.TAB_INVENTORY) {
                this.menu.beginInventoryPageChange(this.menu.getInventoryPage() - 1);
                pressMenuButton(9100);
            }
        })
            .bounds(x + 332, y + 20, 14, 20)
            .build());
        this.nextPageButton = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            if (this.menu.getActiveTab() == BeeSXIControllerBlockEntity.TAB_VIRTUAL_HIVES) {
                linePage++;
            } else if (this.menu.getActiveTab() == BeeSXIControllerBlockEntity.TAB_INVENTORY) {
                this.menu.beginInventoryPageChange(this.menu.getInventoryPage() + 1);
                pressMenuButton(9101);
            }
        })
            .bounds(x + 346, y + 20, 14, 20)
            .build());

        this.analyzeSlotButton = this.addRenderableWidget(Button.builder(Component.literal("Analyze"), b -> pressMenuButton(9000))
            .bounds(x + 128, y + 60, 76, 20)
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
            this.lineButtons.add(this.addRenderableWidget(Button.builder(Component.literal("<"), b -> pressLineButton(line, 0)).bounds(x + 76, rowY, 12, 16).build()));
            this.lineButtons.add(this.addRenderableWidget(Button.builder(Component.literal(">"), b -> pressLineButton(line, 1)).bounds(x + 90, rowY, 12, 16).build()));
            this.lineButtons.add(this.addRenderableWidget(Button.builder(Component.literal("-"), b -> pressLineButton(line, 2)).bounds(x + 332, rowY, 12, 16).build()));
            this.lineButtons.add(this.addRenderableWidget(Button.builder(Component.literal("+"), b -> pressLineButton(line, 3)).bounds(x + 346, rowY, 12, 16).build()));
            this.lineButtons.add(this.addRenderableWidget(Button.builder(Component.literal("<"), b -> pressLineButton(line, 4)).bounds(x + 176, rowY, 12, 16).build()));
            this.lineButtons.add(this.addRenderableWidget(Button.builder(Component.literal(">"), b -> pressLineButton(line, 5)).bounds(x + 190, rowY, 12, 16).build()));
            this.lineButtons.add(this.addRenderableWidget(Button.builder(Component.literal("<"), b -> pressLineButton(line, 6)).bounds(x + 276, rowY, 12, 16).build()));
            this.lineButtons.add(this.addRenderableWidget(Button.builder(Component.literal(">"), b -> pressLineButton(line, 7)).bounds(x + 290, rowY, 12, 16).build()));
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
        guiGraphics.drawString(this.font, stateText, stateX, this.topPos + 2, stateColor, false);

        long powerStored = this.menu.getPowerStoredForUi();
        long powerCap = this.menu.getPowerCapacityForUi();
        long totalRfPerTick = this.menu.getTotalRfPerTickForUi();
        long instanceRfPerTick = this.menu.getInstanceRfPerTickForUi();
        long analyzeRfPerTick = this.menu.getAnalyzeRfPerTickForUi();
        String powerText = "Power: " + powerStored + " / " + powerCap + " RF";
        String usageText = "Usage: " + totalRfPerTick + " RF/t (Instances " + instanceRfPerTick + ", Analysis " + analyzeRfPerTick + ")";
        guiGraphics.drawString(this.font, powerText, this.leftPos + 118, this.topPos + 2, 0x000000, false);
        guiGraphics.drawString(this.font, usageText, this.leftPos + 118, this.topPos + 11, 0x000000, false);
        if (this.menu.isMachineStoppedForInventoryFull()) {
            guiGraphics.drawString(this.font, "Machine Stopped: Inventory Full", this.leftPos + 118, this.topPos + 20, 0xC03030, false);
        }

        if (tab == BeeSXIControllerBlockEntity.TAB_ANALYSIS) {
            renderAnalysisTab(guiGraphics, mouseX, mouseY);
        } else if (tab == BeeSXIControllerBlockEntity.TAB_VIRTUAL_HIVES) {
            renderVirtualTab(guiGraphics, mouseX, mouseY);
        } else if (tab == BeeSXIControllerBlockEntity.TAB_INVENTORY) {
            renderInventoryTab(guiGraphics);
        } else if (tab == BeeSXIControllerBlockEntity.TAB_BEE_SPECIES) {
            renderBeeSpeciesTab(guiGraphics, mouseX, mouseY);
        } else if (tab == BeeSXIControllerBlockEntity.TAB_FLOWERS) {
            renderSimpleUnlockList(guiGraphics, "Unlocked Flowers", this.menu.getUnlockedFlowerIds());
        } else if (tab == BeeSXIControllerBlockEntity.TAB_BIOMES) {
            renderSimpleUnlockList(guiGraphics, "Unlocked Biomes", this.menu.getUnlockedBiomeIds());
        } else {
            renderInfoTab(guiGraphics);
        }
    }

    private void renderInfoTab(GuiGraphics guiGraphics) {
        //guiGraphics.drawString(this.font, "Multiblock Info", this.leftPos + 108, this.topPos + 32, 0x000000, false);
        guiGraphics.drawString(this.font,
            "Dimensions: " + this.menu.getStructureDimX() + "x" + this.menu.getStructureDimY() + "x" + this.menu.getStructureDimZ(),
            this.leftPos + 108, this.topPos + 40, 0x000000, false);

        int y = this.topPos + 54;
        guiGraphics.drawString(this.font, "Controller: " + this.menu.getStructureControllerCount(), this.leftPos + 108, y, 0x000000, false);
        guiGraphics.drawString(this.font, "Casing: " + this.menu.getStructureCasingCount(), this.leftPos + 230, y, 0x000000, false);
        y += 12;
        guiGraphics.drawString(this.font, "CPU: " + this.menu.getStructureCpuCount(), this.leftPos + 108, y, 0x000000, false);
        guiGraphics.drawString(this.font, "RAM: " + this.menu.getStructureRamCount(), this.leftPos + 230, y, 0x000000, false);
        y += 12;
        guiGraphics.drawString(this.font, "HDD: " + this.menu.getStructureHddCount(), this.leftPos + 108, y, 0x000000, false);
        guiGraphics.drawString(this.font, "Analyzer: " + this.menu.getStructureAnalyzerCount(), this.leftPos + 230, y, 0x000000, false);
        y += 12;
        guiGraphics.drawString(this.font, "Power Supply: " + this.menu.getStructurePowerSupplyCount(), this.leftPos + 108, y, 0x000000, false);
        guiGraphics.drawString(this.font, "Battery: " + this.menu.getStructureBatteryCount(), this.leftPos + 230, y, 0x000000, false);
        y += 12;
        guiGraphics.drawString(this.font, "Export Bus: " + this.menu.getStructureExportBusCount(), this.leftPos + 108, y, 0x000000, false);
        y += 12;
        guiGraphics.drawString(this.font, "Invalid/Missing: " + this.menu.getStructureInvalidCount(), this.leftPos + 108, y, 0xB03030, false);
    }

    private void renderAnalysisTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!this.menu.hasAnalyzerTab()) {
            guiGraphics.drawString(this.font, "No Molecular Analyzer in multiblock", this.leftPos + 10, this.topPos + 114, 0xFF6666, false);
            return;
        }

        guiGraphics.drawString(this.font, "Insert bee, flower, or weather report in analyze slot", this.leftPos + 108, this.topPos + 84, 0x000000, false);
        guiGraphics.drawString(this.font, "Species analyzed: " + this.menu.getAnalyzedSpeciesIds().size(), this.leftPos + 108, this.topPos + 114, 0x000000, false);
        guiGraphics.drawString(this.font, "Flowers unlocked: " + this.menu.getUnlockedFlowerIds().size(), this.leftPos + 108, this.topPos + 124, 0x000000, false);
        guiGraphics.drawString(this.font, "Biomes unlocked: " + this.menu.getUnlockedBiomeIds().size(), this.leftPos + 108, this.topPos + 134, 0x000000, false);

        String analyzeProgressText = this.menu.isAnalyzing() ? "Analysis Progress: " + this.menu.getAnalyzeProgressPercent() + "%" : "Analysis Progress: Ready";
        guiGraphics.drawString(this.font, analyzeProgressText, this.leftPos + 108, this.topPos + 44, 0x000000, false);
        int slotX = this.leftPos + 108;
        int slotY = this.topPos + 62;
        guiGraphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF8A96A8);
        guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF202A38);
        ItemStack input = this.menu.slots.get(0).getItem();
        if (!input.isEmpty()) {
            //guiGraphics.renderItem(input, this.leftPos + 108, this.topPos + 62);
            captureHoveredStack(input, this.leftPos + 108, this.topPos + 62, mouseX, mouseY);
        }
    }

    private void renderBeeSpeciesTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        List<ResourceLocation> analyzed = this.menu.getAnalyzedSpeciesIds();
        guiGraphics.drawString(this.font, "Unlocked Bee Species", this.leftPos + 108, this.topPos + 30, 0xA0E0A0, false);
        guiGraphics.drawString(this.font, "Count: " + analyzed.size(), this.leftPos + 108, this.topPos + 40, 0x000000, false);

        int maxLines = 6;
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
            int rowY = this.topPos + 52 + i * 18;

            ItemStack beeIcon = ItemStack.EMPTY;
            IBeeSpecies beeSpecies = SpeciesUtil.getBeeSpecies(speciesId);
            if (beeSpecies != null) {
                beeIcon = beeSpecies.createStack(BeeLifeStage.DRONE);
            }
            if (!beeIcon.isEmpty()) {
                guiGraphics.renderItem(beeIcon, this.leftPos + 108, rowY);
                captureHoveredStack(beeIcon, this.leftPos + 108, rowY, mouseX, mouseY);
            }

            String traitLine = trim(speciesId.toString(), 22) + " spd:" + String.format(java.util.Locale.ROOT, "%.2f", speed) + " act:" + trim(activity, 12);
            guiGraphics.drawString(this.font, traitLine, this.leftPos + 128, rowY + 4, 0x000000, false);
        }
    }

    private void renderSimpleUnlockList(GuiGraphics guiGraphics, String title, List<ResourceLocation> ids) {
        guiGraphics.drawString(this.font, title, this.leftPos + 108, this.topPos + 30, 0xA0E0A0, false);
        guiGraphics.drawString(this.font, "Count: " + ids.size(), this.leftPos + 108, this.topPos + 40, 0x000000, false);

        int maxLines = 12;
        int maxPage = Math.max(0, (ids.size() - 1) / maxLines);
        if (this.analyzedPage > maxPage) {
            this.analyzedPage = maxPage;
        }

        int start = this.analyzedPage * maxLines;
        for (int i = 0; i < maxLines; i++) {
            int index = start + i;
            if (index >= ids.size()) {
                break;
            }
            guiGraphics.drawString(this.font, trim(ids.get(index).toString(), 38), this.leftPos + 108, this.topPos + 52 + i * 10, 0x000000, false);
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

        guiGraphics.drawString(this.font, "CPU Cores: " + cpuCount + "  RAM " + ramCount + "Tb ", this.leftPos + 118, this.topPos + 28, 0x111111, false);

        int rowBackground = 0x55262f3b;

        for (int row = 0; row < VISIBLE_LINES; row++) {
            int absoluteLine = start + row;
            if (absoluteLine >= cpuCount) {
                continue;
            }

            ResourceLocation species = this.menu.getSpeciesForLine(absoluteLine);
            ResourceLocation biome = this.menu.getBiomeForLine(absoluteLine);
            ResourceLocation flower = this.menu.getFlowerForLine(absoluteLine);
            int instances = this.menu.getInstancesForLine(absoluteLine);
            float speed = this.menu.getSpeedForLine(absoluteLine);
            int y = this.topPos + ROW_BASE_Y + row * LINE_HEIGHT;

            guiGraphics.fill(this.leftPos + 76, y, this.leftPos + 398, y + 16, rowBackground);
            guiGraphics.drawString(this.font, biome == null ? "none" : trim(biome.getPath(), 14), this.leftPos + 204, y + 4, 0x6EA8FF, false);
            renderFlowerIcon(guiGraphics, flower, y, mouseX, mouseY);
            //guiGraphics.drawString(this.font, flower == null ? "f:*" : "f:" + trim(flower.getPath(), 8), this.leftPos + 292, y + 4, 0xB7A46B, false);
            //guiGraphics.drawString(this.font, "S" + String.format(java.util.Locale.ROOT, "%.2f", speed), this.leftPos + 340, y + 4, 0xA7E8B6, false);
            guiGraphics.drawString(this.font, "x" + instances, this.leftPos + 366, y + 4, 0xFFFF99, false);

            renderSpeciesRowIcons(guiGraphics, species, y, mouseX, mouseY);
        }
    }
    private static Map<ResourceLocation, ItemStack> createFlowerIconOverrides() {
        Map<ResourceLocation, ItemStack> overrides = new LinkedHashMap<>();
        overrides.put(ResourceLocation.fromNamespaceAndPath("minecraft", "pumpkin_stem"), new ItemStack(Items.PUMPKIN));
        overrides.put(ResourceLocation.fromNamespaceAndPath("minecraft", "melon_stem"), new ItemStack(Items.MELON));
        return overrides;
    }

    private void renderFlowerIcon(GuiGraphics guiGraphics, ResourceLocation flowerId, int rowY, int mouseX, int mouseY) {
        ItemStack flowerStack = ItemStack.EMPTY;

        if (flowerId != null) {
            ItemStack overrideStack = FLOWER_ICON_OVERRIDES.get(flowerId);
            if (overrideStack != null) {
                flowerStack = overrideStack.copy();
            } else {
                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(flowerId);
                if (item != Items.AIR) {
                    flowerStack = new ItemStack(item);
                }
            }
        }

        if (flowerStack.isEmpty()) {
            flowerStack = new ItemStack(Items.BARRIER);
        }

        int x = this.leftPos + 304;
        guiGraphics.renderItem(flowerStack, x, rowY);
        captureHoveredStack(flowerStack, x, rowY, mouseX, mouseY);
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

        int beeX = this.leftPos + 104;
        guiGraphics.renderItem(beeIcon, beeX, rowY);
        captureHoveredStack(beeIcon, beeX, rowY, mouseX, mouseY);

        for (int i = 0; i < Math.min(3, productIcons.size()); i++) {
            ItemStack icon = productIcons.get(i);
            int x = this.leftPos + 122 + i * 18;
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
        if (this.menu.isInventoryPageChangePending() && !this.menu.isInventoryPageReady()) {
            guiGraphics.drawString(this.font, "Loading...", this.leftPos + 108, this.topPos + 98, 0x000000, false);
            return;
        }

        //guiGraphics.drawString(this.font, "HDD Network Inventory", this.leftPos + 108, this.topPos + 24, 0xA0E0A0, false);
        guiGraphics.drawString(this.font, "Each page shows one HDD", this.leftPos + 108, this.topPos + 98, 0x000000, false);
        int page = this.menu.getInventoryPage() + 1;
        int maxPage = this.menu.getInventoryMaxPage() + 1;
        guiGraphics.drawString(this.font, "Page " + page + " / " + maxPage, this.leftPos + 250, this.topPos + 25, 0x000000, false);

        BlockPos hddPos = this.menu.getInventoryPageHddPos();
        String hddPosText = hddPos == null
            ? "HDD Position: (none)"
            : "HDD Position: (" + hddPos.getX() + "," + hddPos.getY() + "," + hddPos.getZ() + ")";
        guiGraphics.drawString(this.font, hddPosText, this.leftPos + 108, this.topPos + 108, 0x000000, false);
    }

    private void renderInventoryCountOverlays(GuiGraphics guiGraphics) {
        if (this.menu.isInventoryPageChangePending() && !this.menu.isInventoryPageReady()) {
            return;
        }

        BeeSXIServerMenu serverMenu = (BeeSXIServerMenu) this.menu;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = row * 9 + col;
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
        if (this.infoTabButton != null) {
            this.infoTabButton.active = tab != BeeSXIControllerBlockEntity.TAB_INFO;
        }
        if (this.beeSpeciesTabButton != null) {
            this.beeSpeciesTabButton.active = tab != BeeSXIControllerBlockEntity.TAB_BEE_SPECIES;
        }
        if (this.flowersTabButton != null) {
            this.flowersTabButton.active = tab != BeeSXIControllerBlockEntity.TAB_FLOWERS;
        }
        if (this.biomesTabButton != null) {
            this.biomesTabButton.active = tab != BeeSXIControllerBlockEntity.TAB_BIOMES;
        }

        boolean analysisActive = tab == BeeSXIControllerBlockEntity.TAB_ANALYSIS;
        boolean virtualActive = tab == BeeSXIControllerBlockEntity.TAB_VIRTUAL_HIVES;
        boolean listTabActive = tab == BeeSXIControllerBlockEntity.TAB_BEE_SPECIES
            || tab == BeeSXIControllerBlockEntity.TAB_FLOWERS
            || tab == BeeSXIControllerBlockEntity.TAB_BIOMES;

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
            int listSize = switch (tab) {
                case BeeSXIControllerBlockEntity.TAB_BEE_SPECIES -> this.menu.getAnalyzedSpeciesIds().size();
                case BeeSXIControllerBlockEntity.TAB_FLOWERS -> this.menu.getUnlockedFlowerIds().size();
                case BeeSXIControllerBlockEntity.TAB_BIOMES -> this.menu.getUnlockedBiomeIds().size();
                default -> this.menu.getAnalyzedSpeciesIds().size();
            };
            int pageSize = tab == BeeSXIControllerBlockEntity.TAB_BEE_SPECIES ? 6 : 12;
            int maxAnalyzedPage = Math.max(0, (listSize - 1) / pageSize);
            this.analyzedPrevButton.visible = listTabActive;
            this.analyzedPrevButton.active = listTabActive && this.analyzedPage > 0;
            this.analyzedNextButton.visible = listTabActive;
            this.analyzedNextButton.active = listTabActive && this.analyzedPage < maxAnalyzedPage;
        }

        for (int idx = 0; idx < this.lineButtons.size(); idx++) {
            Button button = this.lineButtons.get(idx);
            int visibleLine = idx / 8;
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
    if (value == null || value.isBlank()) {
        return "";
    }

    String normalized = value.replace('_', ' ').replace('-', ' ');
    StringBuilder formatted = new StringBuilder();
    boolean capitalizeNext = true;

    for (int i = 0; i < normalized.length(); i++) {
        char c = normalized.charAt(i);
        if (Character.isWhitespace(c)) {
            if (formatted.length() > 0 && formatted.charAt(formatted.length() - 1) != ' ') {
                formatted.append(' ');
            }
            capitalizeNext = true;
        } else if (Character.isLetter(c)) {
            if (capitalizeNext) {
                formatted.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                formatted.append(Character.toLowerCase(c));
            }
        } else {
            formatted.append(c);
            capitalizeNext = false;
        }
    }

    String result = formatted.toString().trim();
    if (result.length() <= maxChars) {
        return result;
    }
    return result.substring(0, maxChars - 3) + "...";
}

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        
        
        // Draw the container name (e.g., bright yellow)
        // 0xFFFFFF is white, 0xFFD700 is gold/yellow
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFFFFF, false);
        
        // Draw the player inventory text (e.g., light gray)
        //guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xAAAAAA, false);
    }

}
