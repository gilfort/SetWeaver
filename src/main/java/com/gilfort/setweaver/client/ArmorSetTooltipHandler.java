package com.gilfort.setweaver.client;

import com.gilfort.setweaver.SetWeaver;
import com.gilfort.setweaver.seteffects.ArmorPieces;
import com.gilfort.setweaver.seteffects.ArmorSetData;
import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import com.gilfort.setweaver.seteffects.AttributePackageManager;
import com.gilfort.setweaver.seteffects.SetIds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Adds the set bonus to the tooltip of every item in a set tag (hold SHIFT). If an item belongs to several sets,
 * SHIFT + mouse wheel pages through them.
 */
@EventBusSubscriber(modid = SetWeaver.MODID, value = Dist.CLIENT)
public final class ArmorSetTooltipHandler {

    /** Index of the displayed set when the hovered item belongs to several. */
    private static int currentSetPage = 0;
    /** Last hovered item, to reset the page when the item changes. */
    private static ItemStack lastHoveredStack = ItemStack.EMPTY;

    private ArmorSetTooltipHandler() {
    }

    /** Clears data received from the server when leaving it. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientHooks.resetClientState();
        currentSetPage = 0;
        lastHoveredStack = ItemStack.EMPTY;
    }

    /** SHIFT + wheel over a set item in a container screen switches the displayed set. */
    @SubscribeEvent
    public static void onMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!Screen.hasShiftDown() || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !ArmorSetDataRegistry.client().isItemInAnyRegisteredTag(slot.getItem())) {
            return;
        }
        if (event.getScrollDeltaY() > 0) {
            currentSetPage--;
        } else if (event.getScrollDeltaY() < 0) {
            currentSetPage++;
        }
        event.setCanceled(true);
    }

    /** Appends the set bonus lines. */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ArmorSetDataRegistry registry = ArmorSetDataRegistry.client();
        if (stack.isEmpty() || !registry.isItemInAnyRegisteredTag(stack)) {
            return;
        }
        if (!ItemStack.isSameItem(stack, lastHoveredStack)) {
            currentSetPage = 0;
            lastHoveredStack = stack.copy();
        }
        List<Component> tooltip = event.getToolTip();
        if (!Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.setweaver.shift_hint").withStyle(ChatFormatting.AQUA));
            return;
        }
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        List<ArmorSetDataRegistry.SetEntry> matching = new ArrayList<>();
        for (ArmorSetDataRegistry.SetEntry entry : registry.getApplicableEntries(ClientPlayerDataCache.getRole(), ClientPlayerDataCache.getLevel())) {
            if (stack.is(entry.tagKey())) {
                matching.add(entry);
            }
        }
        if (matching.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.setweaver.no_bonus_for_role").withStyle(ChatFormatting.GRAY));
            return;
        }
        currentSetPage = Math.floorMod(currentSetPage, matching.size());
        addSetLines(tooltip, player, matching.get(currentSetPage), matching.size());
    }

    private static void addSetLines(List<Component> tooltip, Player player, ArmorSetDataRegistry.SetEntry entry, int totalSets) {
        ArmorSetData data = entry.data();
        int worn = ArmorPieces.countWorn(player, entry.tagKey());

        MutableComponent header = Component.translatable("tooltip.setweaver.header", worn, data.getMaxThreshold());
        if (totalSets > 1) {
            header.append(Component.translatable("tooltip.setweaver.page", currentSetPage + 1, totalSets));
        }
        tooltip.add(header.withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.setweaver.set_name", setName(data, entry.tag())).withStyle(ChatFormatting.GOLD));

        ArmorSetData.PartData part = data.getActivePartData(worn);
        if (part == null) {
            int first = data.getFirstThreshold();
            if (first > worn) {
                tooltip.add(Component.translatable("tooltip.setweaver.equip_more", first - worn).withStyle(ChatFormatting.GRAY));
            }
            return;
        }

        if (part.getEffects() != null) {
            for (ArmorSetData.EffectData effect : part.getEffects()) {
                SetIds.effect(effect.getEffect()).ifPresent(holder -> tooltip.add(Component.literal("- ")
                        .append(holder.value().getDisplayName())
                        .append(" ")
                        .append(Component.translatable("enchantment.level." + (effect.getAmplifier() + 1)))
                        .withStyle(ChatFormatting.DARK_PURPLE)));
            }
        }
        if (part.getAttributes() != null && !part.getAttributes().isEmpty()) {
            tooltip.add(Component.translatable("tooltip.setweaver.attributes").withStyle(ChatFormatting.AQUA));
            addAttributeLines(tooltip, part.getAttributes());
        }
    }

    /** Adds attribute lines, collapsing attributes of the same package into one line unless ALT is held. */
    private static void addAttributeLines(List<Component> tooltip, List<ArmorSetData.AttributeData> attributes) {
        AttributePackageManager packages = AttributePackageManager.client();
        Set<String> present = new HashSet<>();
        attributes.forEach(a -> present.add(a.getAttribute()));

        Map<String, List<ArmorSetData.AttributeData>> groups = new LinkedHashMap<>();
        Map<ArmorSetData.AttributeData, String> packageOf = new LinkedHashMap<>();
        for (ArmorSetData.AttributeData attribute : attributes) {
            AttributePackageManager.AttributePackage pkg = packages.findBestPackageFor(attribute.getAttribute(), present);
            if (pkg != null) {
                groups.computeIfAbsent(pkg.getName(), k -> new ArrayList<>()).add(attribute);
                packageOf.put(attribute, pkg.getName());
            }
        }

        Set<String> shown = new HashSet<>();
        for (ArmorSetData.AttributeData attribute : attributes) {
            String pkgName = packageOf.get(attribute);
            if (pkgName == null) {
                addAttributeLine(tooltip, attribute, "  ");
            } else if (shown.add(pkgName)) {
                if (Screen.hasAltDown()) {
                    tooltip.add(Component.literal("  " + pkgName + ":").withStyle(ChatFormatting.YELLOW));
                    groups.get(pkgName).forEach(a -> addAttributeLine(tooltip, a, "    "));
                } else {
                    tooltip.add(Component.literal("  ")
                            .append(Component.translatable("tooltip.setweaver.package_collapsed", pkgName).withStyle(ChatFormatting.GREEN))
                            .append(Component.translatable("tooltip.setweaver.alt_hint").withStyle(ChatFormatting.GRAY)));
                }
            }
        }
    }

    private static void addAttributeLine(List<Component> tooltip, ArmorSetData.AttributeData data, String indent) {
        var holder = SetIds.attribute(data.getAttribute());
        if (holder.isEmpty()) {
            return;
        }
        AttributeModifier.Operation operation = SetIds.operation(data.getModifier()).orElse(AttributeModifier.Operation.ADD_VALUE);
        TooltipFlag flag = Minecraft.getInstance().options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL;
        String value = holder.get().value().toValueComponent(operation, data.getValue(), flag).getString();
        if (data.getValue() >= 0 && !value.startsWith("+") && !value.startsWith("-")) {
            value = "+" + value;
        }
        MutableComponent line = Component.literal(indent)
                .append(Component.translatable(holder.get().value().getDescriptionId()))
                .append(" " + value);
        if (operation == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
            line.append(Component.translatable("tooltip.setweaver.op.base").withStyle(ChatFormatting.DARK_GREEN));
        } else if (operation == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            line.append(Component.translatable("tooltip.setweaver.op.total").withStyle(ChatFormatting.DARK_GREEN));
        }
        tooltip.add(line.withStyle(ChatFormatting.GREEN));
    }

    /**
     * Returns the display name of a set: its {@code displayName}, otherwise the tag path without suffixes like
     * {@code _armor}, in title case ({@code zauberei:magiccloth_armor} → "Magiccloth").
     *
     * @param data      the set
     * @param tagString the set tag
     * @return the name
     */
    public static String setName(ArmorSetData data, String tagString) {
        if (data != null && data.getDisplayName() != null && !data.getDisplayName().isBlank()) {
            return data.getDisplayName();
        }
        String path = tagString.contains(":") ? tagString.substring(tagString.indexOf(':') + 1) : tagString;
        path = path.replaceAll("_(armou?rs?|set|equipment|gear)$", "");
        StringBuilder sb = new StringBuilder();
        for (String word : path.split("[_/]")) {
            if (!word.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
            }
        }
        return sb.toString();
    }
}
