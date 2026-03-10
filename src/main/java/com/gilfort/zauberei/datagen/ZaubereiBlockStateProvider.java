package com.gilfort.zauberei.datagen;

import com.gilfort.zauberei.Zauberei;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;

public class ZaubereiBlockStateProvider extends BlockStateProvider {
    public ZaubereiBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, Zauberei.MODID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
//            blockWithItem(com.gilfort.zauberei.block.ZaubereiBlocks.EXAMPLE_BLOCK);

    }

    private void blockWithItem(DeferredBlock<?> deferredBlock) {
        simpleBlockWithItem(deferredBlock.get(), cubeAll(deferredBlock.get()));
    }
}
