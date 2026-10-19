package com.mdreal.ashenrequiem.core

import net.minecraft.resources.Identifier

object CoreConfig {
    const val MOD_ID = "ashen_requiem_core"

    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
