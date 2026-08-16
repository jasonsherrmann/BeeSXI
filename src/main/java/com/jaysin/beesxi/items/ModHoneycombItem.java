package com.jaysin.beesxi.items;

import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ModHoneycombItem extends HoneycombItem {
    private final int primaryColor;
    private final int secondaryColor;

    public ModHoneycombItem(int primaryColor, int secondaryColor) {
        super(new Item.Properties());
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
    }

    public int getColor(ItemStack stack, int tintIndex) {
        return tintIndex == 0 ? primaryColor : secondaryColor;
    }

}