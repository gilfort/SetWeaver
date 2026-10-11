package com.gilfort.setweaver.seteffects;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;

/**
 * Version-specific helpers for armor pieces. A piece is anything worn in a humanoid armor slot, so armor of other
 * mods counts even if it does not extend a vanilla armor class.
 */
public final class ArmorPieces {

    private ArmorPieces() {
    }

    /**
     * @param item the item to check
     * @return whether the item can be worn in a helmet, chestplate, leggings or boots slot
     */
    public static boolean isArmor(Item item) {
        Equipable equipable = Equipable.get(new ItemStack(item));
        return equipable != null && equipable.getEquipmentSlot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
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
        for (ItemStack stack : player.getArmorSlots()) {
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
        for (ItemStack stack : player.getArmorSlots()) {
            if (!stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
