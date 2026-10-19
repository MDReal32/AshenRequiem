package com.mdreal.ashenrequiem.core

import net.minecraft.resources.ResourceLocation

object CoreConfig {
    const val MOD_ID = "ashen_requiem_core"

    fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
}
