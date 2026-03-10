package com.gilfort.zauberei.datagen;

import com.gilfort.zauberei.Zauberei;
import com.gilfort.zauberei.item.ZaubereiItems;
import com.gilfort.zauberei.util.ZaubereiTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class ZaubereiItemTagProvider extends ItemTagsProvider {
    public ZaubereiItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                              CompletableFuture<TagLookup<Block>> blockTags, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, blockTags, Zauberei.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(ItemTags.create(ResourceLocation.fromNamespaceAndPath("minecraft", "enchantable/armor")))
                .add(
                        ZaubereiItems.MAGICCLOTH_HELMET.get(),
                        ZaubereiItems.MAGICCLOTH_HELMET_ALT.get(),
                        ZaubereiItems.MAGICCLOTH_CHESTPLATE.get(),
                        ZaubereiItems.MAGICCLOTH_CHESTPLATE_ALT.get(),
                        ZaubereiItems.MAGICCLOTH_LEGGINGS.get(),
                        ZaubereiItems.MAGICCLOTH_LEGGINGS_ALT.get(),
                        ZaubereiItems.MAGICCLOTH_BOOTS.get(),
                        ZaubereiItems.MAGICCLOTH_BOOTS_ALT.get()

                );

        tag(ItemTags.create(ResourceLocation.fromNamespaceAndPath("minecraft", "enchantable/helmet")))
                .add(
                        ZaubereiItems.MAGICCLOTH_HELMET.get(),
                        ZaubereiItems.MAGICCLOTH_HELMET_ALT.get()

                );

        tag(ItemTags.create(ResourceLocation.fromNamespaceAndPath("minecraft", "enchantable/chestplate")))
                .add(
                        ZaubereiItems.MAGICCLOTH_CHESTPLATE.get(),
                        ZaubereiItems.MAGICCLOTH_CHESTPLATE_ALT.get()

                );

        tag(ItemTags.create(ResourceLocation.fromNamespaceAndPath("minecraft", "enchantable/leggings")))
                .add(
                        ZaubereiItems.MAGICCLOTH_LEGGINGS.get(),
                        ZaubereiItems.MAGICCLOTH_LEGGINGS_ALT.get()

                );

        tag(ItemTags.create(ResourceLocation.fromNamespaceAndPath("minecraft", "enchantable/boots")))
                .add(
                        ZaubereiItems.MAGICCLOTH_BOOTS.get(),
                        ZaubereiItems.MAGICCLOTH_BOOTS_ALT.get()

                );

        tag(ZaubereiTags.Items.TRANSFORMABLE_ITEMS);

    }
}