package com.mdreal.ashenrequiem.world.worldGen

import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance
import com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.resources.RegistryFileCodec
import net.minecraft.server.level.WorldGenRegion
import net.minecraft.world.level.LevelHeightAccessor
import net.minecraft.world.level.NoiseColumn
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.biome.BiomeSource
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings
import net.minecraft.world.level.levelgen.RandomState
import net.minecraft.world.level.levelgen.blending.Blender
import java.util.concurrent.CompletableFuture

class DeadlandsChunkGenerator(
    private val biomeSrc: BiomeSource,
    private val settings: Holder<NoiseGeneratorSettings>
) : ChunkGenerator(biomeSrc) {

    companion object {
        val CODEC: MapCodec<DeadlandsChunkGenerator> = mapCodec { instance ->
            val biomeSourceField: RecordCodecBuilder<DeadlandsChunkGenerator, BiomeSource> =
                BiomeSource.CODEC
                    .fieldOf("biome_source")
                    .forGetter { generator -> generator.biomeSource }

            val settingsHolderField: RecordCodecBuilder<DeadlandsChunkGenerator, Holder<NoiseGeneratorSettings>> =
                RegistryFileCodec.create(Registries.NOISE_SETTINGS, NoiseGeneratorSettings.DIRECT_CODEC)
                    .fieldOf("settings")
                    .forGetter { generator -> generator.settings }


            instance
                .group(biomeSourceField, settingsHolderField)
                .apply(instance) { biomeSource, settingsHolder ->
                    DeadlandsChunkGenerator(biomeSource, settingsHolder)
                }
        }
    }

    private val vanilla = NoiseBasedChunkGenerator(biomeSrc, settings)
    override fun codec(): MapCodec<out ChunkGenerator> = CODEC

    override fun applyCarvers(
        region: WorldGenRegion,
        seed: Long,
        random: RandomState,
        biomeManager: net.minecraft.world.level.biome.BiomeManager,
        structureManager: StructureManager,
        chunk: ChunkAccess
    ) = vanilla.applyCarvers(region, seed, random, biomeManager, structureManager, chunk)

    override fun buildSurface(
        region: WorldGenRegion,
        structureManager: StructureManager,
        random: RandomState,
        chunk: ChunkAccess
    ) = vanilla.buildSurface(region, structureManager, random, chunk)

    override fun fillFromNoise(
        blender: Blender,
        random: RandomState,
        structureManager: StructureManager,
        chunk: ChunkAccess
    ): CompletableFuture<ChunkAccess> =
        vanilla.fillFromNoise(blender, random, structureManager, chunk)

    override fun spawnOriginalMobs(region: WorldGenRegion) =
        vanilla.spawnOriginalMobs(region)

    override fun getGenDepth(): Int = vanilla.genDepth
    override fun getSeaLevel(): Int = vanilla.seaLevel
    override fun getMinY(): Int = vanilla.minY

    override fun getBaseHeight(
        x: Int, z: Int, type: Heightmap.Types, level: LevelHeightAccessor, random: RandomState
    ): Int = vanilla.getBaseHeight(x, z, type, level, random)

    override fun getBaseColumn(
        x: Int, z: Int, level: LevelHeightAccessor, random: RandomState
    ): NoiseColumn= vanilla.getBaseColumn(x, z, level, random)

    override fun addDebugScreenInfo(lines: MutableList<String>, random: RandomState, pos: net.minecraft.core.BlockPos) {
        vanilla.addDebugScreenInfo(lines, random, pos)
    }
}