package com.gilfort.setweaver.util;

import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import com.gilfort.setweaver.util.SetWeaverPlayerData.PlayerData;
import net.minecraft.server.level.ServerPlayer;

/**
 * Reads and writes a player's role and level, stored in the {@link SetWeaverPlayerData#PLAYER_DATA} attachment.
 */
public final class PlayerDataHelper {

    private PlayerDataHelper() {
    }

    private static PlayerData get(ServerPlayer player) {
        return player.getData(SetWeaverPlayerData.PLAYER_DATA.get());
    }

    /**
     * Sets the role (stored in lower case).
     *
     * @param player the player
     * @param role   the role name
     */
    public static void setRole(ServerPlayer player, String role) {
        player.setData(SetWeaverPlayerData.PLAYER_DATA.get(), new PlayerData(ArmorSetDataRegistry.normalizeRole(role), getLevel(player)));
    }

    /**
     * @param player the player
     * @return the role, empty if none is set
     */
    public static String getRole(ServerPlayer player) {
        return get(player).role();
    }

    /**
     * Sets the level.
     *
     * @param player the player
     * @param level  the level (1 or higher)
     */
    public static void setLevel(ServerPlayer player, int level) {
        player.setData(SetWeaverPlayerData.PLAYER_DATA.get(), new PlayerData(getRole(player), level));
    }

    /**
     * @param player the player
     * @return the level, 0 if none is set
     */
    public static int getLevel(ServerPlayer player) {
        return get(player).level();
    }
}
