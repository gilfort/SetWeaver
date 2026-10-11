package com.gilfort.setweaver.util;

import com.gilfort.setweaver.SetWeaver;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerData>> PLAYER_DATA =
            ATTACHMENT_TYPES.register("player_data", () -> AttachmentType.builder(() -> PlayerData.EMPTY)
                    .serialize(PlayerData.CODEC)
                    .copyOnDeath()
                    .build());

    private SetWeaverPlayerData() {
    }

    /**
     * Role and level of a player. Saved with the field names {@code Role} and {@code Level}, the same layout as the
     * compound tag used by 1.x, so player data of upgraded worlds is kept.
     *
     * @param role  lower-case role, empty if none
     * @param level level, 0 if none
     */
    public record PlayerData(String role, int level) {

        /** No role and no level. */
        public static final PlayerData EMPTY = new PlayerData("", 0);

        /** Save format. */
        public static final MapCodec<PlayerData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("Role", "").forGetter(PlayerData::role),
                Codec.INT.optionalFieldOf("Level", 0).forGetter(PlayerData::level)
        ).apply(instance, PlayerData::new));
    }
}
