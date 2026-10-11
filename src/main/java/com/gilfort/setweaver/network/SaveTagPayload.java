package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Client → Server payload that requests creating/updating a custom item tag
 * in the SetWeaver datapack within the world save.
 *
 * @param namespace  the tag namespace (e.g. "setweaver" or "c")
 * @param tagName    the tag path (e.g. "chainmail_and_iron")
 * @param itemsJson  JSON array of item IDs, e.g. ["minecraft:iron_helmet","minecraft:chainmail_helmet"]
 */
public record SaveTagPayload(String namespace, String tagName, String itemsJson) implements CustomPacketPayload {

    public static final Type<SaveTagPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SetWeaver.MODID, "save_tag"));

    public static final StreamCodec<ByteBuf, SaveTagPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SaveTagPayload::namespace,
                    ByteBufCodecs.STRING_UTF8, SaveTagPayload::tagName,
                    ByteBufCodecs.STRING_UTF8, SaveTagPayload::itemsJson,
                    SaveTagPayload::new
            );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
