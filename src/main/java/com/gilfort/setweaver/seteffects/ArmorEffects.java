package com.gilfort.setweaver.seteffects;

import com.gilfort.setweaver.SetWeaver;
import com.gilfort.setweaver.datagen.PlayerDataHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.gilfort.setweaver.network.PlayerDataPayload;

import java.util.*;

public class ArmorEffects {

    /** Interval in ticks between set-effect recalculations (60 ticks = 3 seconds). */
    private static final int TICK_INTERVAL = 60;

    /** Per-player cooldown tracker. Stores the last server tick when effects were applied. */
    private static final Map<UUID, Long> lastAppliedTick = new HashMap<>();

    public static void register(IEventBus eventBus) {
        NeoForge.EVENT_BUS.addListener(ArmorEffects::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ArmorEffects::onPlayerLogout);
    }

    /** Clean up per-player tracking data on logout to prevent memory leaks. */
    public static void onPlayerLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        lastAppliedTick.remove(event.getEntity().getUUID());
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {

            long currentTick = player.getServer().getTickCount();
            long lastTick = lastAppliedTick.getOrDefault(player.getUUID(), 0L);

            if (currentTick - lastTick < TICK_INTERVAL) {
                return;
            }

            lastAppliedTick.put(player.getUUID(), currentTick);

            String role = PlayerDataHelper.getRole(player);
            int level = PlayerDataHelper.getLevel(player);

            applySetBasedEffects(player, role, level);

            // Sync role/year to client cache (ensures tooltip works on dedicated servers)
            PacketDistributor.sendToPlayer(player, new PlayerDataPayload(role, level));
        }
    }

    /**
     * Tag-only set logic:
     * - Read registered tags for (role, level)
     * - Count how many worn armor pieces match each tag
     * - Remove old Zauberei modifiers ONCE
     * - Apply effects + attributes for each matching set
     */
    private static void applySetBasedEffects(Player player, String role, int level) {
        // Early skip: no armor worn at all
        boolean anyArmor = false;
        for (ItemStack stack : player.getArmorSlots()) {
            if (!stack.isEmpty() && stack.getItem() instanceof ArmorItem) {
                anyArmor = true;
                break;
            }
        }
        if (!anyArmor) {
            removeOldSetModifiers(player);
            return;
        }

        Set<String> registeredTags = ArmorSetDataRegistry.getRegisteredTags(role.toLowerCase(), level);
        if (registeredTags.isEmpty()) {
            removeOldSetModifiers(player);
            return;
        }

        Map<String, Integer> tagCounts = new HashMap<>();

        // Count worn pieces per tag
        for (String tagString : registeredTags) {
            ResourceLocation tagLoc;
            try {
                tagLoc = ResourceLocation.parse(tagString);
            } catch (Exception e) {
                // Should not happen if validated in reload listener
                continue;
            }

            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagLoc);

            int count = 0;
            for (ItemStack stack : player.getArmorSlots()) {
                if (!stack.isEmpty() && stack.is(tagKey)) {
                    count++;
                }
            }

            if (count > 0) {
                tagCounts.put(tagString, count);
            }
        }

        if (tagCounts.isEmpty()) {
            removeOldSetModifiers(player);
            return;
        }

        // Remove old modifiers ONCE, then apply all sets (stacking allowed)
        removeOldSetModifiers(player);

        for (Map.Entry<String, Integer> entry : tagCounts.entrySet()) {
            String tagString = entry.getKey();
            int count = entry.getValue();

            ArmorSetData data = ArmorSetDataRegistry.getData(role.toLowerCase(), level, tagString);
            if (data == null || data.getParts() == null) {
                continue;
            }

            ArmorSetData.PartData partData = data.getActivePartData(count);
            if (partData == null) {
                // Player hasn't reached the first threshold yet
                continue;
            }


            applySetEffects(player, partData);
            applyAttributes(player, partData, tagString);
        }
    }

    private static void applyAttributes(Player player, ArmorSetData.PartData partData, String tagString) {
        if (partData.getAttributes() == null) {
            return;
        }

        for (Map.Entry<String, ArmorSetData.AttributeData> entry : partData.getAttributes().entrySet()) {
            String attributeName = entry.getKey();
            ArmorSetData.AttributeData value = entry.getValue();

            Holder.Reference<Attribute> attributeHolder = getAttributeHolder(attributeName);
            if (attributeHolder == null) {
                continue;
            }

            AttributeModifier.Operation operation;
            switch (value.getModifier().toLowerCase()) {
                case "addition":
                    operation = AttributeModifier.Operation.ADD_VALUE;
                    break;
                case "multiply":
                case "multiply_base":
                    operation = AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                    break;
                case "multiply_total":
                    operation = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                    break;
                default:
                    // invalid modifier type -> skip
                    continue;
            }

            AttributeInstance attributeInstance = player.getAttribute(attributeHolder);
            if (attributeInstance == null) {
                continue;
            }

            ResourceLocation modifierId = makeModifierId(attributeName, value.getModifier().toLowerCase(), tagString);
            if (modifierId == null) {
                continue;
            }

            AttributeModifier modifier = new AttributeModifier(
                    modifierId,
                    value.getValue(),
                    operation
            );

            AttributeModifier existing = attributeInstance.getModifier(modifier.id());
            if (existing == null) {
                attributeInstance.addTransientModifier(modifier);
            } else if (existing.amount() != modifier.amount()
                    || existing.operation() != modifier.operation()) {
                attributeInstance.removeModifier(modifier.id());
                attributeInstance.addTransientModifier(modifier);
            }
        }
    }

    private static void removeOldSetModifiers(Player player) {
        for (Attribute attribute : BuiltInRegistries.ATTRIBUTE) {
            Optional<ResourceKey<Attribute>> optionalKey = BuiltInRegistries.ATTRIBUTE.getResourceKey(attribute);
            if (optionalKey.isEmpty()) {
                continue;
            }

            Holder.Reference<Attribute> attributeHolder = BuiltInRegistries.ATTRIBUTE.getHolderOrThrow(optionalKey.get());
            AttributeInstance attributeInstance = player.getAttribute(attributeHolder);
            if (attributeInstance == null) {
                continue;
            }

            // Copy to avoid concurrent modification
            List<AttributeModifier> modifiers = new ArrayList<>(attributeInstance.getModifiers());
            for (AttributeModifier modifier : modifiers) {
                ResourceLocation id = modifier.id();
                if (id != null && SetWeaver.MODID.equals(id.getNamespace())) {
                    attributeInstance.removeModifier(modifier);
                }
            }
        }
    }

    private static Holder.Reference<Attribute> getAttributeHolder(String attributeName) {
        ResourceLocation loc = tryMakeResourceLocation(attributeName);
        if (loc == null) return null;

        Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(loc);
        if (attribute == null) return null;

        Optional<ResourceKey<Attribute>> optionalKey = BuiltInRegistries.ATTRIBUTE.getResourceKey(attribute);
        return optionalKey.map(BuiltInRegistries.ATTRIBUTE::getHolderOrThrow).orElse(null);

    }

    public static ResourceLocation makeModifierId(String attributeName, String operation, String setScope) {
        int index = attributeName.indexOf(":");
        if (index == -1) {
            return null;
        }
        String attrPath = attributeName.substring(index + 1);
        String sanitizedScope = sanitizeForResourceLocation(setScope);
        String path = attrPath + "_" + operation + "_" + sanitizedScope;
        return ResourceLocation.fromNamespaceAndPath(SetWeaver.MODID, path);
    }

    private static String sanitizeForResourceLocation(String input) {
        if (input == null) return "unknown";
        return input.toLowerCase()
                .replaceAll("[^a-z0-9_/.-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }


    private static void applySetEffects(Player player, ArmorSetData.PartData partData) {
        if (partData.getEffects() == null) {
            return;
        }

        for (ArmorSetData.EffectData effectData : partData.getEffects()) {
            ResourceLocation effectLoc = tryMakeResourceLocation(effectData.getEffect());
            if (effectLoc == null) {
                continue;
            }

            MobEffect mobEffect = BuiltInRegistries.MOB_EFFECT.get(effectLoc);
            if (mobEffect == null) {
                continue;
            }

            int duration = 200; // 10 seconds
            int amplifier = effectData.getAmplifier();

            Optional<ResourceKey<MobEffect>> optionalKey = BuiltInRegistries.MOB_EFFECT.getResourceKey(mobEffect);
            if (optionalKey.isEmpty()) {
                continue;
            }
            Holder.Reference<MobEffect> effectHolder = BuiltInRegistries.MOB_EFFECT.getHolderOrThrow(optionalKey.get());

            player.addEffect(new MobEffectInstance(effectHolder, duration, amplifier, false, false, true));
        }
    }

    private static ResourceLocation tryMakeResourceLocation(String name) {
        if (name == null) return null;
        String s = name.contains(":") ? name : "minecraft:" + name;
        try {
            return ResourceLocation.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
