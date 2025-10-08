package com.mdreal.boundlesshorizons.world.blocks

import com.mdreal.boundlesshorizons.core.BlockRegistries
import com.mdreal.boundlesshorizons.world.WorldConfig

object Blocks {
    val ASHEN_DIRT = BlockRegistries.prepare(
        "${WorldConfig.MOD_ID}:dirt",
        factory = {

        }
    )

    val blocks = arrayOf(
        ASHEN_DIRT
    )

    fun registerAll() {


        blocks.forEach { it.register() }
    }
}