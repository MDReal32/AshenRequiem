package com.mdreal.boundlesshorizons.world.config

import net.neoforged.neoforge.common.ModConfigSpec

// ToDo: Implement Configuration file for that config
object WorldCommonConfig {
    private val builder = ModConfigSpec.Builder()
    
    val forceDeadlandsOnly = builder
        .comment("If true, Create World UI is reduced to Name + Game Mode, and Deadlands preset is forced.")
        .define("worldPreset.forceDeadlandsOnly", false)

    val spec: ModConfigSpec = builder.build()
}