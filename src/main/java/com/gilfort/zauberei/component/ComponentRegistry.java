package com.gilfort.zauberei.component;

import com.gilfort.zauberei.Zauberei;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

public class ComponentRegistry {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Zauberei.MODID);


    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(String pName, UnaryOperator<DataComponentType.Builder<T>> pBuilder) {
        return COMPONENTS.register(pName, () -> pBuilder.apply(DataComponentType.builder()).build());
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> CLOTHING_ALT =
            register("transmog",
                    (builder) -> builder
                            .persistent(Unit.CODEC)
                            .networkSynchronized(StreamCodec.unit(Unit.INSTANCE)).cacheEncoding());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> MAJOR =
            register("major",
                    b -> b.persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                            .cacheEncoding()
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> YEAR =
            register("year",
                    b -> b.persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.VAR_INT)
                            .cacheEncoding()
            );
}