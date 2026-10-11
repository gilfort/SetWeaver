package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: check all set files and answer with a {@link ValidationResultsPayload}. Requires operator
 * level 2.
 */
public record ValidateRequestPayload() implements CustomPacketPayload {

    /** Payload type. */
    public static final Type<ValidateRequestPayload> TYPE = new Type<>(SetWeaver.id("validate_request"));

    /** Network codec. */
    public static final StreamCodec<ByteBuf, ValidateRequestPayload> STREAM_CODEC = StreamCodec.unit(new ValidateRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
