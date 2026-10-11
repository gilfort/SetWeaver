package com.gilfort.setweaver.seteffects;

import com.gilfort.setweaver.SetWeaver;
import com.gilfort.setweaver.util.PlayerDataHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Applies the set bonuses on the server: every {@link #TICK_INTERVAL} ticks each player's worn pieces are counted
 * per set tag and the effects and attribute modifiers of the reached threshold are applied. Several sets stack.
 *
 * <p>Attribute modifiers are only touched when they change. Removing and re-adding a max health modifier would
 * otherwise cut the player's health down to the base maximum on every update.</p>
 */
public final class ArmorEffects {

    /** Interval in ticks between set bonus updates (3 seconds). */
    public static final int TICK_INTERVAL = 60;

    /** Duration of applied effects; longer than {@link #TICK_INTERVAL} so they never run out while worn. */
    private static final int EFFECT_DURATION = 200;

    /** Attributes that currently carry modifiers of this mod, per player. Server thread only. */
    private static final Map<UUID, Set<Holder<Attribute>>> MODIFIED_ATTRIBUTES = new HashMap<>();

    private ArmorEffects() {
    }

    /**
     * Registers the tick and logout listeners.
     *
     * @param modEventBus the mod event bus (unused, kept for a uniform registration call)
     */
    public static void register(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(ArmorEffects::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ArmorEffects::onPlayerLogout);
    }

    private static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        MODIFIED_ATTRIBUTES.remove(event.getEntity().getUUID());
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Spread players over the interval instead of updating everyone in the same tick
        if ((player.tickCount + player.getId()) % TICK_INTERVAL != 0) {
            return;
        }
        update(player);
    }

    /**
     * Recalculates and applies all set bonuses of a player.
     *
     * @param player the player
     */
    public static void update(ServerPlayer player) {
        Map<Holder<Attribute>, Map<ResourceLocation, AttributeModifier>> wanted = new HashMap<>();

        if (ArmorPieces.wearsAny(player)) {
            String role = PlayerDataHelper.getRole(player);
            int level = PlayerDataHelper.getLevel(player);
            for (ArmorSetDataRegistry.SetEntry entry : ArmorSetDataRegistry.server().getApplicableEntries(role, level)) {
                int worn = ArmorPieces.countWorn(player, entry.tagKey());
                if (worn == 0) {
                    continue;
                }
                ArmorSetData.PartData part = entry.data().getActivePartData(worn);
                if (part != null) {
                    applyEffects(player, part);
                    collectModifiers(part, entry.tag(), wanted);
                }
            }
        }
        syncModifiers(player, wanted);
    }

    private static void applyEffects(ServerPlayer player, ArmorSetData.PartData part) {
        if (part.getEffects() == null) {
            return;
        }
        for (ArmorSetData.EffectData effect : part.getEffects()) {
            SetIds.effect(effect.getEffect()).ifPresent(holder -> player.addEffect(
                    new MobEffectInstance(holder, EFFECT_DURATION, effect.getAmplifier(), false, false, true)));
        }
    }

    private static void collectModifiers(ArmorSetData.PartData part, String tag,
                                         Map<Holder<Attribute>, Map<ResourceLocation, AttributeModifier>> wanted) {
        if (part.getAttributes() == null) {
            return;
        }
        List<ArmorSetData.AttributeData> attributes = part.getAttributes();
        for (int i = 0; i < attributes.size(); i++) {
            ArmorSetData.AttributeData data = attributes.get(i);
            var holder = SetIds.attribute(data.getAttribute());
            var operation = SetIds.operation(data.getModifier());
            if (holder.isEmpty() || operation.isEmpty()) {
                continue;
            }
            // The index keeps ids unique when the same attribute appears twice in one set
            ResourceLocation id = modifierId(holder.get().key().location().getPath(),
                    data.getModifier().toLowerCase(Locale.ROOT) + "_" + i, tag);
            wanted.computeIfAbsent(holder.get(), h -> new HashMap<>())
                    .put(id, new AttributeModifier(id, data.getValue(), operation.get()));
        }
    }

    /** Brings the player's modifiers of this mod in line with {@code wanted}, touching only what changed. */
    private static void syncModifiers(ServerPlayer player,
                                      Map<Holder<Attribute>, Map<ResourceLocation, AttributeModifier>> wanted) {
        Set<Holder<Attribute>> touched = new HashSet<>(wanted.keySet());
        touched.addAll(MODIFIED_ATTRIBUTES.getOrDefault(player.getUUID(), Set.of()));

        for (Holder<Attribute> attribute : touched) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            Map<ResourceLocation, AttributeModifier> target = wanted.getOrDefault(attribute, Map.of());
            for (AttributeModifier existing : new ArrayList<>(instance.getModifiers())) {
                if (SetWeaver.MODID.equals(existing.id().getNamespace()) && !target.containsKey(existing.id())) {
                    instance.removeModifier(existing.id());
                }
            }
            for (AttributeModifier modifier : target.values()) {
                AttributeModifier existing = instance.getModifier(modifier.id());
                if (existing == null) {
                    instance.addTransientModifier(modifier);
                } else if (existing.amount() != modifier.amount() || existing.operation() != modifier.operation()) {
                    instance.removeModifier(modifier.id());
                    instance.addTransientModifier(modifier);
                }
            }
        }

        if (wanted.isEmpty()) {
            MODIFIED_ATTRIBUTES.remove(player.getUUID());
        } else {
            MODIFIED_ATTRIBUTES.put(player.getUUID(), new HashSet<>(wanted.keySet()));
        }
    }

    /**
     * Builds the id of a set modifier.
     *
     * @param attributePath path of the attribute id
     * @param operation     operation name plus index
     * @param setTag        the set tag
     * @return a modifier id in this mod's namespace
     */
    public static ResourceLocation modifierId(String attributePath, String operation, String setTag) {
        return SetWeaver.id(sanitize(attributePath + "_" + operation + "_" + setTag));
    }

    private static String sanitize(String input) {
        return input.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_/.-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }
}
