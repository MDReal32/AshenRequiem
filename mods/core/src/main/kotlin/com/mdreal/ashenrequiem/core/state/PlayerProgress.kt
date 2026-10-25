package com.mdreal.ashenrequiem.core.state

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Per-player story state. Stored as a data attachment that is copied on death,
 * so nothing in here can ever be lost.
 */
data class PlayerProgress(
    val awakened: Boolean = false,
    val requiem: Boolean = false,
    val chapter: Int = 0,
    /** Which of the six levels (STORY.md §9) the player is in. The quest that ends a level raises it. */
    val level: Int = 1,
    val seenCinematics: Set<String> = emptySet(),
) {
    companion object {
        val CODEC: Codec<PlayerProgress> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.BOOL.optionalFieldOf("awakened", false).forGetter(PlayerProgress::awakened),
                Codec.BOOL.optionalFieldOf("requiem", false).forGetter(PlayerProgress::requiem),
                Codec.INT.optionalFieldOf("chapter", 0).forGetter(PlayerProgress::chapter),
                Codec.INT.optionalFieldOf("level", 1).forGetter(PlayerProgress::level),
                Codec.STRING.listOf().xmap({ it.toSet() }, { it.toList() })
                    .optionalFieldOf("seen_cinematics", emptySet()).forGetter(PlayerProgress::seenCinematics),
            ).apply(i) { awakened, requiem, chapter, level, seen -> PlayerProgress(awakened, requiem, chapter, level, seen) }
        }
    }
}
