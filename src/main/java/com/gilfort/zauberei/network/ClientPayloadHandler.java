package com.gilfort.zauberei.network;

import com.gilfort.zauberei.guis.SetsManagerScreen;
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
}
