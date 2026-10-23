package com.mdreal.ashenrequiem.world.client

import com.mdreal.ashenrequiem.world.WorldConfig
import com.mdreal.ashenrequiem.world.progression.DeadlandsArrival
import net.minecraft.client.renderer.FogRenderer
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.material.FogType
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ViewportEvent
import kotlin.math.exp

/**
 * Ash haze: pulls the terrain fog in from the render distance to a per-biome distance in the Dead Lands,
 * so the world fades into grey instead of ending at a hard chunk edge. Moving between biomes eases
 * the distance over a second or two rather than snapping. Fluids, blindness and darkness keep vanilla fog.
 */
@EventBusSubscriber(modid = WorldConfig.MOD_ID, value = [Dist.CLIENT])
object DeadlandsFog {
    /** Where the fog is fully opaque, in blocks. Never further than the player's render distance. */
    private val FOG_END: Map<ResourceKey<Biome>, Float> = mapOf(
        biome("ash_plains") to 160f,
        // Must match Ash Plains: the garden is disguised as it until the reveal (ROADMAP D10).
        biome("last_garden") to 160f,
        biome("ash_ocean") to 192f,
        biome("bone_desert") to 224f,
        biome("petrified_forest") to 112f,
        biome("grey_marsh") to 64f,
        biome("shattered_highlands") to 192f,
    )
    private const val DEFAULT_END = 160f

    /** Fog starts at this fraction of its end distance. */
    private const val START_FRACTION = 0.2f

    /** Seconds for the distance to cover about 63% of the way to a new biome's value. */
    private const val EASE_SECONDS = 1.5

    private var current: Float? = null
    private var lastNanos = 0L

    @SubscribeEvent
    fun onRenderFog(event: ViewportEvent.RenderFog) {
        if (event.mode != FogRenderer.FogMode.FOG_TERRAIN) return
        val entity = event.camera.entity
        val level = entity.level()
        if (level.dimension() != DeadlandsArrival.DEADLANDS) {
            current = null
            return
        }
        if (event.type != FogType.NONE) return
        if (entity is LivingEntity && (entity.hasEffect(MobEffects.BLINDNESS) || entity.hasEffect(MobEffects.DARKNESS))) return

        val target = level.getBiome(event.camera.blockPosition).unwrapKey()
            .map { FOG_END[it] ?: DEFAULT_END }.orElse(DEFAULT_END)
        val end = ease(target).coerceAtMost(event.farPlaneDistance)
        event.nearPlaneDistance = end * START_FRACTION
        event.farPlaneDistance = end
        event.isCanceled = true
    }

    private fun ease(target: Float): Float {
        val now = System.nanoTime()
        val previous = current
        val next = if (previous == null) {
            target
        } else {
            val seconds = (now - lastNanos) / 1_000_000_000.0
            previous + (target - previous) * (1 - exp(-seconds / EASE_SECONDS)).toFloat()
        }
        current = next
        lastNanos = now
        return next
    }

    private fun biome(path: String): ResourceKey<Biome> =
        ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(WorldConfig.MOD_ID, "deadlands/$path"))
}
