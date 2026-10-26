package com.mdreal.cinematica.client

import net.neoforged.neoforge.common.ModConfigSpec

/** Client settings, in `config/cinematica-studio-client.toml`. */
object CinematicaClientConfig {
    private val spec = ModConfigSpec.Builder()

    val developerMode: ModConfigSpec.BooleanValue = spec
        .comment(
            "The playback controls while a scene plays: pause, step, snapshot, click to pause and the control bar.",
            "Off for players, so a scene just plays; turn it on to work on scenes.",
        )
        .define("developerMode", false)

    fun getSpec(): ModConfigSpec = spec.build()
}
