package com.gilfort.setweaver.component;

import com.gilfort.setweaver.SetWeaver;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Legacy data components inherited from Zauberei. SetWeaver no longer uses them, but they are persistent, so item
 * stacks in existing worlds may still carry them. They stay registered so those stacks keep loading.
 *
 * @deprecated kept only for save compatibility; do not use in new code
 */
@Deprecated
public final class ComponentRegistry {

    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, SetWeaver.MODID);

    /** @deprecated legacy, unused */
    @Deprecated
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> TRANSMOG =
            COMPONENTS.register("transmog", () -> DataComponentType.<Unit>builder()
                    .persistent(Unit.CODEC)
                    .networkSynchronized(StreamCodec.unit(Unit.INSTANCE))
                    .cacheEncoding()
                    .build());

    /** @deprecated legacy, unused */
    @Deprecated
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> ROLE =
            COMPONENTS.register("role", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .cacheEncoding()
                    .build());

    /** @deprecated legacy, unused */
    @Deprecated
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> LEVEL =
            COMPONENTS.register("level", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .cacheEncoding()
                    .build());

    private ComponentRegistry() {
    }

    /**
     * Registers the components.
     *
     * @param eventBus the mod event bus
     */
    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }
}
