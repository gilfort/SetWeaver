package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import com.gilfort.setweaver.seteffects.ValidationResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Server → client: results of checking the set files on the server.
 *
 * @param results one result per file
 */
public record ValidationResultsPayload(List<ValidationResult> results) implements CustomPacketPayload {

    /** Max number of results sent. */
    public static final int MAX_RESULTS = 4096;

    /** Payload type. */
    public static final Type<ValidationResultsPayload> TYPE = new Type<>(SetWeaver.id("validation_results"));

    /** Network codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ValidationResultsPayload> STREAM_CODEC = StreamCodec.composite(
            ValidationResult.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_RESULTS)), ValidationResultsPayload::results,
            ValidationResultsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
