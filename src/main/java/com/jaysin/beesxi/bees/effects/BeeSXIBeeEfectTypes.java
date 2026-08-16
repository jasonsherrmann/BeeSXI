package com.jaysin.beesxi.bees.effects;
import com.mojang.serialization.MapCodec;

import forestry.api.ForestryRegistries;
import forestry.api.apiculture.genetics.IBeeEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.jaysin.beesxi.BeeSXI;

public class BeeSXIBeeEfectTypes {
    public static final DeferredRegister<MapCodec<? extends IBeeEffect>> BEE_EFFECT_TYPES = DeferredRegister.create(ForestryRegistries.Keys.BEE_EFFECT_TYPE, BeeSXI.MODID);

	
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<PlaceBlockBeeEffect>> PLACE_BLOCK = BEE_EFFECT_TYPES.register("place_block", () -> PlaceBlockBeeEffect.MAP_CODEC);


	public static void register(IEventBus modBus) {
		BEE_EFFECT_TYPES.register(modBus);
	}

	private BeeSXIBeeEfectTypes() {
	}
}
