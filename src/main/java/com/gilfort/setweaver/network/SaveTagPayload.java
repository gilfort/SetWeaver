package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Client → server: create or update an item tag in the world datapack {@code setweaver_custom}. Requires operator
 * level 2; the server validates the tag id and the item ids.
 *
 * @param namespace tag namespace, e.g. {@code setweaver}
 * @param tagName   tag path, e.g. {@code chainmail_and_iron}
 * @param items     item ids in the tag
 */
public record SaveTagPayload(String namespace, String tagName, List<String> items) implements CustomPacketPayload {

    /** Max number of items per tag. */
    public static final int MAX_ITEMS = 1024;

    /** Payload type. */
    public static final Type<SaveTagPayload> TYPE = new Type<>(SetWeaver.id("save_tag"));

    /** Network codec. */
    public static final StreamCodec<ByteBuf, SaveTagPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), SaveTagPayload::namespace,
            ByteBufCodecs.stringUtf8(256), SaveTagPayload::tagName,
            ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(MAX_ITEMS)), SaveTagPayload::items,
            SaveTagPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
