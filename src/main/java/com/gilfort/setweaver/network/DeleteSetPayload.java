package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: delete a set definition. Requires operator level 2; the server validates the path.
 *
 * @param relativePath path inside {@code config/setweaver/set_armor/}
 */
public record DeleteSetPayload(String relativePath) implements CustomPacketPayload {

    /** Payload type. */
    public static final Type<DeleteSetPayload> TYPE = new Type<>(SetWeaver.id("delete_set"));

    /** Network codec. */
    public static final StreamCodec<ByteBuf, DeleteSetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(512), DeleteSetPayload::relativePath,
            DeleteSetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
