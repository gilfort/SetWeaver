package com.gilfort.zauberei.item.armor;

import com.gilfort.zauberei.component.ComponentRegistry;
import com.gilfort.zauberei.item.armorbonus.ArmorSetData;
import com.gilfort.zauberei.item.armorbonus.ArmorSetDataRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Universal tooltip handler for the armor set effect system.
 * Uses NeoForge's ItemTooltipEvent to display set bonuses on ANY ArmorItem,
 * not just MagicclothArmorItem. This enables tooltips on vanilla and modded armor.
 *
 * <h3>Pagination (Issue #17)</h3>
 * When an item belongs to multiple sets simultaneously (e.g. via broad tags
 * like {@code c:armors}), only ONE set is shown at a time. The player can
 * hold SHIFT and scroll the mouse wheel to browse through all matching sets.
 * If only one set matches, behavior is identical to the non-paginated version.
 *
 * @see ArmorEffects — writes MAJOR/YEAR DataComponents to all worn ArmorItems
 * @see ArmorSetDataRegistry — provides set definitions per major/year/tag
 * @see <a href="https://github.com/gilfort/Zauberei-1.21.1/issues/17">Issue #17</a>
 */
@OnlyIn(Dist.CLIENT)
public class ArmorSetTooltipHandler {

    // ─── Pagination State ────────────────────────────────────────────────
    // These are static because the tooltip event fires per-frame and has no
    // persistent context. We track which "page" (= which set) the player is
    // currently viewing, and reset the page when they hover a different item.

    /** Index of the currently displayed set (0-based). */
    private static int currentSetPage = 0;

    /** The last item the player hovered — used to detect item changes and reset the page. */
    private static ItemStack lastHoveredStack = ItemStack.EMPTY;

    // ─── Registration ────────────────────────────────────────────────────

    /**
     * Register BOTH event listeners on the NeoForge event bus:
     * <ol>
     *   <li>{@link ItemTooltipEvent} — renders the tooltip content</li>
     *   <li>{@link InputEvent.MouseScrollingEvent} — handles SHIFT+scroll pagination</li>
     * </ol>
     * Call from {@code ClientModEvents.onClientSetup()}.
     */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ArmorSetTooltipHandler::onItemTooltip);
        NeoForge.EVENT_BUS.addListener(ArmorSetTooltipHandler::onMouseScroll);
    }

    // ─── Scroll Handler ──────────────────────────────────────────────────

    /**
     * Listens for mouse scroll events while SHIFT is held inside a container screen.
     * Increments / decrements {@link #currentSetPage} and consumes the event
     * so the inventory does not scroll simultaneously.
     *
     * <p>The actual clamping/wrapping of the page index happens in
     * {@link #onItemTooltip}, because only there do we know how many
     * sets actually match the currently hovered item.</p>
     */
/**
 * Listens for mouse scroll events INSIDE screens (inventory, chest, creative, …).
 * Uses {@link ScreenEvent.MouseScrolled.Pre} which fires before the screen
 * processes the scroll — allowing us to cancel it and prevent inventory scrolling.
 *
 * <p>Only reacts when SHIFT is held and the current screen is a container screen.</p>
 */
public static void onMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
    // Only react when SHIFT is held
    if (!Screen.hasShiftDown()) return;

    // Only inside container screens (inventory, chest, creative, …)
    if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) return;

    // Scroll direction: up (positive) = previous page, down (negative) = next page
    if (event.getScrollDeltaY() > 0) {
        currentSetPage--;
    } else if (event.getScrollDeltaY() < 0) {
        currentSetPage++;
    }

    // Consume the event so the container doesn't process the scroll
    event.setCanceled(true);
}


