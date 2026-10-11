package com.gilfort.setweaver.util;

import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Reads and writes a player's role and level, stored in the {@link SetWeaverPlayerData#PLAYER_DATA} attachment.
 */
public final class PlayerDataHelper {

    private static final String ROLE_TAG = "Role";
    private static final String LEVEL_TAG = "Level";

    private PlayerDataHelper() {
    }

    private static CompoundTag getTag(ServerPlayer player) {
        return player.getData(SetWeaverPlayerData.PLAYER_DATA.get());
    }

    /**
     * Sets the role (stored in lower case).
     *
     * @param player the player
     * @param role   the role name
     */
    public static void setRole(ServerPlayer player, String role) {
        CompoundTag tag = getTag(player).copy();
        tag.putString(ROLE_TAG, ArmorSetDataRegistry.normalizeRole(role));
        player.setData(SetWeaverPlayerData.PLAYER_DATA.get(), tag);
    }

    /**
     * @param player the player
     * @return the role, empty if none is set
     */
    public static String getRole(ServerPlayer player) {
        return getTag(player).getString(ROLE_TAG);
    }

    /**
     * Sets the level.
     *
     * @param player the player
     * @param level  the level (1 or higher)
     */
    public static void setLevel(ServerPlayer player, int level) {
        CompoundTag tag = getTag(player).copy();
        tag.putInt(LEVEL_TAG, level);
        player.setData(SetWeaverPlayerData.PLAYER_DATA.get(), tag);
    }

    /**
     * @param player the player
     * @return the level, 0 if none is set
     */
    public static int getLevel(ServerPlayer player) {
        return getTag(player).getInt(LEVEL_TAG);
    }
}
