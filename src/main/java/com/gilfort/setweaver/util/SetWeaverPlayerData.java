package com.gilfort.setweaver.util;

import java.util.function.Supplier;

import com.gilfort.setweaver.SetWeaver;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class SetWeaverPlayerData {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SetWeaver.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CompoundTag>> PLAYER_DATA =
            ATTACHMENT_TYPES.register("player_data", () ->
                    AttachmentType.<CompoundTag>builder((Supplier<CompoundTag>) CompoundTag::new)
                            .serialize(CompoundTag.CODEC)
                            .copyOnDeath()   // Automatisches Kopieren beim Respawn
                            .build()
            );



}
