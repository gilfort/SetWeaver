package com.gilfort.zauberei.network;

import com.gilfort.zauberei.Zauberei;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.stream.Stream;

public record LetterButtonPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LetterButtonPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Zauberei.MODID, "letterbuttonpayload"));

    public static final StreamCodec<ByteBuf, LetterButtonPayload> STREAM_CODEC = StreamCodec.unit(new LetterButtonPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
