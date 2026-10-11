package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: the receiving player's role and level. Sent on login, after {@code /reload} and when a command
 * changes them.
 *
 * @param role  the role, empty if none
 * @param level the level, 0 if none
 */
public record PlayerDataPayload(String role, int level) implements CustomPacketPayload {

    /** Payload type. */
    public static final Type<PlayerDataPayload> TYPE = new Type<>(SetWeaver.id("player_data"));

    /** Network codec. */
    public static final StreamCodec<ByteBuf, PlayerDataPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PlayerDataPayload::role,
            ByteBufCodecs.VAR_INT, PlayerDataPayload::level,
            PlayerDataPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
