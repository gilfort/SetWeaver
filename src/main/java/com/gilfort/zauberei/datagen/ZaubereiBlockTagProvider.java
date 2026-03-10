package com.gilfort.zauberei.datagen;

import com.gilfort.zauberei.Zauberei;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class ZaubereiBlockTagProvider extends BlockTagsProvider {
    public ZaubereiBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, Zauberei.MODID, existingFileHelper);
    }


    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
//                .add(ZaubereiBlocks.EXAMPLE_BLOCK.get())
                ;

        tag(BlockTags.NEEDS_IRON_TOOL)
//                .add(ZaubereiBlocks.EXAMPLE_BLOCK.get());
                ;
    }
}
