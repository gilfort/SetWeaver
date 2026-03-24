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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Handles payloads received on the SERVER side.
 * All handlers verify OP level 2 before processing.
 */
public class ServerPayloadHandler {

    public static void handleSaveSet(final SaveSetPayload payload,
                                     final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (!sender.hasPermissions(2)) {
                sender.sendSystemMessage(Component.literal("[SetWeaver] Permission denied."));
                return;
            }

            try {
                File baseDir = new File(FMLPaths.CONFIGDIR.get().toFile(),
                        "setweaver" + File.separator + "set_armor");
                File target = new File(baseDir, payload.relativePath());

                if (!target.getCanonicalPath().startsWith(baseDir.getCanonicalPath())) {
                    sender.sendSystemMessage(Component.literal("[SetWeaver] Invalid file path."));
                    return;
                }

                target.getParentFile().mkdirs();
                try (FileWriter writer = new FileWriter(target)) {
                    writer.write(payload.jsonContent());
                }

                SetWeaverReloadListener.loadAllEffects();
                broadcastRegistrySync(sender);

                sender.sendSystemMessage(Component.literal("[SetWeaver] Set saved and synced."));

            } catch (IOException e) {
                SetWeaver.LOGGER.error("[SetWeaver] Failed to save set: {}", e.getMessage());
                sender.sendSystemMessage(
                        Component.literal("[SetWeaver] Failed to save: " + e.getMessage()));
            }
        });
    }

    public static void handleSaveTag(final SaveTagPayload payload,
                                     final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (!sender.hasPermissions(2)) {
                sender.sendSystemMessage(Component.literal("[SetWeaver] Permission denied."));
                return;
            }

            try {
                File worldDir = sender.getServer().getWorldPath(
                        net.minecraft.world.level.storage.LevelResource.ROOT).toFile();
                File datapackDir = new File(worldDir,
                        "datapacks" + File.separator + "setweaver_custom");

                File packMcmeta = new File(datapackDir, "pack.mcmeta");
                if (!packMcmeta.exists()) {
                    datapackDir.mkdirs();
                    try (FileWriter w = new FileWriter(packMcmeta)) {
                        w.write("{\"pack\":{\"pack_format\":34,\"description\":\"SetWeaver custom tags\"}}");
                    }
                }

                File tagFile = new File(datapackDir,
                        "data" + File.separator + payload.namespace()
                                + File.separator + "tags" + File.separator + "item"
                                + File.separator + payload.tagName() + ".json");
                tagFile.getParentFile().mkdirs();

                String tagJson = "{\"replace\":false,\"values\":" + payload.itemsJson() + "}";
                try (FileWriter writer = new FileWriter(tagFile)) {
                    writer.write(tagJson);
                }

                sender.sendSystemMessage(Component.literal(
                        "[SetWeaver] Tag saved. Run /reload to activate."));

            } catch (IOException e) {
                SetWeaver.LOGGER.error("[SetWeaver] Failed to save tag: {}", e.getMessage());
                sender.sendSystemMessage(
                        Component.literal("[SetWeaver] Failed to save tag: " + e.getMessage()));
            }
        });
    }

    public static void handleReloadRequest(final ReloadRequestPayload payload,
                                           final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (!sender.hasPermissions(2)) {
                sender.sendSystemMessage(Component.literal("[SetWeaver] Permission denied."));
                return;
            }

            SetWeaverReloadListener.loadAllEffects();
            broadcastRegistrySync(sender);

            sender.sendSystemMessage(Component.literal("[SetWeaver] Reloaded and synced to all players."));
        });
    }

    private static void broadcastRegistrySync(ServerPlayer sender) {
        String registryJson = ArmorSetDataRegistry.serializeToJson();
        String packagesJson = AttributePackageManager.serializeToJson();
        sender.getServer().getPlayerList().getPlayers().forEach(player ->
                PacketDistributor.sendToPlayer(player,
                        new RegistrySyncPayload(registryJson, packagesJson))
        );
    }
}
