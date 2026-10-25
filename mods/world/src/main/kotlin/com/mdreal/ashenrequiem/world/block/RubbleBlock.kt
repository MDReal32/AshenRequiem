package com.mdreal.ashenrequiem.world.block

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/** A half-high heap of what the old world left behind. The first source of scrap (ROADMAP D11). */
class RubbleBlock(props: Properties) : Block(props) {
    override fun codec(): MapCodec<out Block> = CODEC

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = SHAPE

    override fun useShapeForLightOcclusion(state: BlockState) = true

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean =
        level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)

    override fun updateShape(
        state: BlockState, direction: Direction, neighborState: BlockState, level: LevelAccessor, pos: BlockPos, neighborPos: BlockPos
    ): BlockState =
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) Blocks.AIR.defaultBlockState()
        else super.updateShape(state, direction, neighborState, level, pos, neighborPos)

    companion object {
        val CODEC: MapCodec<RubbleBlock> = simpleCodec(::RubbleBlock)
        private val SHAPE = box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0)
    }
}
