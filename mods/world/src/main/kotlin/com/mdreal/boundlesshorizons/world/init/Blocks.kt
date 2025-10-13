package com.mdreal.boundlesshorizons.world.init

import com.mdreal.boundlesshorizons.world.WorldConfig
import com.mdreal.boundlesshorizons.world.utils.BlockWithItem
import com.mdreal.boundlesshorizons.world.utils.registerBlockWithItem
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.material.MapColor
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister

object Blocks {
    val BLOCKS: DeferredRegister<Block> = DeferredRegister.createBlocks(WorldConfig.MOD_ID)

    fun hook(bus: IEventBus) {
        BLOCKS.register(bus)
    }

    val allBlocks: MutableSet<BlockWithItem> = mutableSetOf()

    val ASHEN_DIRT = registerBlockWithItem(
        "ashen_dirt", { it.mapColor(MapColor.DIRT).strength(0.5f).sound(SoundType.GRAVEL) })
    val ASHEN_GRASS = registerBlockWithItem(
        "ashen_grass", { it.mapColor(MapColor.GRASS).strength(0.6f).sound(SoundType.GRASS) })
    val ASHEN_STONE = registerBlockWithItem(
        "ashen_stone", { it.mapColor(MapColor.STONE).strength(1.5f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.STONE) }
    )
    val ASHEN_COBBLE_STONE = registerBlockWithItem(
        "ashen_cobblestone", { it.mapColor(MapColor.STONE).strength(1.5f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.STONE) }
    )
}