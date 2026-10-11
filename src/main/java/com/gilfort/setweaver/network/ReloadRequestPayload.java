package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: reload all set definitions and attribute packages from disk and sync them to every player.
 * Requires operator level 2.
 */
public record ReloadRequestPayload() implements CustomPacketPayload {

    /** Payload type. */
    public static final Type<ReloadRequestPayload> TYPE = new Type<>(SetWeaver.id("reload_request"));

    /** Network codec. */
    public static final StreamCodec<ByteBuf, ReloadRequestPayload> STREAM_CODEC = StreamCodec.unit(new ReloadRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
