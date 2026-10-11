package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: replace the attribute packages. Requires operator level 2.
 *
 * @param packagesJson JSON list of packages (see {@code AttributePackageManager#parseList})
 */
public record SavePackagesPayload(String packagesJson) implements CustomPacketPayload {

    /** Payload type. */
    public static final Type<SavePackagesPayload> TYPE = new Type<>(SetWeaver.id("save_packages"));

    /** Network codec. */
    public static final StreamCodec<ByteBuf, SavePackagesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(262_144), SavePackagesPayload::packagesJson,
            SavePackagesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
