package com.gilfort.setweaver.network;

import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import com.gilfort.setweaver.seteffects.AttributePackageManager;
import com.gilfort.setweaver.util.PlayerDataHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends server state to clients.
 */
public final class SetWeaverSync {

    private SetWeaverSync() {
    }

    /** @return a packet with the current set definitions and attribute packages */
    public static RegistrySyncPayload registryPayload() {
        return new RegistrySyncPayload(ArmorSetDataRegistry.server().serializeToJson(),
                AttributePackageManager.server().serializeToJson());
    }

    /**
     * Sends a player's own role and level to them.
     *
     * @param player the player
     */
    public static void sendPlayerData(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new PlayerDataPayload(PlayerDataHelper.getRole(player), PlayerDataHelper.getLevel(player)));
    }

    /**
     * Sends the set definitions and attribute packages to every online player.
     *
     * @param server the server
     */
    public static void broadcastRegistry(MinecraftServer server) {
        RegistrySyncPayload payload = registryPayload();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
