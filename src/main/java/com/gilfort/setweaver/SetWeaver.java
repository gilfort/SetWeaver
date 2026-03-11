package com.gilfort.setweaver;

import com.gilfort.setweaver.client.ArmorSetTooltipHandler;
import com.gilfort.setweaver.component.ComponentRegistry;
import com.gilfort.setweaver.seteffects.SetWeaverReloadListener;
import com.gilfort.setweaver.util.SetWeaverPlayerData;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
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



    @SubscribeEvent
    public void onAddReloadListenerEvent(AddReloadListenerEvent event) {

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
