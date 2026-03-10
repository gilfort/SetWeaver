package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Empty payload sent from server → client to trigger opening
 * the SetsManagerScreen GUI.
 */
public record OpenSetsGuiPayload() implements CustomPacketPayload {

    public static final Type<OpenSetsGuiPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SetWeaver.MODID, "open_sets_gui"));

    public static final StreamCodec<ByteBuf, OpenSetsGuiPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenSetsGuiPayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
