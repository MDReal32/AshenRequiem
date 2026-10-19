package com.mdreal.ashenrequiem.core.state

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Per-player story state. Stored as a data attachment that is copied on death,
 * so nothing in here can ever be lost.
 */
data class PlayerProgress(
    val awakened: Boolean = false,
    val requiem: Boolean = false,
    val chapter: Int = 0,
    val seenCinematics: Set<String> = emptySet(),
) {
    companion object {
        val MAP_CODEC: MapCodec<PlayerProgress> = RecordCodecBuilder.mapCodec { i ->
            i.group(
                Codec.BOOL.optionalFieldOf("awakened", false).forGetter(PlayerProgress::awakened),
                Codec.BOOL.optionalFieldOf("requiem", false).forGetter(PlayerProgress::requiem),
                Codec.INT.optionalFieldOf("chapter", 0).forGetter(PlayerProgress::chapter),
                Codec.STRING.listOf().xmap({ it.toSet() }, { it.toList() })
                    .optionalFieldOf("seen_cinematics", emptySet()).forGetter(PlayerProgress::seenCinematics),
            ).apply(i) { awakened, requiem, chapter, seen -> PlayerProgress(awakened, requiem, chapter, seen) }
        }
        val CODEC: Codec<PlayerProgress> = MAP_CODEC.codec()
    }
}
