package com.gilfort.zauberei.datagen;

import com.gilfort.zauberei.Zauberei;
import com.gilfort.zauberei.item.ZaubereiItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ZaubereiItemModelProvider extends ItemModelProvider {
    public ZaubereiItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Zauberei.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {

        //loop through all Items registered in ZaubereiItems and register them
        ZaubereiItems.ITEMS.getEntries().forEach(item -> basicItem(item.get()));

        basicItem(ZaubereiItems.MAGICCLOTH_HELMET.get());
        basicItem(ZaubereiItems.MAGICCLOTH_CHESTPLATE.get());
        basicItem(ZaubereiItems.MAGICCLOTH_LEGGINGS.get());
        basicItem(ZaubereiItems.MAGICCLOTH_BOOTS.get());

        basicItem(ZaubereiItems.MAGICCLOTH_HELMET_ALT.get());
        basicItem(ZaubereiItems.MAGICCLOTH_CHESTPLATE_ALT.get());
        basicItem(ZaubereiItems.MAGICCLOTH_LEGGINGS_ALT.get());
        basicItem(ZaubereiItems.MAGICCLOTH_BOOTS_ALT.get());



    }
}