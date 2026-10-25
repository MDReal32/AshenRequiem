package com.mdreal.ashenrequiem.core.compat.jei

import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.common.crafting.SizedIngredient

/**
 * Something done to a block in the world rather than in a grid: using [held] on [block] turns it into [result]
 * (scraping a log, repairing a workbench). Shown by [BlockInteractionCategory].
 */
data class BlockInteraction(
    val held: List<SizedIngredient>,
    val block: ItemStack,
    val result: ItemStack,
)
