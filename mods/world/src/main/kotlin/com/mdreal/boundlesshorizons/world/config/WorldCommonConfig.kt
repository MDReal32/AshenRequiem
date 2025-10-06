package com.mdreal.boundlesshorizons.world.config

import net.neoforged.neoforge.common.ModConfigSpec

object WorldCommonConfig {
    private val spec = ModConfigSpec.Builder()
    fun getSpec(): ModConfigSpec = spec.build()
    
    val forceDeadlandsOnly: ModConfigSpec.BooleanValue = spec
        .comment("If true, shows minimal UI with Deadlands preset; if false, uses the normal world setup.")
        .define("forceDeadlandsOnly", false)
}