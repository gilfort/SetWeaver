package com.gilfort.setweaver.datagen;

import com.gilfort.setweaver.SetWeaver;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;

public class SetWeaverBlockStateProvider extends BlockStateProvider {
    public SetWeaverBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, SetWeaver.MODID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
//            blockWithItem(com.gilfort.zauberei.block.ZaubereiBlocks.EXAMPLE_BLOCK);

    }

    private void blockWithItem(DeferredBlock<?> deferredBlock) {
        simpleBlockWithItem(deferredBlock.get(), cubeAll(deferredBlock.get()));
    }
}
