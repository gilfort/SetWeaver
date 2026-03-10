package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LetterButtonPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LetterButtonPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SetWeaver.MODID, "letterbuttonpayload"));

    public static final StreamCodec<ByteBuf, LetterButtonPayload> STREAM_CODEC = StreamCodec.unit(new LetterButtonPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
