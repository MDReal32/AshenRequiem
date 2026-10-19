package com.mdreal.ashenrequiem.world.worldgen

import com.mojang.logging.LogUtils
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.Holder
import net.minecraft.core.HolderSet
import net.minecraft.core.QuartPos
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.BiomeSource
import net.minecraft.world.level.biome.Climate
import java.util.stream.Stream
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Wraps another biome source and carves one hidden region into it (ROADMAP D10, the Last Garden).
 *
 * The region sits [minDistance]..[maxDistance] blocks from the origin, along a bearing derived from the world's
 * noise (so it is seed-stable without any saved state), and only where the whole blob would otherwise be
 * one of the [disguise] biomes (which must share the garden's ground blocks). That keeps it invisible until the veil
 * lifts: the terrain is unchanged and the veil shows each spot as the biome that would otherwise be there.
 */
class VeiledRegionBiomeSource(
    val base: BiomeSource,
    val biome: Holder<Biome>,
    val disguise: HolderSet<Biome>,
    val minDistance: Int,
    val maxDistance: Int,
    val radius: Int,
) : BiomeSource() {

    /** A noise-edged blob. [bearing] also points toward Vigil Station (D5). */
    data class Region(val centerX: Int, val centerZ: Int, val radius: Int, val bearing: Double,
                      private val phase1: Double, private val phase2: Double) {
        fun edgeAt(angle: Double): Double = radius * (1 + 0.22 * sin(3 * angle + phase1) + 0.12 * sin(5 * angle + phase2))

        fun contains(x: Int, z: Int): Boolean {
            val dx = (x - centerX).toDouble()
            val dz = (z - centerZ).toDouble()
            val d2 = dx * dx + dz * dz
            if (d2 > MAX_EDGE * MAX_EDGE * radius * radius) return false
            return sqrt(d2) < edgeAt(atan2(dz, dx))
        }
    }

    // Worldgen threads share one sampler per world, so a single-entry cache is enough and avoids hashing samplers.
    @Volatile private var cached: Pair<Climate.Sampler, Region>? = null

    fun region(sampler: Climate.Sampler): Region {
        cached?.let { if (it.first === sampler) return it.second }
        return locate(sampler).also { cached = sampler to it }
    }

    override fun codec(): MapCodec<out BiomeSource> = CODEC

    override fun collectPossibleBiomes(): Stream<Holder<Biome>> =
        Stream.concat(base.possibleBiomes().stream(), Stream.of(biome)).distinct()

    override fun getNoiseBiome(quartX: Int, quartY: Int, quartZ: Int, sampler: Climate.Sampler): Holder<Biome> {
        val x = QuartPos.toBlock(quartX) + 2
        val z = QuartPos.toBlock(quartZ) + 2
        return if (region(sampler).contains(x, z)) biome else base.getNoiseBiome(quartX, quartY, quartZ, sampler)
    }

    private fun locate(sampler: Climate.Sampler): Region {
        val random = java.util.Random(worldHash(sampler))
        val bearing = random.nextDouble() * 2 * PI
        val phase1 = random.nextDouble() * 2 * PI
        val phase2 = random.nextDouble() * 2 * PI
        // Search outward along the bearing, then swing left and right of it in 20 degree steps.
        // If nothing fits at full size, the garden may shrink to two thirds before giving up.
        for (size in intArrayOf(radius, radius * 5 / 6, radius * 2 / 3)) {
            for (turn in 0 until 18) {
                val swing = (turn + 1) / 2 * (if (turn % 2 == 1) 1 else -1) * (PI / 9)
                val angle = bearing + swing
                for (distance in minDistance..maxDistance step 25) {
                    val candidate = Region((cos(angle) * distance).toInt(), (sin(angle) * distance).toInt(),
                        size, bearing, phase1, phase2)
                    if (fitsInsideDisguise(candidate, sampler)) return candidate
                }
            }
        }
        // Still nothing: look further out at the smallest size before settling for a spot that doesn't fit.
        for (turn in 0 until 18) {
            val angle = bearing + turn * (PI / 9)
            for (distance in maxDistance..maxDistance * 2 step 25) {
                val candidate = Region((cos(angle) * distance).toInt(), (sin(angle) * distance).toInt(),
                    radius * 2 / 3, bearing, phase1, phase2)
                if (fitsInsideDisguise(candidate, sampler)) return candidate
            }
        }
        // No clean spot at all: keep the garden anyway, halfway out on the bearing.
        LOGGER.warn("No area of {} fits {}; placing it on the bearing anyway", disguise, biome.unwrapKey().orElse(null))
        val distance = (minDistance + maxDistance) / 2
        return Region((cos(bearing) * distance).toInt(), (sin(bearing) * distance).toInt(), radius, bearing, phase1, phase2)
    }

    /** True when the centre, a middle ring and the edge itself all fall on [disguise] biomes in [base]. */
    private fun fitsInsideDisguise(region: Region, sampler: Climate.Sampler): Boolean {
        fun isDisguise(x: Int, z: Int) =
            disguise.contains(base.getNoiseBiome(QuartPos.fromBlock(x), SURFACE_QUART_Y, QuartPos.fromBlock(z), sampler))
        if (!isDisguise(region.centerX, region.centerZ)) return false
        for (i in 0 until 12) {
            val angle = i * PI / 6
            for (scale in doubleArrayOf(0.5, 0.97)) {
                val r = region.edgeAt(angle) * scale
                if (!isDisguise(region.centerX + (cos(angle) * r).toInt(), region.centerZ + (sin(angle) * r).toInt())) return false
            }
        }
        return true
    }

    companion object {
        /** Largest edge multiplier [Region.edgeAt] can produce (1 + 0.22 + 0.12). */
        private const val MAX_EDGE = 1.34
        private val LOGGER = LogUtils.getLogger()
        private val SURFACE_QUART_Y = QuartPos.fromBlock(64)

        val CODEC: MapCodec<VeiledRegionBiomeSource> = RecordCodecBuilder.mapCodec { i ->
            i.group(
                BiomeSource.CODEC.fieldOf("base").forGetter { it.base },
                Biome.CODEC.fieldOf("biome").forGetter { it.biome },
                Biome.LIST_CODEC.fieldOf("disguise").forGetter { it.disguise },
                Codec.intRange(0, 100_000).fieldOf("min_distance").forGetter { it.minDistance },
                Codec.intRange(0, 100_000).fieldOf("max_distance").forGetter { it.maxDistance },
                Codec.intRange(16, 4096).fieldOf("radius").forGetter { it.radius },
            ).apply(i) { base, biome, disguise, min, max, radius ->
                VeiledRegionBiomeSource(base, biome, disguise, min, max, radius)
            }
        }

        /**
         * A seed-stable number for this world. Biome sources never see the seed, but the climate noise is built
         * from it, so a few far-away samples identify the world just as well.
         */
        private fun worldHash(sampler: Climate.Sampler): Long {
            var hash = -0x61c8864680b583ebL
            for (i in 0 until 6) {
                val p = sampler.sample(QuartPos.fromBlock(i * 7919 - 20000), SURFACE_QUART_Y, QuartPos.fromBlock(15000 - i * 6271))
                for (v in longArrayOf(p.temperature, p.humidity, p.continentalness, p.erosion, p.weirdness)) {
                    hash = (hash xor v) * 0x100000001b3L
                }
            }
            return hash
        }
    }
}
