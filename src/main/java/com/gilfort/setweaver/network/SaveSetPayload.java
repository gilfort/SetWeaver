package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: write a set definition. Requires operator level 2; the server validates path and content.
 *
 * @param relativePath path inside {@code config/setweaver/set_armor/}, e.g. {@code naturalist/3/c__armors.json}
 * @param jsonContent  the set definition as JSON
 */
public record SaveSetPayload(String relativePath, String jsonContent) implements CustomPacketPayload {

    /** Max JSON length of one set file. */
    public static final int MAX_JSON_LENGTH = 262_144;

    /** Payload type. */
    public static final Type<SaveSetPayload> TYPE = new Type<>(SetWeaver.id("save_set"));

    /** Network codec. */
    public static final StreamCodec<ByteBuf, SaveSetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(512), SaveSetPayload::relativePath,
            ByteBufCodecs.stringUtf8(MAX_JSON_LENGTH), SaveSetPayload::jsonContent,
            SaveSetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
