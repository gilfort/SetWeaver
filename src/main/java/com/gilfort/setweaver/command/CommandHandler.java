package com.gilfort.setweaver.command;


import com.gilfort.setweaver.datagen.PlayerDataHelper;
import com.gilfort.setweaver.network.OpenSetsGuiPayload;
import com.gilfort.setweaver.seteffects.ArmorSetData;
import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import com.gilfort.setweaver.seteffects.SetWeaverReloadListener;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Command handler for the SetWeaver mod.
 *
 * <h3>Command Tree:</h3>
 * <pre>
 * /setweaver
 *   ├── setrole &lt;role&gt;                           Set your own role
 *   ├── checkrole &lt;player&gt;                        Check a player's role
 *   ├── setLevel &lt;Level&gt;                             Set your own Level
 *   ├── checkLevel &lt;player&gt;                         Check a player's Level
 *   ├── debug
 *   │   ├── tag &lt;namespace&gt; &lt;tagpath&gt;             Debug: check if worn armor matches a tag
 *   │   ├── sets                                    Debug: show loaded sets for your role/Level
 *   │   └── reload                                  Reload all set definitions from config
 *   └── sets
 *       ├── list                                    List all loaded set definitions
 *       ├── info &lt;namespace&gt; &lt;tagpath&gt;             Show full details of a set definition
 *       ├── validate                                Check all JSON files for errors
 *       ├── create &lt;ns&gt; &lt;tagpath&gt; universal        Template: all roles + all Levels
 *       ├── create &lt;ns&gt; &lt;tagpath&gt; all_roles &lt;y&gt;  Template: all roles + specific Level
 *       └── create &lt;ns&gt; &lt;tagpath&gt; &lt;role&gt; &lt;Level&gt;  Template: specific role + Level
 * </pre>
 */
public class CommandHandler {

    private static final File SET_ARMOR_DIR = new File(
            FMLPaths.CONFIGDIR.get().toFile(),
            "setweaver" + File.separator + "set_armor"
    );

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ═══════════════════════════════════════════════════════════════════════
    //  SUGGESTION PROVIDERS
    // ═══════════════════════════════════════════════════════════════════════

    /** Suggests all real role names from the registry (excludes wildcard). */
    public static final SuggestionProvider<CommandSourceStack> ROLE_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(ArmorSetDataRegistry.getRoles(), builder);

    /**
     * Suggests all unique namespaces from item tags currently loaded in the game.
     * Reads directly from the item tag registry, so only tags that exist in the
     * current modpack are shown.
     *
     * Examples: "c", "minecraft", "setweaver", "ars_nouveau", "forge"
     */
    public static final SuggestionProvider<CommandSourceStack> TAG_NAMESPACE_SUGGESTIONS = (ctx, builder) -> {
        Set<String> namespaces = BuiltInRegistries.ITEM.getTagNames()
                .map(tagKey -> tagKey.location().getNamespace())
                .collect(Collectors.toSet());
        return SharedSuggestionProvider.suggest(namespaces, builder);
    };

    /**
     * Suggests tag paths filtered by the namespace already entered.
     * Only shows tags that actually exist in the current modpack for that namespace.
     *
     * Example: namespace="c" → suggests "armors", "iron_armors", "gold_ingots", ...
     */
    public static final SuggestionProvider<CommandSourceStack> TAG_PATH_SUGGESTIONS = (ctx, builder) -> {
        String namespace = StringArgumentType.getString(ctx, "namespace");
        List<String> paths = BuiltInRegistries.ITEM.getTagNames()
                .filter(tagKey -> tagKey.location().getNamespace().equals(namespace))
                .map(tagKey -> tagKey.location().getPath())
                .sorted()
                .toList();
        return SharedSuggestionProvider.suggest(paths, builder);
    };

    /**
     * Suggests tag paths filtered by namespace, for the "info" context.
     * Uses "info_namespace" argument name to avoid collision.
     */
    public static final SuggestionProvider<CommandSourceStack> INFO_TAG_PATH_SUGGESTIONS = (ctx, builder) -> {
        String namespace = StringArgumentType.getString(ctx, "info_namespace");
        List<String> paths = BuiltInRegistries.ITEM.getTagNames()
                .filter(tagKey -> tagKey.location().getNamespace().equals(namespace))
                .map(tagKey -> tagKey.location().getPath())
                .sorted()
                .toList();
        return SharedSuggestionProvider.suggest(paths, builder);
    };

