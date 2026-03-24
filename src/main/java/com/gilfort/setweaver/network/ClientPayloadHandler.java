package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import com.gilfort.setweaver.guis.SetsManagerScreen;
import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import com.gilfort.setweaver.seteffects.AttributePackageManager;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Handles payloads received on the CLIENT side.
 * Separated into its own class to avoid loading client classes on the server.
 */
public class ClientPayloadHandler {

    /**
     * Called when the server tells us to open the Sets Manager GUI.
     * Runs on the main client thread via enqueueWork.
     */
    public static void handleOpenSetsGui(final OpenSetsGuiPayload payload,
                                         final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft.getInstance().setScreen(new SetsManagerScreen());
        });
    }

    /**
     * Called when the server syncs the player's role and year.
     * Updates the client-side cache used by tooltip rendering.
     */
    public static void handlePlayerData(final PlayerDataPayload payload,
                                        final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPlayerDataCache.update(payload.role(), payload.level());
        });
    }

    /**
     * Called when the server syncs the entire set definition registry.
     * Replaces the client-side ArmorSetDataRegistry so tooltips and GUIs
     * can display set data on dedicated servers.
     */
    public static void handleRegistrySync(final RegistrySyncPayload payload,
                                          final IPayloadContext context) {
        context.enqueueWork(() -> {
            ArmorSetDataRegistry.deserializeFromJson(payload.registryJson());
            AttributePackageManager.deserializeFromJson(payload.packagesJson());
            SetWeaver.LOGGER.info("[SetWeaver] Received registry sync from server ({} chars, {} chars packages)",
                    payload.registryJson().length(), payload.packagesJson().length());
        });
    }
}
