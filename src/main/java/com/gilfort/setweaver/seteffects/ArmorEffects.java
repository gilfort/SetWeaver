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

import java.util.*;

import static com.gilfort.setweaver.component.ComponentRegistry.MAJOR;
import static com.gilfort.setweaver.component.ComponentRegistry.YEAR;

public class ArmorEffects {

    public static void register(IEventBus eventBus) {
        NeoForge.EVENT_BUS.addListener(ArmorEffects::onPlayerTick);
    }

    private static int tickcounter = 0;

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {

            tickcounter++;

            if (tickcounter < 60) {
                return;
            }

            tickcounter = 0;

            String major = PlayerDataHelper.getMajor(player);
            int year = PlayerDataHelper.getYear(player);

            applySetBasedEffects(player, major, year);

            // keep components updated on armor stacks (used by tooltip)
            for (ItemStack stack : player.getArmorSlots()) {
                if (stack.getItem() instanceof ArmorItem) {
                    stack.set(MAJOR.value(), major);
                    stack.set(YEAR.value(), year);
                }
            }
        }
    }

    /**
     * Tag-only set logic:
     * - Read registered tags for (major, year)
     * - Count how many worn armor pieces match each tag
     * - Remove old Zauberei modifiers ONCE
     * - Apply effects + attributes for each matching set
     */
    private static void applySetBasedEffects(Player player, String major, int year) {
        // Early skip: no armor worn at all
        boolean anyArmor = false;
        for (ItemStack stack : player.getArmorSlots()) {
            if (!stack.isEmpty() && stack.getItem() instanceof ArmorItem) {
                anyArmor = true;
                break;
            }
        }
        if (!anyArmor) {
            removeOldZaubereiModifiers(player);
            return;
        }

        Set<String> registeredTags = ArmorSetDataRegistry.getRegisteredTags(major.toLowerCase(), year);
        if (registeredTags.isEmpty()) {
            removeOldZaubereiModifiers(player);
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
            removeOldZaubereiModifiers(player);
            return;
        }

        // Remove old modifiers ONCE, then apply all sets (stacking allowed)
        removeOldZaubereiModifiers(player);

        for (Map.Entry<String, Integer> entry : tagCounts.entrySet()) {
            String tagString = entry.getKey();
            int count = entry.getValue();

            ArmorSetData data = ArmorSetDataRegistry.getData(major.toLowerCase(), year, tagString);
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

    private static void removeOldZaubereiModifiers(Player player) {
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
