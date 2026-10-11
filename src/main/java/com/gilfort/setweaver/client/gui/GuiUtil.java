package com.gilfort.setweaver.client.gui;

import com.gilfort.setweaver.seteffects.ArmorPieces;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.Comparator;
import java.util.List;

/**
 * Text and lookup helpers of the SetWeaver screens.
 */
public final class GuiUtil {

    private GuiUtil() {
    }

    /**
     * Translates a key on the client.
     *
     * @param key  the lang key
     * @param args format arguments
     * @return the translated text
     */
    public static String tr(String key, Object... args) {
        return I18n.get(key, args);
    }

    /** @return all item tags that contain at least one armor piece, sorted by id */
    public static List<TagKey<Item>> armorItemTags() {
        return BuiltInRegistries.ITEM.getTags()
                .filter(named -> named.stream().anyMatch(h -> ArmorPieces.isArmor(h.value())))
                .map(HolderSet.Named::key)
                .sorted(Comparator.comparing(t -> t.location().toString()))
                .toList();
    }

    /** @return all items of a tag */
    public static List<Item> itemsInTag(TagKey<Item> tag) {
        return BuiltInRegistries.ITEM.get(tag).map(set -> set.stream().map(Holder::value).toList()).orElse(List.of());
    }

    /**
     * Formats a number of armor pieces, e.g. "2 Pieces".
     *
     * @param count the number of pieces
     * @return the translated text
     */
    public static String pieces(int count) {
        return tr(count == 1 ? "gui.setweaver.piece" : "gui.setweaver.pieces", count);
    }
}
