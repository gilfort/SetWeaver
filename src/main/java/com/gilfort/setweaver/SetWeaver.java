package com.gilfort.setweaver;

import com.gilfort.setweaver.component.ComponentRegistry;
import com.gilfort.setweaver.network.RegistrySyncPayload;
import com.gilfort.setweaver.network.SetWeaverSync;
import com.gilfort.setweaver.seteffects.ArmorEffects;
import com.gilfort.setweaver.seteffects.SetWeaverReloadListener;
import com.gilfort.setweaver.util.SetWeaverPlayerData;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * Mod entry point. SetWeaver grants effects and attribute modifiers for wearing several pieces of an armor set,
 * defined per item tag, player role and level.
 */
@Mod(SetWeaver.MODID)
public final class SetWeaver {

    /** Mod id. */
    public static final String MODID = "setweaver";
    /** Mod logger. */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Wires registries and event listeners.
     *
     * @param modEventBus the mod event bus
     */
    @SuppressWarnings("deprecation")
    public SetWeaver(IEventBus modEventBus) {
        ComponentRegistry.register(modEventBus);
        SetWeaverPlayerData.ATTACHMENT_TYPES.register(modEventBus);
        ArmorEffects.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(SetWeaver::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(SetWeaver::onDatapackSync);
    }

    /**
     * Creates an id in this mod's namespace.
     *
     * @param path the path
     * @return {@code setweaver:<path>}
     */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /** Loads the set definitions on server start and on {@code /reload}. */
    private static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SetWeaverReloadListener());
    }

    /** Syncs set definitions and player data on login (one player) and after {@code /reload} (everyone). */
    private static void onDatapackSync(OnDatapackSyncEvent event) {
        RegistrySyncPayload payload = SetWeaverSync.registryPayload();
        event.getRelevantPlayers().forEach((ServerPlayer player) -> {
            SetWeaverSync.sendPlayerData(player);
            PacketDistributor.sendToPlayer(player, payload);
        });
    }
}