// ─── Tooltip Handler ─────────────────────────────────────────────────

    /**
     * Fires for every item tooltip.
     * <p>Guards:</p>
     * <ol>
     *   <li>Only ArmorItems</li>
     *   <li>Only items that belong to at least one registered set tag</li>
     *   <li>SHIFT-to-reveal</li>
     * </ol>
     *
     * <p>When multiple sets match, only the set at index {@link #currentSetPage}
     * is rendered. The page wraps around (scrolling past the last set goes
     * back to the first, and vice versa).</p>
     */
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();

        // Guard 1: Only ArmorItems
        if (!(stack.getItem() instanceof ArmorItem)) return;

        // Guard 2: Does this item belong to ANY registered set tag?
        if (!ArmorSetDataRegistry.isItemInAnyRegisteredTag(stack)) return;

        // ── Page reset on item change ────────────────────────────────────
        if (!ItemStack.isSameItem(stack, lastHoveredStack)) {
            currentSetPage = 0;
            lastHoveredStack = stack.copy();
        }

        // Guard 3: SHIFT-to-reveal
        if (!Screen.hasShiftDown()) {
            event.getToolTip().add(Component.literal("[SHIFT to view Set Bonus]")
                    .withStyle(ChatFormatting.AQUA));
            return;
        }

        // --- Determine Major and Year ---
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        var majorType = ComponentRegistry.MAJOR.value();
        var yearType = ComponentRegistry.YEAR.value();

        String major = stack.has(majorType) ? stack.get(majorType) : null;
        Integer yearObj = stack.has(yearType) ? stack.get(yearType) : null;

        // Fallback: read from currently worn armor stacks
        if (major == null || yearObj == null) {
            for (ItemStack worn : player.getArmorSlots()) {
                if (worn.has(majorType) && worn.has(yearType)) {
                    major = worn.get(majorType);
                    yearObj = worn.get(yearType);
                    break;
                }
            }
        }

        // Still no data → generic hint
        if (major == null || yearObj == null) {
            event.getToolTip().add(Component.literal("[Set Bonus available — equip to see details]")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        int year = yearObj;

        // --- Lookup registered tags for this major/year ---
        Set<String> registeredTags = ArmorSetDataRegistry.getRegisteredTags(major.toLowerCase(), year);
        if (registeredTags.isEmpty()) {
            event.getToolTip().add(Component.literal("[No set bonus for your current Major]")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        // ── Collect MATCHING tags ────────────────────────────────────────
        // Filter to only those tags that this specific item actually belongs to.
        // This is the list we paginate over.
        List<String> matchingTags = new ArrayList<>();
        for (String tagString : registeredTags) {
            ResourceLocation tagLoc;
            try {
                tagLoc = ResourceLocation.parse(tagString);
            } catch (Exception e) {
                continue;
            }
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagLoc);
            if (stack.is(tagKey)) {
                matchingTags.add(tagString);
            }
        }

        if (matchingTags.isEmpty()) {
            event.getToolTip().add(Component.literal("(Equip pieces to activate set bonus)")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        // ── Clamp page index (wrap around) ───────────────────────────────
        // Modular arithmetic ensures wrapping in both directions:
        //   scrolling past last  → goes to first
        //   scrolling before first → goes to last
        currentSetPage = ((currentSetPage % matchingTags.size()) + matchingTags.size()) % matchingTags.size();

        // ── Render the SINGLE selected set ───────────────────────────────
        String selectedTag = matchingTags.get(currentSetPage);
        renderSetTooltip(event, player, major, year, selectedTag, matchingTags.size());
    }

    // ─── Single-Set Rendering ────────────────────────────────────────────

    /**
     * Renders the tooltip for exactly one set. Extracted from the old for-loop
     * so that pagination can call it for just the selected set.
     *
     * @param event         the tooltip event to append lines to
     * @param player        the local player
     * @param major         the player's current major (lowercase)
     * @param year          the player's current year
     * @param tagString     the tag string of the set to render
     * @param totalSets     total number of matching sets (for the pagination header)
     */
    private static void renderSetTooltip(ItemTooltipEvent event, Player player,
                                         String major, int year,
                                         String tagString, int totalSets) {

        ResourceLocation tagLoc = ResourceLocation.parse(tagString);
        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagLoc);

        // Count worn pieces for this tag
        int wornParts = 0;
        for (ItemStack armorStack : player.getArmorSlots()) {
            if (!armorStack.isEmpty() && armorStack.is(tagKey)) {
                wornParts++;
            }
        }

        ArmorSetData data = ArmorSetDataRegistry.getData(major.toLowerCase(), year, tagString);
        if (data == null || data.getParts() == null) return;

        // Determine the maximum part threshold defined in the set
        int maxParts = data.getParts().keySet().stream()
                .map(k -> k.replace("Part", ""))
                .mapToInt(s -> {
                    try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
                })
                .max()
                .orElse(0);

        // ── Header line ──────────────────────────────────────────────────
        // Single set:    [Set Bonus 2/4]
        // Multiple sets: [Set Bonus 2/4]  ◄ 1/3 — Scroll ►
        String header = "[Set Bonus " + wornParts + "/" + maxParts + "]";
        if (totalSets > 1) {
            header += "  \u25C4 " + (currentSetPage + 1) + "/" + totalSets + " \u2014 Scroll \u25BA";
        }
        event.getToolTip().add(Component.literal(header)
                .withStyle(ChatFormatting.AQUA));

        // ── Set name line  ──────────────────────────────────────────
        String setName = resolveSetName(data, tagString);
        event.getToolTip().add(Component.literal("[Set: " + setName + "]")
                .withStyle(ChatFormatting.GOLD));


        // ── Part data: highest threshold ≤ worn pieces ───────────────────
        ArmorSetData.PartData partData = data.getActivePartData(wornParts);

        if (partData == null) {
            // Player hasn't reached ANY threshold yet → show hint for first one
            int firstThreshold = data.getParts().keySet().stream()
                    .map(k -> k.replace("Part", ""))
                    .mapToInt(s -> {
                        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return Integer.MAX_VALUE; }
                    })
                    .min()
                    .orElse(0);
            if (firstThreshold > 0 && firstThreshold != Integer.MAX_VALUE) {
                event.getToolTip().add(Component.literal(
                                "  Equip " + (firstThreshold - wornParts) + " more piece(s) for a bonus")
                        .withStyle(ChatFormatting.GRAY));
            }
            return;
        }

        // ── Optional: Show next upgrade hint ─────────────────────────────
        // If there's a higher threshold the player hasn't reached yet, hint at it.
        int nextThreshold = -1;
        for (String key : data.getParts().keySet()) {
            String numStr = key.replace("Part", "");
            try {
                int t = Integer.parseInt(numStr);
                if (t > wornParts && (nextThreshold == -1 || t < nextThreshold)) {
                    nextThreshold = t;
                }
            } catch (NumberFormatException e) { /* skip */ }
        }


        // --- Render Effects ---
        if (partData.getEffects() != null && !partData.getEffects().isEmpty()) {
            for (ArmorSetData.EffectData effect : partData.getEffects()) {
                ResourceLocation effectLoc;
                try {
                    effectLoc = ResourceLocation.parse(effect.getEffect());
                } catch (Exception e) {
                    continue;
                }

                MobEffect mobEffect = BuiltInRegistries.MOB_EFFECT.get(effectLoc);
                if (mobEffect == null) continue;

                Component effectName = mobEffect.getDisplayName();
                int level = effect.getAmplifier() + 1;
                Component levelRoman = Component.translatable("enchantment.level." + level);

                event.getToolTip().add(Component.literal("- ")
                        .append(effectName)
                        .append(" ")
                        .append(levelRoman)
                        .withStyle(ChatFormatting.DARK_PURPLE));
            }
        }

        // --- Render Attributes ---
        if (partData.getAttributes() != null && !partData.getAttributes().isEmpty()) {
            event.getToolTip().add(Component.literal("[Bonus Attributes]")
                    .withStyle(ChatFormatting.AQUA));

            for (Map.Entry<String, ArmorSetData.AttributeData> attr : partData.getAttributes().entrySet()) {
                ResourceLocation attrLoc;
                try {
                    attrLoc = ResourceLocation.parse(attr.getKey());
                } catch (Exception e) {
                    continue;
                }

                Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(attrLoc);
                if (attribute == null) continue;

                Component attributeName = Component.translatable(attribute.getDescriptionId());
                double rawValue = attr.getValue().getValue();
                String modifier = attr.getValue().getModifier();

                String displayValue;
                if (modifier != null && (modifier.equalsIgnoreCase("multiply")
                        || modifier.equalsIgnoreCase("multiply_base")
                        || modifier.equalsIgnoreCase("multiply_total"))) {
                    displayValue = String.format("+%.0f%%", rawValue * 100);
                } else {
                    displayValue = (rawValue == (long) rawValue)
                            ? String.format("+%d", (long) rawValue)
                            : String.format("+%.2f", rawValue);
                }

                event.getToolTip().add(attributeName.copy()
                        .append(" ")
                        .append(Component.literal(displayValue))
                        .withStyle(ChatFormatting.GREEN));
            }
        }
    }

    // ─── Set Name Resolution ─────────────────────────────────────────────

    /**
     * Resolves a human-readable set name from an {@link ArmorSetData} and its tag.
     *
     * <p>Resolution order:</p>
     * <ol>
     *   <li>{@code data.getDisplayName()} — explicit name from JSON (e.g. "Magiccloth Robes")</li>
     *   <li>Fallback: derive from tag path — strips common suffixes like {@code _armor},
     *       replaces underscores with spaces, and title-cases each word.
     *       Example: {@code "zauberei:magiccloth_armor"} → {@code "Magiccloth"}</li>
     * </ol>
     *
     * @param data      the set data (may contain a displayName)
     * @param tagString the full tag string, e.g. "zauberei:magiccloth_armor"
     * @return a formatted display name, never null
     */
    private static String resolveSetName(ArmorSetData data, String tagString) {
        // Priority 1: explicit displayName from JSON
        if (data != null && data.getDisplayName() != null && !data.getDisplayName().isBlank()) {
            return data.getDisplayName();
        }

        // Priority 2: derive from tag path
        try {
            ResourceLocation tagLoc = ResourceLocation.parse(tagString);
            String path = tagLoc.getPath(); // e.g. "magiccloth_armor"

            // Strip common armor-related suffixes
            path = path.replaceAll("_(armou?rs?|set|equipment|gear)$", "");

            // Split by underscore, capitalize each word
            String[] words = path.split("_");
            StringBuilder sb = new StringBuilder();
            for (String word : words) {
                if (!word.isEmpty()) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(Character.toUpperCase(word.charAt(0)));
                    if (word.length() > 1) sb.append(word.substring(1));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return tagString; // absolute fallback: raw tag string
        }
    }


}
