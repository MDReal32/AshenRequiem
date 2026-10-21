package com.mdreal.ashenrequiem.world.worldgen

import com.mdreal.ashenrequiem.world.WorldConfig
import com.mojang.serialization.MapCodec
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.biome.BiomeSource
import net.minecraft.world.level.levelgen.feature.Feature
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister

object ModWorldgen {
    private val BIOME_SOURCES: DeferredRegister<MapCodec<out BiomeSource>> =
        DeferredRegister.create(Registries.BIOME_SOURCE, WorldConfig.MOD_ID)
    private val FEATURES: DeferredRegister<Feature<*>> = DeferredRegister.create(Registries.FEATURE, WorldConfig.MOD_ID)

    init {
        BIOME_SOURCES.register("veiled_region") { -> VeiledRegionBiomeSource.CODEC }
        FEATURES.register("fallen_log") { -> FallenLogFeature() }
    }

    fun hook(bus: IEventBus) {
        BIOME_SOURCES.register(bus)
        FEATURES.register(bus)
    }
}
