package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: open the Sets Manager screen.
 */
public record OpenSetsGuiPayload() implements CustomPacketPayload {

    /** Payload type. */
    public static final Type<OpenSetsGuiPayload> TYPE = new Type<>(SetWeaver.id("open_sets_gui"));

    /** Network codec. */
    public static final StreamCodec<ByteBuf, OpenSetsGuiPayload> STREAM_CODEC = StreamCodec.unit(new OpenSetsGuiPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
