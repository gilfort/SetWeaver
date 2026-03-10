package com.gilfort.zauberei.datagen;

import com.gilfort.zauberei.block.ZaubereiBlocks;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.util.Set;

public class ZaubereiBlockLootTableProvider extends BlockLootSubProvider
{
    public ZaubereiBlockLootTableProvider(HolderLookup.Provider registries)
    {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        // dropSelf(ZaubereiBlocks.EXAMPLE_BLOCK.get());

        // add(ZaubereiBlocks.EXAMPLE_BLOCK.get(),
        //      block -> createOreDrop(block, ZaubereiItems.EXAMPLE_ITEM.get()));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ZaubereiBlocks.BLOCKS.getEntries().stream().map(Holder::value)::iterator;
    }


    //Custom Function to create multiple drops from a block
    protected LootTable.Builder createMultipleDrops(Block pBlock, Item item, float minDrops, float maxDrops){
        HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return this.createSilkTouchDispatchTable(pBlock,
                this.applyExplosionDecay(pBlock, LootItem.lootTableItem(item)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(minDrops, maxDrops)))
                        .apply(ApplyBonusCount.addOreBonusCount(registryLookup.getOrThrow(Enchantments.FORTUNE)))));

    }

}
