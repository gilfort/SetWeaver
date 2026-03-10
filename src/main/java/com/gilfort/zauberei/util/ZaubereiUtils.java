package com.gilfort.zauberei.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class ZaubereiUtils {

    public static void giveorDrop(ServerPlayer player, ItemStack stack){
        boolean success = player.getInventory().add(stack);
        if(!success){
            player.drop(stack, false);
        }
    }



}
