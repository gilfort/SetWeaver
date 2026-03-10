package com.gilfort.zauberei.util;

import net.minecraft.world.item.Item;

public class ItemPropertiesHelper {
    public static Item.Properties equipment() {
        return new Item.Properties();
    }

    public static Item.Properties equipment(int stacksize) {
        return equipment().stacksTo(stacksize);
    }

    public static Item.Properties material() {
        return new Item.Properties();
    }

    public static Item.Properties material(int stackSize) {
        return material().stacksTo(stackSize);
    }

    public static Item.Properties hidden() {
        return new Item.Properties();
    }

    public static Item.Properties hidden(int stackSize) {
        return hidden().stacksTo(stackSize);
    }

}