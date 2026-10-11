package com.gilfort.setweaver.util;

import com.gilfort.setweaver.SetWeaver;
import net.minecraft.nbt.CompoundTag;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Player data attachment holding role and level (see {@link PlayerDataHelper}). Persistent: stored in player saves.
 */
public final class SetWeaverPlayerData {

    /** Attachment type register. */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SetWeaver.MODID);

    /** Role and level of a player; copied on death so respawning keeps them. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CompoundTag>> PLAYER_DATA =
            ATTACHMENT_TYPES.register("player_data", () -> AttachmentType.builder((Supplier<CompoundTag>) CompoundTag::new)
                    .serialize(CompoundTag.CODEC)
                    .copyOnDeath()
                    .build());

    private SetWeaverPlayerData() {
    }
}
