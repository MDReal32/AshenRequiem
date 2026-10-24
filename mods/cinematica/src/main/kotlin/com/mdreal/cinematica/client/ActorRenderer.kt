package com.mdreal.cinematica.client

import com.mdreal.cinematica.entity.ActorEntity
import com.mdreal.cinematica.timeline.ActorState
import com.mdreal.cinematica.timeline.Pose
import com.mdreal.cinematica.timeline.Rot
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import java.util.EnumSet

/** Draws the actor in the skin of [ActorEntity.skinOwner], posed bone by bone, with elbows and knees. */
class ActorRenderer(context: EntityRendererProvider.Context) : EntityRenderer<ActorEntity>(context) {
    private val wide = Body(slim = false)
    private val slim = Body(slim = true)

    override fun getTextureLocation(entity: ActorEntity): ResourceLocation = skin(entity).texture()

    override fun render(
        entity: ActorEntity,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int
    ) {
        if (entity.hidden) return
        val skin = skin(entity)
        val body = if (skin.model() == PlayerSkin.Model.SLIM) slim else wide
        body.pose(entity.pose)

        poseStack.pushPose()
        // Same transforms as LivingEntityRenderer, plus the root tilt around the hips.
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - entity.bodyYaw))
        poseStack.translate(0f, HIP, 0f)
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.root.x))
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.root.y))
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.root.z))
        poseStack.translate(0f, -HIP, 0f)
        poseStack.scale(-1f, -1f, 1f)
        poseStack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE)
        poseStack.translate(0f, -1.501f, 0f)
        val consumer = buffer.getBuffer(RenderType.entityTranslucent(skin.texture()))
        body.parts.forEach { it.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY) }
        poseStack.popPose()
    }

    private fun skin(entity: ActorEntity): PlayerSkin {
        val owner = entity.skinOwner ?: return DefaultPlayerSkin.get(entity.uuid)
        return Minecraft.getInstance().connection?.getPlayerInfo(owner)?.skin ?: DefaultPlayerSkin.get(owner)
    }

    /**
     * The player model with each arm and leg cut in two at the middle. Every half carries the skin's base and outer
     * layer and reads its own half of the limb's texture, so a straight limb looks exactly like the vanilla one.
     */
    private class Body(slim: Boolean) {
        private val armWidth = if (slim) 3 else 4
        private val armY = if (slim) 2.5f else 2f

        val head = part(Box(0, 0, 32, 0, -4f, -8f, 8, 8, 8, grow = 0.5f))
        val torso = part(Box(16, 16, 16, 32, -4f, 0f, 8, 12, 4))
        val rightArm = limb(40, 16, 40, 32, if (slim) -2f else -3f, -2f, armWidth).at(-5f, armY)
        val leftArm = limb(32, 48, 48, 48, -1f, -2f, armWidth).at(5f, armY)
        val rightLeg = limb(0, 16, 0, 32, -2f, 0f, 4).at(-1.9f, 12f)
        val leftLeg = limb(16, 48, 0, 48, -2f, 0f, 4).at(1.9f, 12f)
        val parts = listOf(head, torso, rightArm, leftArm, rightLeg, leftLeg)

        fun pose(pose: Pose) {
            head.set(pose.head)
            torso.set(pose.body)
            rightArm.set(pose.rightArm)
            leftArm.set(pose.leftArm)
            rightLeg.set(pose.rightLeg)
            leftLeg.set(pose.leftLeg)
            rightArm.getChild(LOWER).set(pose.rightElbow)
            leftArm.getChild(LOWER).set(pose.leftElbow)
            rightLeg.getChild(LOWER).set(pose.rightKnee)
            leftLeg.getChild(LOWER).set(pose.leftKnee)
        }

        private fun ModelPart.at(x: Float, y: Float) = apply { setPos(x, y, 0f) }

        /**
         * A 12 pixel limb as two 6 pixel halves; the lower one hangs from the joint. A limb's skin is a box unwrap
         * (top and bottom faces above the four sides), so the upper half reads it as is, the lower half's sides start
         * 6 pixels further down, and its bottom face (the hand or the sole) comes from the original spot.
         */
        private fun limb(u: Int, v: Int, outerU: Int, outerV: Int, x: Float, top: Float, width: Int): ModelPart {
            val upper = listOf(Box(u, v, outerU, outerV, x, top, width, HALF, 4))
            val lower = listOf(
                Box(u, v + HALF, outerU, outerV + HALF, x, 0f, width, HALF, 4, faces = SIDES_AND_TOP),
                Box(u, v, outerU, outerV, x, 0f, width, HALF, 4, faces = EnumSet.of(Direction.DOWN)),
            )
            val joint = part(*lower.toTypedArray()).apply { setPos(0f, top + HALF, 0f) }
            return ModelPart(upper.flatMap { it.cubes() }, mapOf(LOWER to joint))
        }

        private fun part(vararg boxes: Box) = ModelPart(boxes.flatMap { it.cubes() }, emptyMap())
    }

    /** One box of the skin: the base layer at [u], [v] and the outer layer, slightly bigger, at [outerU], [outerV]. */
    private class Box(
        val u: Int, val v: Int, val outerU: Int, val outerV: Int,
        val x: Float, val y: Float, val width: Int, val height: Int, val depth: Int,
        val grow: Float = 0.25f,
        val faces: Set<Direction> = ALL_FACES,
    ) {
        fun cubes() = listOf(cube(u, v, 0f), cube(outerU, outerV, grow))

        private fun cube(u: Int, v: Int, grow: Float) = ModelPart.Cube(
            u, v, x, y, -depth / 2f, width.toFloat(), height.toFloat(), depth.toFloat(),
            grow, grow, grow, false, SKIN_SIZE, SKIN_SIZE, faces
        )
    }

    private companion object {
        const val PLAYER_SCALE = 0.9375f
        const val HIP = ActorState.HIP_HEIGHT.toFloat()
        const val SKIN_SIZE = 64f
        const val HALF = 6
        const val LOWER = "lower"
        val ALL_FACES: Set<Direction> = EnumSet.allOf(Direction::class.java)
        val SIDES_AND_TOP: Set<Direction> = EnumSet.complementOf(EnumSet.of(Direction.DOWN))

        fun ModelPart.set(rot: Rot) {
            xRot = rot.x * Mth.DEG_TO_RAD
            yRot = rot.y * Mth.DEG_TO_RAD
            zRot = rot.z * Mth.DEG_TO_RAD
        }
    }
}
