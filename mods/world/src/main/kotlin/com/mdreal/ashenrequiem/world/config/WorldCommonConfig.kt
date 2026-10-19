package com.mdreal.ashenrequiem.world.config

import net.neoforged.neoforge.common.ModConfigSpec

object WorldCommonConfig {
    private val spec = ModConfigSpec.Builder()
    fun getSpec(): ModConfigSpec = spec.build()
    
    val forceDeadlandsOnly: ModConfigSpec.BooleanValue = spec
        .comment("If true, shows a minimal create-world screen locked to the Ashen Requiem preset; if false, uses the normal world setup.")
        .define("forceDeadlandsOnly", false)
}