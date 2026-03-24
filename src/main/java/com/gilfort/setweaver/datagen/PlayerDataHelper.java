package com.gilfort.setweaver.datagen;

import com.gilfort.setweaver.SetWeaver;
import com.gilfort.setweaver.util.SetWeaverPlayerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public class PlayerDataHelper {

    private static final String ROLE_TAG = "Role";
    private static final String LEVEL_TAG = "Level";

    private static CompoundTag getTag(ServerPlayer player){
        return player.getData(SetWeaverPlayerData.PLAYER_DATA.get());
    }

    // Saves the role tag for the given player
    public static void setRole(ServerPlayer player, String role) {
        CompoundTag persistentData = getTag(player);
        persistentData.putString(ROLE_TAG, role);
        player.setData(SetWeaverPlayerData.PLAYER_DATA.get(), persistentData);
        SetWeaver.LOGGER.info("Role set to {} for {}", role, player.getName().getString());
    }

    // Reads the role tag for the given player
    public static String getRole(ServerPlayer player) {
        return getTag(player).getString(ROLE_TAG);
    }

    // Saves the level tag for the given player
    public static void setLevel(ServerPlayer player, int level) {
        CompoundTag tag = getTag(player);
        tag.putInt(LEVEL_TAG, level);
        player.setData(SetWeaverPlayerData.PLAYER_DATA.get(), tag);
        SetWeaver.LOGGER.info("Level set to {} for {}", level, player.getName().getString());
    }

    // Reads the level tag for the given player
    public static int getLevel(ServerPlayer player) {
        return getTag(player).getInt(LEVEL_TAG);
    }
}
