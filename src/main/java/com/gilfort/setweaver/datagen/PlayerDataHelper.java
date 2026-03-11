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

    // Speichert den role-Tag
    public static void setRole(ServerPlayer player, String role) {
        CompoundTag persistentData = getTag(player);
        persistentData.putString(ROLE_TAG, role);
        player.setData(SetWeaverPlayerData.PLAYER_DATA.get(), persistentData);
        SetWeaver.LOGGER.info("Role set to {} for {}", role, player.getName().getString());
    }

    // Liest den Role-Tag
    public static String getRole(ServerPlayer player) {
        return getTag(player).getString(ROLE_TAG);
    }

    // Speichert den Level-Tag
    public static void setLevel(ServerPlayer player, int Level) {
        CompoundTag tag = getTag(player);
        tag.putInt(LEVEL_TAG, Level);
        player.setData(SetWeaverPlayerData.PLAYER_DATA.get(), tag);
        SetWeaver.LOGGER.info("LevelTag set to {} for {}", Level, player.getName().getString());
    }

    // Liest den Level-Tag
    public static int getLevel(ServerPlayer player) {
        return getTag(player).getInt(LEVEL_TAG);
    }
}