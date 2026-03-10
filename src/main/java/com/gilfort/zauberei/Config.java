package com.gilfort.zauberei;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.

// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<? extends String>> LETTER_ITEMS;
    public static final ModConfigSpec.ConfigValue<String> LETTER_TEXT;
    public static final ModConfigSpec.ConfigValue<Boolean> SPAWN_WITCHWOOD_SCHOOL;

    /**
     * When set to false, all Magiccloth Armor items and the Introduction Letter
     * are hidden from the Creative Tab and from item viewers like JEI/EMI.
     * Items remain registered to prevent crashes in existing worlds.
     * The set effect system (ArmorEffects, ArmorSetData, etc.) is always active.
     * Requires a game restart to take effect.
     */
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_MAGICAL_ARMOR;

    static {
        LETTER_ITEMS = BUILDER
                .comment("\nItems, the player can get when using the Introduction Letter\n")
                .defineListAllowEmpty(
                        List.of("letter_items"),
                        List.of(
                                "zauberei:magiccloth_helmet",
                                "zauberei:magiccloth_chestplate",
                                "zauberei:magiccloth_leggings",
                                "zauberei:magiccloth_boots"
                        ),
                        o -> o instanceof String
                );

        LETTER_TEXT = BUILDER
                .comment("\nText shown in the introduction letter item. Use %player% as a placeholder for the player name.\n")
                .define("letter_text",
                        """
                        Dear %player%,
                        
                        Congratulations!
                        Our detection system - "Oculus" - has recognized your magical talent.
                        We are pleased to inform you that you have been accepted into our school as a scholarship student.
                        From today on you are part of the Witchwood Academy.
                        
                        To receive your first-year supplies, please tear this letter apart.
                        Welcome to a world where magic comes to life.
                        We look forward to guiding you on your journey and helping you master your new skills.
                        
                        Yours sincerely,
                        Professor McDumblesnape
                        Headmaster of Witchwood Academy
                        """);

        SPAWN_WITCHWOOD_SCHOOL = BUILDER
                .comment("\nWhether the Witchwood School should be spawned in the world. (at 0 250 0)\n")
                .define("spawn_witchwood_school", false);

        ENABLE_MAGICAL_ARMOR = BUILDER
                .comment("\nWhether the Magiccloth Armor items and the Introduction Letter are enabled.\n"
                        + "When set to false, these items are hidden from the Creative Tab and item viewers (JEI/EMI).\n"
                        + "Items remain registered to prevent crashes in existing worlds.\n"
                        + "The set effect system remains active regardless of this setting.\n"
                        + "Requires a game restart to take effect.\n")
                .define("enable_magical_armor", true);

    }

    static final ModConfigSpec SPEC = BUILDER.build();


    public static List<ItemStack> getLetterItems() {

        return LETTER_ITEMS.get().stream()
                .map(itemName -> BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemName)))
                .filter(item -> item != Items.AIR)
                .map(ItemStack::new)
                .toList();
    }

    public static String getLetterText(String playerName) {
        return LETTER_TEXT.get().replace("%player%", playerName).replace("\r", "");
    }


}