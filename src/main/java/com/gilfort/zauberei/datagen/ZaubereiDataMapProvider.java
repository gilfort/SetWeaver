package com.gilfort.zauberei.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.registries.datamaps.builtin.FurnaceFuel;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

import java.util.concurrent.CompletableFuture;

public class ZaubereiDataMapProvider extends DataMapProvider {
    protected ZaubereiDataMapProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(packOutput, lookupProvider);
    }

    @Override
    protected void gather() {
        this.builder(NeoForgeDataMaps.FURNACE_FUELS)
//                .add(ModItems.STARLIGHT_ASHES.getId(), new FurnaceFuel(1200), false)
//                .add(ModItems.FROSTFIRE_ICE.getId(), new FurnaceFuel(2400), false);
        ;
    }
}