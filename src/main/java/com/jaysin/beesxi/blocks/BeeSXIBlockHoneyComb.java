package com.jaysin.beesxi.blocks;


import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;


public class BeeSXIBlockHoneyComb extends Block{
    private final int primaryColor;
    private final int secondaryColor;

    public BeeSXIBlockHoneyComb(int primaryColor, int secondaryColor) {
        super(Block.Properties.of().sound(SoundType.CORAL_BLOCK).strength(1F));
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        
    }
       
    public int getColor(ItemStack stack, int tintIndex) {
        return tintIndex == 0 ? primaryColor : secondaryColor;
    }

    
}
