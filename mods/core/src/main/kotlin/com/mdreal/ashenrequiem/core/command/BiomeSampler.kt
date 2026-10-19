package com.mdreal.ashenrequiem.core.command

import net.minecraft.core.QuartPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.biome.Climate
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator
import net.minecraft.world.level.levelgen.RandomState
import kotlin.math.sqrt

/**
 * Asks a dimension's biome source what it would place on a grid, without generating any chunks.
 * With [seeds], it builds a fresh noise state per seed (1..seeds) so placement can be judged across many worlds.
 */
object BiomeSampler {
    data class Stat(val biome: String, val meanShare: Double, val minShare: Double, val maxShare: Double,
                    val seedsFound: Int, val nearestMin: Int, val nearestMax: Int)

    fun sample(level: ServerLevel, radius: Int, step: Int, seeds: Int?): List<Stat> {
        val generator = level.chunkSource.generator
        val samplers: List<Climate.Sampler> = if (seeds == null) {
            listOf(level.chunkSource.randomState().sampler())
        } else {
            val noise = generator as? NoiseBasedChunkGenerator
                ?: error("${level.dimension().identifier()} is not noise-based; it can only be sampled with its own seed")
            val noises = level.server.registryAccess().lookupOrThrow(Registries.NOISE)
            (1..seeds).map { RandomState.create(noise.generatorSettings().value(), noises, it.toLong()).sampler() }
        }
        val qy = QuartPos.fromBlock(level.seaLevel.coerceAtLeast(64))
        val shares = mutableMapOf<String, MutableList<Double>>()
        val nearest = mutableMapOf<String, MutableList<Int>>()
        for (sampler in samplers) {
            val count = mutableMapOf<String, Int>()
            val near = mutableMapOf<String, Int>()
            var total = 0
            for (x in -radius..radius step step) for (z in -radius..radius step step) {
                val holder = generator.biomeSource.getNoiseBiome(QuartPos.fromBlock(x), qy, QuartPos.fromBlock(z), sampler)
                val id = holder.unwrapKey().map { it.identifier().toString() }.orElse("?")
                count.merge(id, 1, Int::plus)
                val dist = sqrt((x.toDouble() * x + z.toDouble() * z)).toInt()
                near.merge(id, dist, ::minOf)
                total++
            }
            count.forEach { (id, n) -> shares.getOrPut(id) { mutableListOf() } += 100.0 * n / total }
            near.forEach { (id, d) -> nearest.getOrPut(id) { mutableListOf() } += d }
        }
        return shares.map { (id, list) ->
            val padded = list + List(samplers.size - list.size) { 0.0 }
            Stat(id, padded.average(), padded.min(), padded.max(), list.size, nearest[id]!!.min(), nearest[id]!!.max())
        }.sortedByDescending { it.meanShare }
    }
}
