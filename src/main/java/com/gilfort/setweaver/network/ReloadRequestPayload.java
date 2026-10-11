package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Empty payload sent from client → server to request a reload
 * of all set definitions and attribute packages.
 * The server reloads from disk and broadcasts the updated registry
 * to all connected clients.
 */
public record ReloadRequestPayload() implements CustomPacketPayload {

    public static final Type<ReloadRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SetWeaver.MODID, "reload_request"));

    public static final StreamCodec<ByteBuf, ReloadRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new ReloadRequestPayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
