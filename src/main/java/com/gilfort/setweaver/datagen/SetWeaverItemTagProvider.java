package com.gilfort.setweaver.datagen;

import com.gilfort.setweaver.SetWeaver;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class SetWeaverItemTagProvider extends ItemTagsProvider {
    public SetWeaverItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                                    CompletableFuture<TagLookup<Block>> blockTags, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, blockTags, SetWeaver.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {

    }
}