package com.gilfort.zauberei;

import com.gilfort.zauberei.component.ComponentRegistry;
import com.gilfort.zauberei.creativetab.ZaubereiCreativeModeTabs;
import com.gilfort.zauberei.item.armor.ArmorEffects;
import com.gilfort.zauberei.item.ZaubereiItems;
import com.gilfort.zauberei.item.armor.ArmorSetTooltipHandler;
import com.gilfort.zauberei.item.armor.ZaubereiArmorMaterials;
import com.gilfort.zauberei.commands.CommandsService;
import com.gilfort.zauberei.item.armorbonus.ZaubereiReloadListener;
import com.gilfort.zauberei.structure.ZaubereiStructures;
import com.gilfort.zauberei.util.ZaubereiPlayerData;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
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

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Zauberei.MODID)
public class Zauberei
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "zauberei";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();


//    // Create a Deferred Register to hold Blocks which will all be registered under the "examplemod" namespace
//    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
//    // Create a Deferred Register to hold Items which will all be registered under the "examplemod" namespace
//    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
//    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "examplemod" namespace
//    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
//
//    // Creates a new Block with the id "examplemod:example_block", combining the namespace and path
//    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
//    // Creates a new BlockItem with the id "examplemod:example_block", combining the namespace and path
//    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);
//
//    // Creates a new food item with the id "examplemod:example_id", nutrition 1 and saturation 2
//    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
//            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));
//


    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public Zauberei(IEventBus modEventBus, ModContainer modContainer)
    {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        ZaubereiItems.register(modEventBus);
        ZaubereiCreativeModeTabs.register(modEventBus);
        ArmorEffects.register(modEventBus);
        ZaubereiArmorMaterials.register(modEventBus);
        ZaubereiStructures.STRUCTURES.register(modEventBus);
        ComponentRegistry.register(modEventBus);
        ZaubereiPlayerData.ATTACHMENT_TYPES.register(modEventBus);

        if (ModList.get().isLoaded("ars_nouveau")) {
            com.gilfort.zauberei.compat.ArsNouveauCompat.init(modEventBus);
        } else {
            LOGGER.info("[Zauberei] Ars Nouveau not found, skipping compatibility setup.");
        }


        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        CommandsService.init();
        NeoForge.EVENT_BUS.register(this);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

    }

//    // Add the example block item to the building blocks tab
//    private void addCreative(BuildCreativeModeTabContentsEvent event)
//    {
//        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS)
//            event.accept(EXAMPLE_BLOCK_ITEM);
//    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");

        //Loads all set_effect files from config/zauberei/set_effects and applies effects
        ZaubereiReloadListener.loadAllEffects();

    }
    public static ResourceLocation id(@NotNull String path) {
        return ResourceLocation.fromNamespaceAndPath(Zauberei.MODID, path);
    }



    @SubscribeEvent
    public void onAddReloadListenerEvent(AddReloadListenerEvent event) {
        // Hier deinen Listener hinzufügen

    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

            ArmorSetTooltipHandler.register();
        }
    }
}
