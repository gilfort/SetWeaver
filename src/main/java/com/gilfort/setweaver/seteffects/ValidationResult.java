package com.gilfort.setweaver.seteffects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Result of checking one set file.
 *
 * @param filePath path relative to the set folder
 * @param status   overall result
 * @param messages problems found (translatable), empty when valid
 */
public record ValidationResult(String filePath, Status status, List<Component> messages) {

    /** Overall result of a file check. */
    public enum Status {
        /** Loaded without changes. */
        OK,
        /** Loaded, but invalid entries were skipped or corrected. */
        WARNING,
        /** Not loaded. */
        ERROR
    }

    /** Network codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ValidationResult> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ValidationResult::filePath,
            ByteBufCodecs.VAR_INT.map(i -> Status.values()[Math.floorMod(i, Status.values().length)], Status::ordinal),
            ValidationResult::status,
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(64)), ValidationResult::messages,
            ValidationResult::new);
}
