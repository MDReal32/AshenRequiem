package com.mdreal.ashenrequiem.core.api

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import java.util.concurrent.ConcurrentHashMap

/**
 * Where a player lands when sent to a dimension by story code or `/ar tp`.
 * Content modules register their own resolver (e.g. the Dead Lands Cradle).
 */
object DimensionSpawns {
    private val resolvers = ConcurrentHashMap<ResourceKey<Level>, (ServerLevel) -> BlockPos>()

    fun register(dimension: ResourceKey<Level>, resolver: (ServerLevel) -> BlockPos) {
        resolvers[dimension] = resolver
    }

    fun resolve(level: ServerLevel): BlockPos {
        resolvers[level.dimension()]?.let { return it(level) }
        val spawn = level.respawnData.globalPos().pos()
        return safeSurface(level, spawn.x, spawn.z)
    }

    fun teleport(player: ServerPlayer, level: ServerLevel) = teleport(player, level, resolve(level))

    fun teleport(player: ServerPlayer, level: ServerLevel, pos: BlockPos) {
        player.teleportTo(level, pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5, emptySet(), player.yRot, player.xRot, true)
    }

    /**
     * Like [safeSurface], but walks outward in square rings until it finds ground that is not
     * on or under a liquid. Falls back to the centre if nothing dry is found within [maxRadius].
     */
    fun findDryLand(level: ServerLevel, x: Int, z: Int, step: Int = 64, maxRadius: Int = 4096): BlockPos {
        for (r in 0..maxRadius step step) {
            for (i in -r..r step step) {
                val ring = if (r == 0) listOf(x to z) else listOf(x + i to z - r, x + i to z + r, x - r to z + i, x + r to z + i)
                for ((px, pz) in ring) {
                    val pos = safeSurface(level, px, pz)
                    if (level.getFluidState(pos).isEmpty && level.getFluidState(pos.below()).isEmpty) return pos
                }
                if (r == 0) break
            }
        }
        return safeSurface(level, x, z)
    }

    /** First standable position at (x, z). Forces the chunk to generate. */
    fun safeSurface(level: ServerLevel, x: Int, z: Int): BlockPos {
        level.getChunk(x shr 4, z shr 4)
        if (!level.dimensionType().hasCeiling) {
            return BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z)
        }
        // Ceiling dimensions: the heightmap points at the roof, so scan up for an air pocket instead.
        val top = level.minY + level.logicalHeight - 2
        val cursor = BlockPos.MutableBlockPos(x, level.minY + 1, z)
        while (cursor.y < top) {
            val below = cursor.below()
            val standable = level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
            if (standable && level.getBlockState(cursor).isAir && level.getBlockState(cursor.above()).isAir) {
                return cursor.immutable()
            }
            cursor.move(0, 1, 0)
        }
        return BlockPos(x, top, z)
    }
}
