package com.gilfort.setweaver.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side cache for the local player's role and level.
 * Updated by {@link ClientPayloadHandler} when a {@link PlayerDataPayload} arrives.
 * Read by {@link com.gilfort.setweaver.client.ArmorSetTooltipHandler} for tooltip rendering.
 */
@OnlyIn(Dist.CLIENT)
public class ClientPlayerDataCache {

    private static String role = "";
    private static int level = 0;

    public static void update(String role, int level) {
        ClientPlayerDataCache.role = role;
        ClientPlayerDataCache.level = level;
    }

    public static String getRole() {
        return role;
    }

    public static int getLevel() {
        return level;
    }

    /** @deprecated Use {@link #getLevel()} instead. */
    @Deprecated(forRemoval = true)
    public static int getYear() {
        return level;
    }

    /** Returns true if the server has sent player data at least once. */
    public static boolean hasData() {
        return role != null && !role.isEmpty() && level > 0;
    }
}
