package com.gilfort.setweaver.client;

/**
 * The local player's role and level as last sent by the server. Client thread only.
 */
public final class ClientPlayerDataCache {

    private static String role = "";
    private static int level = 0;
    private static boolean received = false;

    private ClientPlayerDataCache() {
    }

    /**
     * Stores the values sent by the server.
     *
     * @param newRole  the role, empty if none
     * @param newLevel the level, 0 if none
     */
    public static void update(String newRole, int newLevel) {
        role = newRole == null ? "" : newRole;
        level = newLevel;
        received = true;
    }

    /** Forgets the values, e.g. when leaving a server. */
    public static void reset() {
        role = "";
        level = 0;
        received = false;
    }

    /** @return the role, empty if none */
    public static String getRole() {
        return role;
    }

    /** @return the level, 0 if none */
    public static int getLevel() {
        return level;
    }

    /** @return whether the server has sent the values since joining */
    public static boolean hasData() {
        return received;
    }
}