    /**
     * Suggests tag paths filtered by namespace, for the "debug tag" context.
     * Uses "debug_namespace" argument name to avoid collision.
     */
    public static final SuggestionProvider<CommandSourceStack> DEBUG_TAG_PATH_SUGGESTIONS = (ctx, builder) -> {
        String namespace = StringArgumentType.getString(ctx, "debug_namespace");
        List<String> paths = BuiltInRegistries.ITEM.getTagNames()
                .filter(tagKey -> tagKey.location().getNamespace().equals(namespace))
                .map(tagKey -> tagKey.location().getPath())
                .sorted()
                .toList();
        return SharedSuggestionProvider.suggest(paths, builder);
    };

    // ═══════════════════════════════════════════════════════════════════════
    //  COMMAND REGISTRATION
    // ═══════════════════════════════════════════════════════════════════════

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("setweaver")

                        // ── Player Commands ──────────────────────────────
                        .then(Commands.literal("setrole")
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .suggests(ROLE_SUGGESTIONS)
                                        .executes(CommandHandler::setRoleCommand)))
                        .then(Commands.literal("checkrole")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(CommandHandler::checkRoleCommand)))
                        .then(Commands.literal("setLevel")
                                .then(Commands.argument("Level", IntegerArgumentType.integer())
                                        .executes(CommandHandler::setLevelCommand)))
                        .then(Commands.literal("checkLevel")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(CommandHandler::checkLevelCommand)))
                        // /setweaver tag_items <namespace> <tagpath>
                        .then(Commands.literal("tag_items")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("tag_namespace", StringArgumentType.word())
                                        .suggests(TAG_NAMESPACE_SUGGESTIONS)
                                        .then(Commands.argument("tag_path", StringArgumentType.word())
                                                .suggests(TAG_ITEMS_PATH_SUGGESTIONS)
                                                .executes(ctx -> tagItems(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "tag_namespace"),
                                                        StringArgumentType.getString(ctx, "tag_path"))))))
                        // /setweaver list_tags [filter]
                        .then(Commands.literal("list_tags")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> listTags(ctx.getSource(), ""))
                                .then(Commands.argument("filter", StringArgumentType.greedyString())
                                        .executes(ctx -> listTags(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "filter")))))

                        // ── Debug Commands (OP 2+) ───────────────────────
                        .then(Commands.literal("debug")
                                .requires(src -> src.hasPermission(2))
                                // /setweaver debug tag <namespace> <tagpath>
                                .then(Commands.literal("tag")
                                        .then(Commands.argument("debug_namespace", StringArgumentType.word())
                                                .suggests(TAG_NAMESPACE_SUGGESTIONS)
                                                .then(Commands.argument("debug_tagpath", StringArgumentType.word())
                                                        .suggests(DEBUG_TAG_PATH_SUGGESTIONS)
                                                        .executes(ctx -> debugTag(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "debug_namespace"),
                                                                StringArgumentType.getString(ctx, "debug_tagpath"))))))
                                .then(Commands.literal("sets")
                                        .executes(ctx -> debugSets(ctx.getSource())))
                                .then(Commands.literal("reload")
                                        .executes(ctx -> reloadArmorEffects(ctx.getSource()))))

                        // ── Sets Commands (OP 2+) ────────────────────────
                        .then(Commands.literal("sets")
                                .requires(src -> src.hasPermission(2))
                                // /setweaver sets (ohne subcommand) → öffnet die GUI
                                .executes(ctx -> openSetsGui(ctx.getSource()))

                                // /setweaver sets list
                                .then(Commands.literal("list")
                                        .executes(ctx -> setsList(ctx.getSource())))

                                // /setweaver sets info <namespace> <tagpath>
                                .then(Commands.literal("info")
                                        .then(Commands.argument("info_namespace", StringArgumentType.word())
                                                .suggests(TAG_NAMESPACE_SUGGESTIONS)
                                                .then(Commands.argument("info_tagpath", StringArgumentType.word())
                                                        .suggests(INFO_TAG_PATH_SUGGESTIONS)
                                                        .executes(ctx -> setsInfo(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "info_namespace"),
                                                                StringArgumentType.getString(ctx, "info_tagpath"))))))

                                // /setweaver sets validate
                                .then(Commands.literal("validate")
                                        .executes(ctx -> setsValidate(ctx.getSource())))

                                // /setweaver sets create <namespace> <tagpath> ...
                                .then(Commands.literal("create")
                                        .then(Commands.argument("namespace", StringArgumentType.word())
                                                .suggests(TAG_NAMESPACE_SUGGESTIONS)
                                                .then(Commands.argument("tagpath", StringArgumentType.word())
                                                        .suggests(TAG_PATH_SUGGESTIONS)

                                                        // ... universal
                                                        .then(Commands.literal("universal")
                                                                .executes(ctx -> setsCreate(ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "namespace"),
                                                                        StringArgumentType.getString(ctx, "tagpath"),
                                                                        ArmorSetDataRegistry.WILDCARD_ROLE,
                                                                        ArmorSetDataRegistry.WILDCARD_LEVEL)))

                                                        // ... all_roles <Level>
                                                        .then(Commands.literal("all_roles")
                                                                .then(Commands.argument("Level", IntegerArgumentType.integer(1))
                                                                        .executes(ctx -> setsCreate(ctx.getSource(),
                                                                                StringArgumentType.getString(ctx, "namespace"),
                                                                                StringArgumentType.getString(ctx, "tagpath"),
                                                                                ArmorSetDataRegistry.WILDCARD_ROLE,
                                                                                IntegerArgumentType.getInteger(ctx, "Level")))))

                                                        // ... <role> <Level>
                                                        .then(Commands.argument("role", StringArgumentType.word())
                                                                .suggests(ROLE_SUGGESTIONS)
                                                                .then(Commands.argument("Level", IntegerArgumentType.integer(1))
                                                                        .executes(ctx -> setsCreate(ctx.getSource(),
                                                                                StringArgumentType.getString(ctx, "namespace"),
                                                                                StringArgumentType.getString(ctx, "tagpath"),
                                                                                StringArgumentType.getString(ctx, "role"),
                                                                                IntegerArgumentType.getInteger(ctx, "Level")))))))))
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PLAYER COMMANDS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Lists all items registered under a given item tag.
     * Each item is shown with its registry name and a hover tooltip.
     */
    private static int tagItems(CommandSourceStack source, String namespace, String tagPath) {
        ResourceLocation tagLoc = ResourceLocation.fromNamespaceAndPath(namespace, tagPath);
        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagLoc);

        List<Item> items = BuiltInRegistries.ITEM.getOrCreateTag(tagKey)
                .stream()
                .map(Holder::value)
                .toList();

        if (items.isEmpty()) {
            source.sendSystemMessage(Component.literal("[SetWeaver] ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("Tag ")
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("#" + tagLoc)
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" contains no items (or does not exist).")
                            .withStyle(ChatFormatting.WHITE)));
            return 1;
        }

        // Header
        source.sendSystemMessage(Component.literal("[SetWeaver] ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal("Tag ")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal("#" + tagLoc)
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" (" + items.size() + " items):")
                        .withStyle(ChatFormatting.WHITE)));

        // Each item
        for (Item item : items) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            String displayName = new ItemStack(item).getHoverName().getString();

            MutableComponent line = Component.literal("  \u25B8 ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(displayName)
                            .withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" (" + itemId + ")")
                            .withStyle(ChatFormatting.DARK_GRAY));

            // Hover: show full registry path
            line.withStyle(style -> style.withHoverEvent(
                    new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal(itemId.toString()))));

            source.sendSystemMessage(line);
        }

        return 1;
    }

    /**
     * Lists all registered item tags, optionally filtered by a search string.
     * Each tag is clickable and runs /setweaver tag_items for that tag.
     */
    private static int listTags(CommandSourceStack source, String filter) {
        List<TagKey<Item>> allTags = BuiltInRegistries.ITEM.getTagNames()
                .sorted(Comparator.comparing(t -> t.location().toString()))
                .toList();

        List<TagKey<Item>> filtered = filter.isEmpty()
                ? allTags
                : allTags.stream()
                .filter(t -> t.location().toString().contains(filter.toLowerCase()))
                .toList();

        if (filtered.isEmpty()) {
            source.sendSystemMessage(Component.literal("[SetWeaver] ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("No tags found" +
                                    (filter.isEmpty() ? "." : " matching \"" + filter + "\"."))
                            .withStyle(ChatFormatting.YELLOW)));
            return 1;
        }

        // Header
        source.sendSystemMessage(Component.literal("[SetWeaver] ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(filtered.size() + " tag(s) found"
                                + (filter.isEmpty() ? ":" : " matching \"" + filter + "\":"))
                        .withStyle(ChatFormatting.WHITE)));

        // Max 50 anzeigen, sonst wird der Chat überflutet
        int limit = Math.min(filtered.size(), 50);
        for (int i = 0; i < limit; i++) {
            ResourceLocation loc = filtered.get(i).location();
            String cmd = "/setweaver tag_items " + loc.getNamespace() + " " + loc.getPath();

            MutableComponent line = Component.literal("  #" + loc)
                    .withStyle(ChatFormatting.YELLOW)
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Click to show items in this tag"))));

            source.sendSystemMessage(line);
        }

        if (filtered.size() > limit) {
            source.sendSystemMessage(Component.literal("  ... and " + (filtered.size() - limit) + " more. Use a filter to narrow down.")
                    .withStyle(ChatFormatting.GRAY));
        }

        return 1;
    }


    private static int setRoleCommand(CommandContext<CommandSourceStack> context) {
        String role = StringArgumentType.getString(context, "role");
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return Command.SINGLE_SUCCESS;
        }

        PlayerDataHelper.setRole(player, role);
        source.sendSuccess(() -> Component.literal("Role set to: " + role), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int setLevelCommand(CommandContext<CommandSourceStack> context) {
        int Level = IntegerArgumentType.getInteger(context, "Level");
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return Command.SINGLE_SUCCESS;
        }

        PlayerDataHelper.setLevel(player, Level);
        source.sendSuccess(() -> Component.literal("Level set to: " + Level), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int checkRoleCommand(CommandContext<CommandSourceStack> context) {
        String playerName = StringArgumentType.getString(context, "player");
        CommandSourceStack source = context.getSource();

        ServerPlayer targetPlayer = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (targetPlayer == null) {
            source.sendFailure(Component.literal("Player " + playerName + " not found."));
            return Command.SINGLE_SUCCESS;
        }

        String role = PlayerDataHelper.getRole(targetPlayer);
        source.sendSuccess(() -> Component.literal("Player " + playerName + " has role: " + role), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int checkLevelCommand(CommandContext<CommandSourceStack> context) {
        String playerName = StringArgumentType.getString(context, "player");
        CommandSourceStack source = context.getSource();

        ServerPlayer targetPlayer = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (targetPlayer == null) {
            source.sendFailure(Component.literal("Player " + playerName + " not found."));
            return Command.SINGLE_SUCCESS;
        }

        int Level = PlayerDataHelper.getLevel(targetPlayer);
        source.sendSuccess(() -> Component.literal("Player " + playerName + " is in Level: " + Level), true);
        return Command.SINGLE_SUCCESS;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  DEBUG COMMANDS
    // ═══════════════════════════════════════════════════════════════════════

    private static int reloadArmorEffects(CommandSourceStack source) {
        SetWeaverReloadListener.loadAllEffects();
        source.sendSuccess(() -> Component.literal("[SetWeaver] Reloaded Set-Effects"), true);
        return 1;
    }

    private static int debugSets(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Player only."));
            return 0;
        }

        String role = PlayerDataHelper.getRole(player);
        int Level = PlayerDataHelper.getLevel(player);

        source.sendSystemMessage(Component.literal("[SetWeaver Debug] ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal("role=" + role + ", Level=" + Level)));

        Set<String> tags = ArmorSetDataRegistry.getRegisteredTags(role.toLowerCase(), Level);
        if (tags.isEmpty()) {
            source.sendSystemMessage(Component.literal("No set tags registered for this role/Level.")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        source.sendSystemMessage(Component.literal("Registered tags (" + tags.size() + "):")
                .withStyle(ChatFormatting.GRAY));

        for (String tagString : tags) {
            debugTagInternal(source, player, tagString);
        }
        return 1;
    }

    /**
     * /setweaver debug tag &lt;namespace&gt; &lt;tagpath&gt;
     * Example: /setweaver debug tag setweaver magiccloth_armor
     */
    private static int debugTag(CommandSourceStack source, String namespace, String tagpath) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Player only."));
            return 0;
        }

        String tagString = namespace + ":" + tagpath;
        debugTagInternal(source, player, tagString);
        return 1;
    }

    private static void debugTagInternal(CommandSourceStack source, ServerPlayer player, String tagString) {
        ResourceLocation tagLoc = ResourceLocation.tryParse(tagString);
        if (tagLoc == null) {
            source.sendFailure(Component.literal("Invalid tag ResourceLocation: " + tagString));
            return;
        }

        String role = PlayerDataHelper.getRole(player);
        int Level = PlayerDataHelper.getLevel(player);
        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagLoc);

        int count = 0;
        source.sendSystemMessage(Component.literal("")
                .append(Component.literal("Tag ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(tagString).withStyle(ChatFormatting.GOLD)));

        for (ItemStack armorStack : player.getArmorSlots()) {
            boolean matches = !armorStack.isEmpty() && armorStack.is(tagKey);
            if (matches) count++;

            String itemId = armorStack.isEmpty()
                    ? "<empty>"
                    : String.valueOf(BuiltInRegistries.ITEM.getKey(armorStack.getItem()));

            source.sendSystemMessage(Component.literal(" - ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(itemId).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  matches=").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.valueOf(matches))
                            .withStyle(matches ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }

        source.sendSystemMessage(Component.literal(" => wornParts=" + count + "/4")
                .withStyle(ChatFormatting.AQUA));

        ArmorSetData data = ArmorSetDataRegistry.getData(role.toLowerCase(), Level, tagString);
        if (data == null) {
            source.sendSystemMessage(Component.literal("No set definition loaded for this tag at role/Level.")
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            source.sendSystemMessage(Component.literal("Set definition: FOUND (parts keys=" + data.getParts().keySet() + ")")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SETS COMMANDS — list / info / validate / create
    // ═══════════════════════════════════════════════════════════════════════

    // ─── /setweaver sets list ─────────────────────────────────────────────

    /**
     * Lists all loaded set definitions with scope color-coding:
     * <ul>
     *   <li>🟢 Green  = specific role + Level</li>
     *   <li>🔵 Blue   = all_roles (Level-specific wildcard)</li>
     *   <li>🟣 Purple = all_roles_all_Levels (universal)</li>
     * </ul>
     * Each entry is clickable and runs {@code /setweaver sets info} for that tag.
     */
    private static int openSetsGui(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Player only."));
            return 0;
        }

        // Sende Packet an den Client → Client öffnet den Screen
        PacketDistributor.sendToPlayer(player, new OpenSetsGuiPayload());

        source.sendSuccess(
                () -> Component.literal("[SetWeaver] Opening Sets Manager..."), false);
        return 1;
    }



    private static int setsList(CommandSourceStack source) {
        List<ArmorSetDataRegistry.SetEntry> entries = ArmorSetDataRegistry.getAllEntries();

        if (entries.isEmpty()) {
            source.sendSystemMessage(Component.literal("[SetWeaver] No set definitions loaded.")
                    .withStyle(ChatFormatting.YELLOW));
            source.sendSystemMessage(Component.literal("  Use /setweaver sets create <namespace> <tagpath> to get started!")
                    .withStyle(ChatFormatting.GRAY));
            return 1;
        }

        source.sendSystemMessage(Component.literal("[SetWeaver] ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(entries.size() + " set definition(s) loaded:")
                        .withStyle(ChatFormatting.WHITE)));

        // Sort: universal first, then all_roles, then specific
        entries.sort(Comparator
                .comparingInt((ArmorSetDataRegistry.SetEntry e) -> scopeOrder(e))
                .thenComparing(ArmorSetDataRegistry.SetEntry::tag));

        for (ArmorSetDataRegistry.SetEntry entry : entries) {
            ChatFormatting scopeColor = scopeColor(entry);
            String scopeLabel = formatScope(entry);

            String displayName = entry.data().getDisplayName();
            String nameHint = (displayName != null && !displayName.isBlank())
                    ? " (" + displayName + ")"
                    : "";

            // Build the info command with namespace + tagpath (split by ":")
            String infoCommand = buildInfoCommand(entry.tag());

            // Build clickable line
            MutableComponent line = Component.literal("  ")
                    .append(Component.literal("● ").withStyle(scopeColor))
                    .append(Component.literal(scopeLabel).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(entry.tag()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(nameHint).withStyle(ChatFormatting.DARK_AQUA));

            line.withStyle(style -> style
                    .withClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND, infoCommand))
                    .withHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Click to view details"))));

            source.sendSystemMessage(line);
        }

        // Legend
        source.sendSystemMessage(Component.literal(""));
        source.sendSystemMessage(Component.literal("  ")
                .append(Component.literal("● ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("specific  ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("● ").withStyle(ChatFormatting.BLUE))
                .append(Component.literal("all_roles  ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("● ").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal("universal").withStyle(ChatFormatting.GRAY)));

        return 1;
    }

    // ─── /setweaver sets info <namespace> <tagpath> ───────────────────────

    /**
     * Shows the complete set definition for a given tag,
     * including all thresholds with their effects and attributes.
     * Searches across all scopes and shows every match.
     */
    private static int setsInfo(CommandSourceStack source, String namespace, String tagpath) {
        String tagString = namespace + ":" + tagpath;

        // Find ALL entries for this tag (across scopes)
        List<ArmorSetDataRegistry.SetEntry> matches = ArmorSetDataRegistry.getAllEntries().stream()
                .filter(e -> e.tag().equals(tagString))
                .sorted(Comparator.comparingInt(CommandHandler::scopeOrder))
                .toList();

        if (matches.isEmpty()) {
            source.sendSystemMessage(Component.literal("[SetWeaver] No set definition found for tag: " + tagString)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        for (ArmorSetDataRegistry.SetEntry entry : matches) {
            String scope = formatScope(entry);
            String displayName = entry.data().getDisplayName();

            // Header
            source.sendSystemMessage(Component.literal(""));
            source.sendSystemMessage(Component.literal("[SetWeaver] Set: ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(displayName != null ? displayName : tagString)
                            .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                    .append(Component.literal(" (" + tagString + ")")
                            .withStyle(ChatFormatting.GRAY)));

            source.sendSystemMessage(Component.literal("  Scope: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(scope)
                            .withStyle(scopeColor(entry))));

            // Parts / Thresholds
            Map<String, ArmorSetData.PartData> parts = entry.data().getParts();
            if (parts == null || parts.isEmpty()) {
                source.sendSystemMessage(Component.literal("  (no parts defined)")
                        .withStyle(ChatFormatting.YELLOW));
                continue;
            }

            // Sort part keys naturally: 1Part, 2Part, 3Part, 4Part
            List<String> sortedKeys = new ArrayList<>(parts.keySet());
            sortedKeys.sort(Comparator.comparingInt(CommandHandler::extractPartNumber));

            source.sendSystemMessage(Component.literal("  Thresholds: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.join(", ", sortedKeys))
                            .withStyle(ChatFormatting.WHITE)));

            for (String partKey : sortedKeys) {
                ArmorSetData.PartData partData = parts.get(partKey);

                source.sendSystemMessage(Component.literal(""));
                source.sendSystemMessage(Component.literal("  ── " + partKey + " ──")
                        .withStyle(ChatFormatting.GOLD));

                // Effects
                if (partData.getEffects() != null && !partData.getEffects().isEmpty()) {
                    for (ArmorSetData.EffectData ed : partData.getEffects()) {
                        String levelRoman = toRoman(ed.getAmplifier() + 1);
                        source.sendSystemMessage(Component.literal("    Effect: ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(ed.getEffect() + " " + levelRoman)
                                        .withStyle(ChatFormatting.AQUA)));
                    }
                } else {
                    source.sendSystemMessage(Component.literal("    Effects: none")
                            .withStyle(ChatFormatting.DARK_GRAY));
                }

                // Attributes
                if (partData.getAttributes() != null && !partData.getAttributes().isEmpty()) {
                    for (Map.Entry<String, ArmorSetData.AttributeData> attr : partData.getAttributes().entrySet()) {
                        String modifier = attr.getValue().getModifier() != null
                                ? attr.getValue().getModifier() : "addition";
                        String valueStr = formatAttributeValue(attr.getValue().getValue(), modifier);

                        source.sendSystemMessage(Component.literal("    Attribute: ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(valueStr + " " + attr.getKey())
                                        .withStyle(ChatFormatting.GREEN))
                                .append(Component.literal(" (" + modifier + ")")
                                        .withStyle(ChatFormatting.DARK_GRAY)));
                    }
                } else {
                    source.sendSystemMessage(Component.literal("    Attributes: none")
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
        return 1;
    }

    // ─── /setweaver sets validate ─────────────────────────────────────────

    /**
     * Walks through all JSON files in the config directory and validates them.
     * Delegates actual validation to {@link SetWeaverReloadListener#validateAllFiles()}.
     */
    private static int setsValidate(CommandSourceStack source) {
        source.sendSystemMessage(Component.literal("[SetWeaver] Validating set definitions...")
                .withStyle(ChatFormatting.AQUA));

        List<SetWeaverReloadListener.ValidationResult> results = SetWeaverReloadListener.validateAllFiles();

        if (results.isEmpty()) {
            source.sendSystemMessage(Component.literal("  No JSON files found in config directory.")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        int ok = 0, warnings = 0, errors = 0;

        for (SetWeaverReloadListener.ValidationResult result : results) {
            switch (result.status()) {
                case OK -> {
                    ok++;
                    source.sendSystemMessage(Component.literal("  ✔ " + result.filePath())
                            .withStyle(ChatFormatting.GREEN));
                }
                case WARNING -> {
                    warnings++;
                    source.sendSystemMessage(Component.literal("  ⚠ " + result.filePath())
                            .withStyle(ChatFormatting.YELLOW));
                    source.sendSystemMessage(Component.literal("     → " + result.message())
                            .withStyle(ChatFormatting.YELLOW));
                }
                case ERROR -> {
                    errors++;
                    source.sendSystemMessage(Component.literal("  ✘ " + result.filePath())
                            .withStyle(ChatFormatting.RED));
                    source.sendSystemMessage(Component.literal("     → " + result.message())
                            .withStyle(ChatFormatting.RED));
                }
            }
        }

        source.sendSystemMessage(Component.literal(""));
        source.sendSystemMessage(Component.literal("[SetWeaver] Result: ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(ok + " OK").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(", ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(warnings + " Warning(s)").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(", ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(errors + " Error(s)").withStyle(
                        errors > 0 ? ChatFormatting.RED : ChatFormatting.GREEN)));

        return 1;
    }

    // ─── /setweaver sets create <namespace> <tagpath> <scope> [Level] ─────

    /**
     * Creates a template JSON file for a set effect definition.
     * Generates the file in the correct directory based on scope.
     */
    private static int setsCreate(CommandSourceStack source, String namespace, String tagpath,
                                  String role, int Level) {
        String tagString = namespace + ":" + tagpath;

        // Verify the tag actually exists in the pack
        ResourceLocation tagLoc = ResourceLocation.tryParse(tagString);
        if (tagLoc == null) {
            source.sendFailure(Component.literal("Invalid tag: " + tagString));
            return 0;
        }

        // Check if the tag exists in the item registry
        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagLoc);
        boolean tagExists = BuiltInRegistries.ITEM.getTagNames()
                .anyMatch(t -> t.equals(tagKey));

        if (!tagExists) {
            source.sendSystemMessage(Component.literal("[SetWeaver] ⚠ Tag '" + tagString + "' does not exist in the current pack.")
                    .withStyle(ChatFormatting.YELLOW));
            source.sendSystemMessage(Component.literal("  The file will be created, but no items will match this tag.")
                    .withStyle(ChatFormatting.GRAY));
            source.sendSystemMessage(Component.literal("  Use [TAB] to see available tags.")
                    .withStyle(ChatFormatting.GRAY));
        }

        // Build file path
        String filename = namespace + "__" + tagpath + ".json";
        File targetFile;

        boolean isUniversal = ArmorSetDataRegistry.WILDCARD_ROLE.equals(role)
                && Level == ArmorSetDataRegistry.WILDCARD_LEVEL;
        boolean isAllRoles = ArmorSetDataRegistry.WILDCARD_ROLE.equals(role)
                && Level != ArmorSetDataRegistry.WILDCARD_LEVEL;

        if (isUniversal) {
            targetFile = new File(SET_ARMOR_DIR,
                    "all_roles_all_Levels" + File.separator + filename);
        } else if (isAllRoles) {
            targetFile = new File(SET_ARMOR_DIR,
                    "all_roles" + File.separator + Level + File.separator + filename);
        } else {
            targetFile = new File(SET_ARMOR_DIR,
                    role.toLowerCase() + File.separator + Level + File.separator + filename);
        }

        // Check if file already exists
        if (targetFile.exists()) {
            String infoCmd = buildInfoCommand(tagString);
            source.sendFailure(Component.literal("File already exists: "
                    + SET_ARMOR_DIR.toPath().relativize(targetFile.toPath())));
            source.sendSystemMessage(Component.literal("  ")
                    .append(Component.literal(infoCmd)
                            .withStyle(ChatFormatting.AQUA)
                            .withStyle(style -> style
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, infoCmd))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("Click to view"))))));
            return 0;
        }

        // Create directories
        targetFile.getParentFile().mkdirs();

        // Build template JSON
        String template = """
                {
                  "displayName": "%s",
                  "parts": {
                    "2Part": {
                      "Effects": [
                        { "Effect": "minecraft:speed", "Amplifier": 0 }
                      ],
                      "Attributes": {
                        "minecraft:generic.armor": { "value": 2.0, "modifier": "addition" }
                      }
                    },
                    "4Part": {
                      "Effects": [
                        { "Effect": "minecraft:speed", "Amplifier": 1 },
                        { "Effect": "minecraft:night_vision", "Amplifier": 0 }
                      ],
                      "Attributes": {
                        "minecraft:generic.armor": { "value": 5.0, "modifier": "addition" },
                        "minecraft:generic.movement_speed": { "value": 0.1, "modifier": "multiply_base" }
                      }
                    }
                  }
                }
                """.formatted(autoDisplayName(tagpath));

        // Write file
        try (FileWriter writer = new FileWriter(targetFile)) {
            writer.write(template);
        } catch (IOException e) {
            source.sendFailure(Component.literal("Failed to write file: " + e.getMessage()));
            return 0;
        }

        // Success feedback
        String scope = isUniversal ? "universal"
                : isAllRoles ? "all_roles / Level " + Level
                : role + " / Level " + Level;
        String relativePath = SET_ARMOR_DIR.toPath().relativize(targetFile.toPath()).toString();

        source.sendSystemMessage(Component.literal(""));
        source.sendSystemMessage(Component.literal("[SetWeaver] ✔ Template created!")
                .withStyle(ChatFormatting.GREEN));
        source.sendSystemMessage(Component.literal("  Tag:   ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(tagString).withStyle(ChatFormatting.GOLD)));
        source.sendSystemMessage(Component.literal("  Scope: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(scope).withStyle(ChatFormatting.WHITE)));
        source.sendSystemMessage(Component.literal("  File:  ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(relativePath).withStyle(ChatFormatting.WHITE)));

        if (tagExists) {
            // Show how many items are in this tag
            long itemCount = BuiltInRegistries.ITEM.holders()
                    .filter(holder -> holder.is(tagKey))
                    .count();
            source.sendSystemMessage(Component.literal("  Items in tag: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.valueOf(itemCount))
                            .withStyle(ChatFormatting.WHITE)));
        }

        source.sendSystemMessage(Component.literal(""));
        source.sendSystemMessage(Component.literal("  Edit the file, then run:")
                .withStyle(ChatFormatting.GRAY));

        // Clickable reload command
        source.sendSystemMessage(Component.literal("  ")
                .append(Component.literal("/setweaver debug reload")
                        .withStyle(ChatFormatting.AQUA)
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        "/setweaver debug reload"))
                                .withHoverEvent(new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("Click to reload"))))));

        return 1;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    /** Sort order for scope: universal (0), all_roles (1), specific (2). */
    private static int scopeOrder(ArmorSetDataRegistry.SetEntry entry) {
        if (ArmorSetDataRegistry.WILDCARD_ROLE.equals(entry.role())
                && entry.level() == ArmorSetDataRegistry.WILDCARD_LEVEL) return 0;
        if (ArmorSetDataRegistry.WILDCARD_ROLE.equals(entry.role())) return 1;
        return 2;
    }

    public static final SuggestionProvider<CommandSourceStack> TAG_ITEMS_PATH_SUGGESTIONS = (ctx, builder) -> {
        String namespace = StringArgumentType.getString(ctx, "tag_namespace");
        List<String> paths = BuiltInRegistries.ITEM.getTagNames()
                .filter(tagKey -> tagKey.location().getNamespace().equals(namespace))
                .map(tagKey -> tagKey.location().getPath())
                .sorted()
                .toList();
        return SharedSuggestionProvider.suggest(paths, builder);
    };


    /** Human-readable scope label. */
    private static String formatScope(ArmorSetDataRegistry.SetEntry entry) {
        if (ArmorSetDataRegistry.WILDCARD_ROLE.equals(entry.role())
                && entry.level() == ArmorSetDataRegistry.WILDCARD_LEVEL) {
            return "ALL roles / ALL Levels (universal)";
        } else if (ArmorSetDataRegistry.WILDCARD_ROLE.equals(entry.role())) {
            return "ALL roles / Level " + entry.level();
        }
        return entry.role() + " / Level " + entry.level();
    }

    /** Scope color for chat display. */
    private static ChatFormatting scopeColor(ArmorSetDataRegistry.SetEntry entry) {
        if (ArmorSetDataRegistry.WILDCARD_ROLE.equals(entry.role())
                && entry.level() == ArmorSetDataRegistry.WILDCARD_LEVEL) {
            return ChatFormatting.LIGHT_PURPLE;
        } else if (ArmorSetDataRegistry.WILDCARD_ROLE.equals(entry.role())) {
            return ChatFormatting.BLUE;
        }
        return ChatFormatting.GREEN;
    }

    /**
     * Builds the /setweaver sets info command for a tag string.
     * Splits "namespace:tagpath" into separate arguments.
     */
    private static String buildInfoCommand(String tagString) {
        String[] parts = tagString.split(":", 2);
        if (parts.length == 2) {
            return "/setweaver sets info " + parts[0] + " " + parts[1];
        }
        return "/setweaver sets info setweaver " + tagString;
    }

    /**
     * Generates a human-readable display name from a tag path.
     * Example: "magiccloth_armor" → "Magiccloth Armor"
     */
    private static String autoDisplayName(String tagpath) {
        String[] words = tagpath.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) sb.append(w.substring(1));
            }
        }
        return sb.toString();
    }

    /** Extracts the number from "2Part" → 2, "4Part" → 4. Returns 0 if invalid. */
    private static int extractPartNumber(String partKey) {
        StringBuilder digits = new StringBuilder();
        for (char c : partKey.toCharArray()) {
            if (Character.isDigit(c)) digits.append(c);
            else break;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Formats an attribute value for display. */
    private static String formatAttributeValue(double value, String modifier) {
        if ("multiply_base".equals(modifier) || "multiply_total".equals(modifier)) {
            return String.format("%+.0f%%", value * 100);
        }
        return String.format("%+.1f", value);
    }

    /** Simple int → Roman numeral (I-V). */
    private static String toRoman(int num) {
        return switch (num) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(num);
        };
    }
}