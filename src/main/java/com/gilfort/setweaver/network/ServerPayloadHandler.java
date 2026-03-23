package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import com.gilfort.setweaver.seteffects.AttributePackageManager;
import com.gilfort.setweaver.seteffects.SetWeaverReloadListener;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles payloads received on the SERVER side.
 * All operations validate permissions and sanitize paths before executing.
 */
public class ServerPayloadHandler {

    private static final Path SET_ARMOR_DIR = FMLPaths.CONFIGDIR.get()
            .resolve("setweaver")
            .resolve("set_armor");

    /**
     * Handles a save-set request from a client.
     * Validates OP permission, sanitizes the path to prevent directory traversal,
     * writes the JSON to disk, reloads the registry, and broadcasts the update.
     */
    public static void handleSaveSet(final SaveSetPayload payload,
                                     final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
                return;
            }

            // Permission check: require OP level 2
            if (!sender.hasPermissions(2)) {
                sender.sendSystemMessage(Component.literal("[SetWeaver] Permission denied."));
                SetWeaver.LOGGER.warn("Player {} tried to save a set without permission",
                        sender.getName().getString());
                return;
            }

            // Sanitize path: prevent directory traversal attacks
            String relativePath = payload.relativePath();
            if (relativePath.contains("..") || relativePath.startsWith("/") || relativePath.startsWith("\\")) {
                sender.sendSystemMessage(Component.literal("[SetWeaver] Invalid file path."));
                SetWeaver.LOGGER.warn("Player {} sent invalid save path: {}",
                        sender.getName().getString(), relativePath);
                return;
            }

            Path targetFile = SET_ARMOR_DIR.resolve(relativePath).normalize();

            // Double-check the resolved path is still within the set_armor directory
            if (!targetFile.startsWith(SET_ARMOR_DIR)) {
                sender.sendSystemMessage(Component.literal("[SetWeaver] Invalid file path."));
                SetWeaver.LOGGER.warn("Player {} tried path traversal: {}",
                        sender.getName().getString(), targetFile);
                return;
            }

            try {
                // Create parent directories if needed
                Files.createDirectories(targetFile.getParent());

                // Write the JSON file
                Files.writeString(targetFile, payload.jsonContent());

                SetWeaver.LOGGER.info("Player {} saved set definition to {}",
                        sender.getName().getString(), relativePath);

                // Reload all set definitions from disk
                SetWeaverReloadListener.loadAllEffects();

                // Broadcast updated registry to all connected clients
                String registryJson = ArmorSetDataRegistry.serializeToJson();
                String packagesJson = AttributePackageManager.serializeToJson();
                sender.getServer().getPlayerList().getPlayers().forEach(player ->
                        PacketDistributor.sendToPlayer(player, new RegistrySyncPayload(registryJson, packagesJson))
                );

                sender.sendSystemMessage(Component.literal("[SetWeaver] Set saved and synced successfully."));

            } catch (IOException e) {
                sender.sendSystemMessage(Component.literal("[SetWeaver] Save failed: " + e.getMessage()));
                SetWeaver.LOGGER.error("Failed to save set definition for player {}: {}",
                        sender.getName().getString(), e.getMessage());
            }
        });
    }
}
