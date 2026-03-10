package com.gilfort.setweaver.datagen;

import com.gilfort.setweaver.SetWeaver;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class SetWeaverBlockTagProvider extends BlockTagsProvider {
    public SetWeaverBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, SetWeaver.MODID, existingFileHelper);
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
