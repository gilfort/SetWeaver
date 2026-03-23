package com.gilfort.setweaver;

import com.gilfort.setweaver.client.ArmorSetTooltipHandler;
import com.gilfort.setweaver.component.ComponentRegistry;
import com.gilfort.setweaver.datagen.PlayerDataHelper;
import com.gilfort.setweaver.network.PlayerDataPayload;
import com.gilfort.setweaver.network.RegistrySyncPayload;
import com.gilfort.setweaver.seteffects.ArmorEffects;
import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import com.gilfort.setweaver.seteffects.AttributePackageManager;
import com.gilfort.setweaver.seteffects.SetWeaverReloadListener;
import com.gilfort.setweaver.util.SetWeaverPlayerData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(SetWeaver.MODID)
public class SetWeaver
{
    public static final String MODID = "setweaver";
    public static final Logger LOGGER = LogUtils.getLogger();



    public SetWeaver(IEventBus modEventBus, ModContainer modContainer)
    {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        ComponentRegistry.register(modEventBus);
        SetWeaverPlayerData.ATTACHMENT_TYPES.register(modEventBus);
        ArmorEffects.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {

        LOGGER.info("HELLO FROM COMMON SETUP");

    }


    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        LOGGER.info("[SetWeaver] Server is starting!");

        //Loads all set_effect files from config/setweaver/set_effects and applies effects
        SetWeaverReloadListener.loadAllEffects();

    }
    public static ResourceLocation id(@NotNull String path) {
        return ResourceLocation.fromNamespaceAndPath(SetWeaver.MODID, path);
    }



    /**
     * Registers a reload listener so that {@code /reload} re-reads all
     * set definitions from the config directory.
     */
    @SubscribeEvent
    public void onAddReloadListenerEvent(AddReloadListenerEvent event) {
        event.addListener(new SetWeaverReloadListener());
    }

    /**
     * Fires after {@code /reload} completes AND when a player logs in.
     * Syncs the full set definition registry to the affected client(s).
     * <ul>
     *   <li>If {@code event.getPlayer()} is non-null → single player login</li>
     *   <li>If {@code event.getPlayer()} is null → server-wide reload, broadcast to all</li>
     * </ul>
     */
    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        String registryJson = ArmorSetDataRegistry.serializeToJson();
        String packagesJson = AttributePackageManager.serializeToJson();
        RegistrySyncPayload registryPayload = new RegistrySyncPayload(registryJson, packagesJson);

        ServerPlayer target = event.getPlayer();
        if (target != null) {
            // Single player login — sync to that player only
            String role = PlayerDataHelper.getRole(target);
            int year = PlayerDataHelper.getLevel(target);
            PacketDistributor.sendToPlayer(target, new PlayerDataPayload(role, year));
            PacketDistributor.sendToPlayer(target, registryPayload);
            LOGGER.info("[SetWeaver] Synced player data and registry to {}",
                    target.getName().getString());
        } else {
            // Server-wide /reload — broadcast registry to all connected players
            for (ServerPlayer player : event.getPlayerList().getPlayers()) {
                // Also re-sync each player's own role/year
                String role = PlayerDataHelper.getRole(player);
                int year = PlayerDataHelper.getLevel(player);
                PacketDistributor.sendToPlayer(player, new PlayerDataPayload(role, year));
                PacketDistributor.sendToPlayer(player, registryPayload);
            }
            LOGGER.info("[SetWeaver] Broadcast registry sync to all players after /reload");
        }
    }

    /**
     * Syncs the player's role and year to their client on login.
     * This populates the {@link com.gilfort.setweaver.network.ClientPlayerDataCache}
     * so tooltips work immediately without waiting for the tick event.
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String role = PlayerDataHelper.getRole(player);
            int year = PlayerDataHelper.getLevel(player);
            PacketDistributor.sendToPlayer(player, new PlayerDataPayload(role, year));
            PacketDistributor.sendToPlayer(player, new RegistrySyncPayload(
                    ArmorSetDataRegistry.serializeToJson(),
                    AttributePackageManager.serializeToJson()));
            LOGGER.info("[SetWeaver] Synced player data and registry to client for {}",
                    player.getName().getString());
        }
    }

    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            LOGGER.info("[SetWeaver] Client is setting up!");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

            // Register the tooltip handler for armor sets
            ArmorSetTooltipHandler.register();
        }
    }
}
