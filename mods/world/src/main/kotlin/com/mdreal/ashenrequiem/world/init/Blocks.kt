package com.mdreal.ashenrequiem.world.init

import com.mdreal.ashenrequiem.world.WorldConfig
import com.mdreal.ashenrequiem.world.utils.BlockWithItem
import com.mdreal.ashenrequiem.world.utils.registerBlockWithItem
import net.minecraft.world.level.block.Block
import net.minecraft.util.ColorRGBA
import net.minecraft.world.level.block.ColoredFallingBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument
import net.minecraft.world.level.block.Blocks as VanillaBlocks
import net.minecraft.world.level.material.MapColor
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister

object Blocks {
    val BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(WorldConfig.MOD_ID)

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
    /** Loose grey ash; the floor of the dry Ash Ocean basins. Falls like sand. */
    val ASH = registerBlockWithItem(
        "ash", { it.mapColor(MapColor.COLOR_LIGHT_GRAY).instrument(NoteBlockInstrument.SNARE).strength(0.5f).sound(SoundType.SAND) },
        factory = { ColoredFallingBlock(ColorRGBA(0x9A9893), it) }
    )
    /** Crushed bone; the Bone Desert surface. Falls like sand. */
    val BONE_SAND = registerBlockWithItem(
        "bone_sand", { it.mapColor(MapColor.SAND).instrument(NoteBlockInstrument.SNARE).strength(0.5f).sound(SoundType.SAND) },
        factory = { ColoredFallingBlock(ColorRGBA(0xE6E1D3), it) }
    )
    val GREY_MARSH_MUD = registerBlockWithItem(
        "grey_marsh_mud", { it.mapColor(MapColor.TERRACOTTA_GRAY).strength(0.5f).sound(SoundType.MUD) }
    )
    /** The Dead Lands floor; replaces vanilla bedrock there. */
    val ASHEN_BEDROCK = registerBlockWithItem(
        "ashen_bedrock", {
            it.mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).strength(-1.0f, 3600000.0f)
                .noLootTable().isValidSpawn(VanillaBlocks::never).sound(SoundType.STONE)
        }
    )
}