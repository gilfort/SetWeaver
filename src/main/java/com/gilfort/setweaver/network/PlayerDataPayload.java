package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Server → Client payload that syncs a player's role and level to the client.
 * Sent on login, and whenever role/level changes via commands or the tick event.
 */
public record PlayerDataPayload(String role, int level) implements CustomPacketPayload {

    public static final Type<PlayerDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SetWeaver.MODID, "player_data"));

    public static final StreamCodec<ByteBuf, PlayerDataPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, PlayerDataPayload::role,
                    ByteBufCodecs.VAR_INT, PlayerDataPayload::level,
                    PlayerDataPayload::new
            );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
