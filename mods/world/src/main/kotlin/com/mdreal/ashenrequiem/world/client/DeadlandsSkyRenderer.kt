package com.mdreal.ashenrequiem.world.client

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexBuffer
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.math.Axis
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.FogRenderer
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.material.FogType
import org.joml.Matrix4f

/**
 * Vanilla's overworld sky with the light sources swapped out: the sun and moon are soft, pale glows seen
 * through ash instead of sharp textures, there is no sunrise band, and no stars ever show.
 * Daylight still follows the normal day cycle; only what is drawn changes.
 */
object DeadlandsSkyRenderer {
    /** One additive layer of a glow: [radius] at a distance of 100, [alpha] at its centre fading to 0 at the edge. */
    private data class Glow(val radius: Float, val alpha: Float)

    // Stacked layers give a bright core with a wide, faint halo.
    private val SUN = listOf(Glow(7f, 0.55f), Glow(18f, 0.22f), Glow(55f, 0.08f))
    private val MOON = listOf(Glow(5f, 0.35f), Glow(14f, 0.10f), Glow(38f, 0.03f))

    // Bone white for the sun, pale ash for the moon (ROADMAP §6 palette).
    private const val SUN_COLOR = 0xE6E1D3
    private const val MOON_COLOR = 0xBDBAB2

    private const val GLOW_SEGMENTS = 24

    private var skyDisc: VertexBuffer? = null
    private var darkDisc: VertexBuffer? = null

    fun render(level: ClientLevel, partialTick: Float, frustumMatrix: Matrix4f, camera: Camera,
               projectionMatrix: Matrix4f, isFoggy: Boolean, setupFog: Runnable) {
        setupFog.run()
        if (isFoggy) return
        val fluid = camera.fluidInCamera
        if (fluid == FogType.POWDER_SNOW || fluid == FogType.LAVA || effectBlocksSky(camera)) return

        val pose = PoseStack().apply { mulPose(frustumMatrix) }
        val sky = level.getSkyColor(camera.position, partialTick)
        FogRenderer.levelFogColor()
        RenderSystem.depthMask(false)
        RenderSystem.setShader(GameRenderer::getPositionShader)
        val positionShader = requireNotNull(RenderSystem.getShader()) { "Position shader is not loaded" }
        RenderSystem.setShaderColor(sky.x.toFloat(), sky.y.toFloat(), sky.z.toFloat(), 1f)
        draw(skyDisc ?: buildDisc(16f).also { skyDisc = it }, pose, projectionMatrix, positionShader)

        // Additive like vanilla's sun, so the glows brighten the sky rather than paint over it.
        RenderSystem.enableBlend()
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
        )
        RenderSystem.disableCull()
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        val clear = 1f - level.getRainLevel(partialTick)
        pose.pushPose()
        pose.mulPose(Axis.YP.rotationDegrees(-90f))
        pose.mulPose(Axis.XP.rotationDegrees(level.getTimeOfDay(partialTick) * 360f))
        val matrix = pose.last().pose()
        SUN.forEach { glow(matrix, 100f, it, SUN_COLOR, clear) }
        MOON.forEach { glow(matrix, -100f, it, MOON_COLOR, clear * level.moonBrightness) }
        pose.popPose()
        RenderSystem.enableCull()
        RenderSystem.disableBlend()
        RenderSystem.defaultBlendFunc()

        // Below the horizon, the same dark lower disc vanilla draws.
        val player = camera.entity
        if (player.getEyePosition(partialTick).y - level.levelData.getHorizonHeight(level) < 0.0) {
            RenderSystem.setShader(GameRenderer::getPositionShader)
            RenderSystem.setShaderColor(0f, 0f, 0f, 1f)
            pose.pushPose()
            pose.translate(0f, 12f, 0f)
            draw(darkDisc ?: buildDisc(-16f).also { darkDisc = it }, pose, projectionMatrix, positionShader)
            pose.popPose()
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.depthMask(true)
    }

    private fun glow(matrix: Matrix4f, y: Float, glow: Glow, rgb: Int, strength: Float) {
        val alpha = glow.alpha * strength
        if (alpha <= 0f) return
        val r = (rgb shr 16 and 0xFF) / 255f
        val g = (rgb shr 8 and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR)
        buffer.addVertex(matrix, 0f, y, 0f).setColor(r, g, b, alpha)
        for (i in 0..GLOW_SEGMENTS) {
            val angle = i * Mth.TWO_PI / GLOW_SEGMENTS
            buffer.addVertex(matrix, Mth.cos(angle) * glow.radius, y, Mth.sin(angle) * glow.radius).setColor(r, g, b, 0f)
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow())
    }

    private fun draw(disc: VertexBuffer, pose: PoseStack, projection: Matrix4f, shader: ShaderInstance) {
        disc.bind()
        disc.drawWithShader(pose.last().pose(), projection, shader)
        VertexBuffer.unbind()
    }

    /** Same flat sky disc vanilla builds (private in `LevelRenderer`). */
    private fun buildDisc(y: Float): VertexBuffer {
        val edge = Math.signum(y) * 512f
        val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION)
        buffer.addVertex(0f, y, 0f)
        for (degrees in -180..180 step 45) {
            val angle = degrees * Mth.DEG_TO_RAD
            buffer.addVertex(edge * Mth.cos(angle), y, 512f * Mth.sin(angle))
        }
        return VertexBuffer(VertexBuffer.Usage.STATIC).apply {
            bind()
            upload(buffer.buildOrThrow())
            VertexBuffer.unbind()
        }
    }

    private fun effectBlocksSky(camera: Camera): Boolean {
        val entity = camera.entity as? LivingEntity ?: return false
        return entity.hasEffect(MobEffects.BLINDNESS) || entity.hasEffect(MobEffects.DARKNESS)
    }
}
