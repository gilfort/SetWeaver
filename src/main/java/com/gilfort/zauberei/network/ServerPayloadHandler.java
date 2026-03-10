package com.gilfort.zauberei.network;

import com.gilfort.zauberei.Config;
import com.gilfort.zauberei.item.ZaubereiItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

import static com.gilfort.zauberei.util.ZaubereiUtils.giveorDrop;

public class ServerPayloadHandler {

    public static void handleIntroductionLetter(LetterButtonPayload data, IPayloadContext context) {

        ServerPlayer player = (ServerPlayer) context.player();

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        if (mainHand.getItem() == ZaubereiItems.INTRODUCTIONLETTER.get()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        } else {
            if (offHand.getItem() == ZaubereiItems.INTRODUCTIONLETTER.get()) {
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            } else {
                System.out.println("ERROR - ServerPayloadHandler: handleIntroductionLetter: Player is not holding the Introduction Letter in either hand");
                return;
            }
        }

        List<ItemStack> startingItems = Config.getLetterItems();

        for (ItemStack startingItem : startingItems){
            giveorDrop(player, startingItem);
        }

        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);

    }
}
