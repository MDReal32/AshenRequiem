package com.mdreal.cinematica.server

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * Where a player was and what game mode they had before a scene or recording turned them into a spectator. Saved with
 * the player, so a crash or disconnect mid-scene still gives them back on the next login.
 */
data class RestoreState(
    val gameMode: GameType,
    val dimension: ResourceKey<Level>,
    val pos: Vec3,
    val yaw: Float,
    val pitch: Float,
    /** Spectators always fly, and going back to creative keeps that, so whether they were flying is restored too. */
    val flying: Boolean = false,
) {
    companion object {
        val CODEC: Codec<RestoreState> = RecordCodecBuilder.create { i ->
            i.group(
                GameType.CODEC.fieldOf("game_mode").forGetter(RestoreState::gameMode),
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(RestoreState::dimension),
                Vec3.CODEC.fieldOf("pos").forGetter(RestoreState::pos),
                Codec.FLOAT.fieldOf("yaw").forGetter(RestoreState::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(RestoreState::pitch),
                Codec.BOOL.optionalFieldOf("flying", false).forGetter(RestoreState::flying),
            ).apply(i, ::RestoreState)
        }

        /** Never read: the attachment is only looked at when present. */
        val NONE = RestoreState(GameType.SURVIVAL, Level.OVERWORLD, Vec3.ZERO, 0f, 0f)

        fun of(player: ServerPlayer) = RestoreState(
            player.gameMode.gameModeForPlayer,
            player.level().dimension(),
            player.position(),
            player.yRot,
            player.xRot,
            player.abilities.flying
        )
    }
}
