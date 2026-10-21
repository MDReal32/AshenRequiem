package com.mdreal.ashenrequiem.world.worldgen

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.valueproviders.IntProvider
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.RotatedPillarBlock
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext
import net.minecraft.world.level.levelgen.feature.TreeFeature
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider

/**
 * A stump with the rest of its trunk lying beside it, like the fallen trees vanilla added after 1.21.1.
 * The log is only laid if all of it fits on the ground (gaps of up to two blocks are bridged); the stump always stays.
 */
class FallenLogFeature : Feature<FallenLogFeature.Config>(Config.CODEC) {
    data class Config(val log: BlockStateProvider, val length: IntProvider) : FeatureConfiguration {
        companion object {
            val CODEC: Codec<Config> = RecordCodecBuilder.create { i ->
                i.group(
                    BlockStateProvider.CODEC.fieldOf("log").forGetter(Config::log),
                    IntProvider.codec(3, 16).fieldOf("length").forGetter(Config::length),
                ).apply(i, ::Config)
            }
        }
    }

    override fun place(context: FeaturePlaceContext<Config>): Boolean {
        val level = context.level()
        val random = context.random()
        val config = context.config()
        val origin = context.origin()
        level.setBlock(origin, config.log.getState(random, origin), 3)

        val direction = Direction.Plane.HORIZONTAL.getRandomDirection(random)
        val length = config.length.sample(random) - 2
        val start = origin.relative(direction, 2 + random.nextInt(2)).mutable().move(Direction.UP)
        // Settle the start onto the ground, up to five blocks down a slope.
        for (i in 0 until 6) {
            if (TreeFeature.validTreePos(level, start) && onSolidGround(level, start)) break
            start.move(Direction.DOWN)
        }
        if (!fits(level, start.immutable(), direction, length)) return true

        val pos = start
        repeat(length) {
            val state = config.log.getState(random, pos)
            level.setBlock(pos, if (state.hasProperty(RotatedPillarBlock.AXIS)) state.setValue(RotatedPillarBlock.AXIS, direction.axis) else state, 3)
            pos.move(direction)
        }
        return true
    }

    private fun fits(level: LevelAccessor, start: BlockPos, direction: Direction, length: Int): Boolean {
        var gap = 0
        for (i in 0 until length) {
            val pos = start.relative(direction, i)
            if (!TreeFeature.validTreePos(level, pos)) return false
            gap = if (onSolidGround(level, pos)) 0 else gap + 1
            if (gap > 2) return false
        }
        return true
    }

    private fun onSolidGround(level: LevelAccessor, pos: BlockPos): Boolean =
        level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
}
