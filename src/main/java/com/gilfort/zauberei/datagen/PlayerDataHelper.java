package com.gilfort.zauberei.helpers;

import com.gilfort.zauberei.Zauberei;
import com.gilfort.zauberei.util.ZaubereiPlayerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public class PlayerDataHelper {
    
    private static final String MAJOR_TAG = "Major";
    private static final String YEAR_TAG = "Year";

    private static CompoundTag getTag(ServerPlayer player){
        return player.getData(ZaubereiPlayerData.PLAYER_DATA.get());
    }

    // Speichert den Major-Tag
    public static void setMajor(ServerPlayer player, String major) {
        CompoundTag persistentData = getTag(player);
        persistentData.putString(MAJOR_TAG, major);
        player.setData(ZaubereiPlayerData.PLAYER_DATA.get(), persistentData);
        Zauberei.LOGGER.info("MajorTag set to {} for {}", major, player.getName().getString());
    }

    // Liest den Major-Tag
    public static String getMajor(ServerPlayer player) {
        return getTag(player).getString(MAJOR_TAG);
    }

    // Speichert den Year-Tag
    public static void setYear(ServerPlayer player, int year) {
        CompoundTag tag = getTag(player);
        tag.putInt(YEAR_TAG, year);
        player.setData(ZaubereiPlayerData.PLAYER_DATA.get(), tag);
        Zauberei.LOGGER.info("YearTag set to {} for {}", year, player.getName().getString());
    }

    // Liest den Year-Tag
    public static int getYear(ServerPlayer player) {
        return getTag(player).getInt(YEAR_TAG);
    }
}