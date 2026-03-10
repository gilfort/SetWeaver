package com.gilfort.setweaver.datagen;

import com.gilfort.setweaver.SetWeaver;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class SetWeaverItemModelProvider extends ItemModelProvider {
    public SetWeaverItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, SetWeaver.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {

    }
}