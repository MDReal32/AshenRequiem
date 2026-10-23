package com.mdreal.ashenrequiem.world.client

import com.mdreal.ashenrequiem.world.WorldConfig
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.DimensionSpecialEffects
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent
import org.joml.Matrix4f

/**
 * The Dead Lands sky: an overcast, colourless overworld. No warm sunrise or sunset band,
 * and the fog darkens evenly with the daylight instead of keeping the overworld's blue tint at night.
 * The sky itself is drawn by [DeadlandsSkyRenderer] (hazy sun and moon, no stars).
 * Bound by `"effects": "ashen_requiem_world:deadlands"` in the dimension type.
 */
class DeadlandsSkyEffects : DimensionSpecialEffects(CLOUD_LEVEL, true, SkyType.NORMAL, false, false) {
    override fun getBrightnessDependentFogColor(fogColor: Vec3, brightness: Float): Vec3 =
        fogColor.scale((brightness * 0.92f + 0.08f).toDouble())

    override fun isFoggyAt(x: Int, y: Int): Boolean = false

    /** Nothing warm ever reaches this sky. Also keeps vanilla from tinting the fog orange toward the sun. */
    override fun getSunriseColor(timeOfDay: Float, partialTicks: Float): FloatArray? = null

    override fun renderSky(level: ClientLevel, ticks: Int, partialTick: Float, modelViewMatrix: Matrix4f, camera: Camera,
                           projectionMatrix: Matrix4f, isFoggy: Boolean, setupFog: Runnable): Boolean {
        DeadlandsSkyRenderer.render(level, partialTick, modelViewMatrix, camera, projectionMatrix, isFoggy, setupFog)
        return true
    }

    private companion object {
        /** Same height as the overworld's clouds, so the Shattered Highlands peaks stay under them. */
        const val CLOUD_LEVEL = 192f
    }
}

@EventBusSubscriber(modid = WorldConfig.MOD_ID, value = [Dist.CLIENT])
object DeadlandsSkyRegistration {
    val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(WorldConfig.MOD_ID, "deadlands")

    @SubscribeEvent
    fun onRegister(event: RegisterDimensionSpecialEffectsEvent) {
        event.register(ID, DeadlandsSkyEffects())
    }
}
