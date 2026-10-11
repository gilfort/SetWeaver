package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Client → Server payload that requests saving a set definition JSON file.
 * The relativePath is the path within config/setweaver/set_armor/ (e.g. "naturalist/3/c__armors.json").
 * The jsonContent is the full JSON string to write.
 */
public record SaveSetPayload(String relativePath, String jsonContent) implements CustomPacketPayload {

    public static final Type<SaveSetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SetWeaver.MODID, "save_set"));

    public static final StreamCodec<ByteBuf, SaveSetPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SaveSetPayload::relativePath,
                    ByteBufCodecs.STRING_UTF8, SaveSetPayload::jsonContent,
                    SaveSetPayload::new
            );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
