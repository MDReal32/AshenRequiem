package com.mdreal.ashenrequiem.world.worldGen

import com.mdreal.ashenrequiem.world.WorldConfig
import com.mojang.serialization.MapCodec
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.Identifier
import net.minecraft.world.level.chunk.ChunkGenerator
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.RegisterEvent

object ModWorldgen {
    private val CHUNK_GENERATOR_KEY: ResourceKey<MapCodec<out ChunkGenerator>> =
        ResourceKey.create(
            Registries.CHUNK_GENERATOR,
            Identifier.fromNamespaceAndPath(WorldConfig.MOD_ID, "deadlands")
        )

    fun hook(eventBus: IEventBus) {
        eventBus.addListener(::onRegister)
    }


    private fun onRegister(registerEvent: RegisterEvent) {
        if (registerEvent.registryKey == Registries.CHUNK_GENERATOR) {
            registerEvent.register(
                Registries.CHUNK_GENERATOR,
                CHUNK_GENERATOR_KEY.identifier()
            ) {
                DeadlandsChunkGenerator.CODEC
            }
        }
    }
}