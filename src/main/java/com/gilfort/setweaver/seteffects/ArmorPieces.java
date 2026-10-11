package com.gilfort.setweaver.seteffects;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.Arrays;
import java.util.List;

/**
 * Version-specific helpers for armor pieces. A piece is anything worn in a humanoid armor slot, so armor of other
 * mods counts even if it does not extend a vanilla armor class.
 */
public final class ArmorPieces {

    /** Helmet, chestplate, leggings and boots slots. */
    private static final List<EquipmentSlot> ARMOR_SLOTS = Arrays.stream(EquipmentSlot.values())
            .filter(slot -> slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR)
            .toList();

    private ArmorPieces() {
    }

    /**
     * @param item the item to check
     * @return whether the item can be worn in a helmet, chestplate, leggings or boots slot
     */
    public static boolean isArmor(Item item) {
        Equippable equippable = item.components().get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }

    /**
     * Counts the worn armor pieces in a tag.
     *
     * @param player the player
     * @param tag    the set tag
     * @return the number of armor slots holding an item of the tag
     */
    public static int countWorn(Player player, TagKey<Item> tag) {
        int count = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.is(tag)) {
                count++;
            }
        }
        return count;
    }

    /**
     * @param player the player
     * @return whether the player wears anything in an armor slot
     */
    public static boolean wearsAny(Player player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!player.getItemBySlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
