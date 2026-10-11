package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: all set definitions and attribute packages as JSON. Sent on login and after every reload.
 *
 * @param registryJson set definitions (see {@code ArmorSetDataRegistry#serializeToJson})
 * @param packagesJson attribute packages (see {@code AttributePackageManager#serializeToJson})
 */
public record RegistrySyncPayload(String registryJson, String packagesJson) implements CustomPacketPayload {

    /** Max length per JSON string; large modpacks exceed the default limit of 32767. */
    private static final int MAX_STRING_LENGTH = 2_097_152;

    /** Payload type. */
    public static final Type<RegistrySyncPayload> TYPE = new Type<>(SetWeaver.id("registry_sync"));

    /** Network codec. */
    public static final StreamCodec<ByteBuf, RegistrySyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH), RegistrySyncPayload::registryJson,
            ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH), RegistrySyncPayload::packagesJson,
            RegistrySyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
