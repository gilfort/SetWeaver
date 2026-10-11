package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Server → Client payload that syncs the entire ArmorSetDataRegistry
 * and the attribute package configuration.
 * Both are serialized as JSON strings for simplicity and forward compatibility.
 * Sent on player login and after any server-side reload of set definitions.
 *
 * <p>Uses a custom codec with a 2 MB limit per string to support large modpacks
 * (the default STRING_UTF8 limit of 32767 bytes is too small).</p>
 */
public record RegistrySyncPayload(String registryJson, String packagesJson) implements CustomPacketPayload {

    /** Max bytes per JSON string field (2 MB). */
    private static final int MAX_STRING_LENGTH = 2_097_152;

    public static final Type<RegistrySyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SetWeaver.MODID, "registry_sync"));

    public static final StreamCodec<ByteBuf, RegistrySyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RegistrySyncPayload decode(ByteBuf buf) {
                    FriendlyByteBuf friendly = new FriendlyByteBuf(buf);
                    String registry = friendly.readUtf(MAX_STRING_LENGTH);
                    String packages = friendly.readUtf(MAX_STRING_LENGTH);
                    return new RegistrySyncPayload(registry, packages);
                }

                @Override
                public void encode(ByteBuf buf, RegistrySyncPayload payload) {
                    FriendlyByteBuf friendly = new FriendlyByteBuf(buf);
                    friendly.writeUtf(payload.registryJson(), MAX_STRING_LENGTH);
                    friendly.writeUtf(payload.packagesJson(), MAX_STRING_LENGTH);
                }
            };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
