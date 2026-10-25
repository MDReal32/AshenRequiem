package com.mdreal.ashenrequiem.world.compat.jer

import com.mdreal.ashenrequiem.core.compat.jer.JerHooks
import com.mdreal.ashenrequiem.world.init.Blocks
import com.mdreal.ashenrequiem.world.progression.DeadlandsArrival
import jeresources.api.distributions.DistributionBase
import jeresources.api.distributions.DistributionSquare
import jeresources.api.restrictions.DimensionRestriction
import jeresources.api.restrictions.Restriction
import jeresources.entry.WorldGenEntry
import jeresources.registry.WorldGenRegistry
import jeresources.util.LootTableHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block

/**
 * The Dead Lands' own resources in JER's world-gen tab. Added on every JER rebuild, so the drops shown are read from
 * the loot tables in use (a pack's changes included). Only touched when JER is installed.
 */
object DeadlandsJer {
    // The Dead Lands' surface, measured across the dimension: almost all of it lies between y 37 and 79.
    private const val SURFACE_MIN = 36
    private const val SURFACE_MAX = 80
    // Trees stand up to about 15 blocks over the ground.
    private const val CANOPY_MAX = 95

    fun hook() = JerHooks.onRebuild(::register)

    private fun register() {
        val deadlands = Restriction(DimensionRestriction(DeadlandsArrival.DEADLANDS))
        // About 0.7 rubble blocks per chunk (a heap of ~3 in one chunk out of 4), lying on the surface.
        add(Blocks.RUBBLE.block.get(), perChunk(0.7f, SURFACE_MIN, SURFACE_MAX), deadlands)
        // Dead trees and fallen logs: roughly 4 logs per chunk where trees grow, from the ground up.
        add(Blocks.DEAD_LOG.block.get(), perChunk(4f, SURFACE_MIN, CANOPY_MAX), deadlands)
    }

    /** JER's chance is per block on each layer, so a count per chunk is spread over the 16×16 columns and the layers. */
    private fun perChunk(blocks: Float, minY: Int, maxY: Int): DistributionBase =
        DistributionSquare(minY, maxY, blocks / (256f * (maxY - minY + 1)))

    private fun add(block: Block, distribution: DistributionBase, restriction: Restriction) {
        val drops = LootTableHelper.toDrops(block.lootTable)
        WorldGenRegistry.getInstance().registerEntry(
            WorldGenEntry(ItemStack(block), distribution, restriction, false, *drops.toTypedArray())
        )
    }
}
