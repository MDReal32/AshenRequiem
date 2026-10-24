package com.mdreal.cinematica.client

import com.mdreal.cinematica.entity.ActorEntity
import com.mdreal.cinematica.timeline.ActorState
import com.mdreal.cinematica.timeline.Pose
import com.mdreal.cinematica.timeline.Rot
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
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
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.EnumSet

/** Draws the actor in the skin of [ActorEntity.skinOwner], posed bone by bone, with arms and legs that bend. */
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
        body.render(poseStack, buffer.getBuffer(RenderType.entityTranslucent(skin.texture())), packedLight)
        poseStack.popPose()
    }

    private fun skin(entity: ActorEntity): PlayerSkin {
        val owner = entity.skinOwner ?: return DefaultPlayerSkin.get(entity.uuid)
        return Minecraft.getInstance().connection?.getPlayerInfo(owner)?.skin ?: DefaultPlayerSkin.get(owner)
    }

    /** The player model: head and torso as vanilla boxes, arms and legs as [Limb]s. */
    private class Body(slim: Boolean) {
        private val armWidth = if (slim) 3 else 4
        private val armY = if (slim) 2.5f else 2f

        private val head = part(0, 0, 32, 0, -4f, -8f, 8, 8, 8, grow = 0.5f)
        private val torso = part(16, 16, 16, 32, -4f, 0f, 8, 12, 4)
        private val rightArm = Limb(-5f, armY, 40, 16, 40, 32, if (slim) -2f else -3f, -2f, armWidth)
        private val leftArm = Limb(5f, armY, 32, 48, 48, 48, -1f, -2f, armWidth)
        private val rightLeg = Limb(-1.9f, 12f, 0, 16, 0, 32, -2f, 0f, 4)
        private val leftLeg = Limb(1.9f, 12f, 16, 48, 0, 48, -2f, 0f, 4)

        fun pose(pose: Pose) {
            head.set(pose.head)
            torso.set(pose.body)
            rightArm.set(pose.rightArm, pose.rightElbow)
            leftArm.set(pose.leftArm, pose.leftElbow)
            rightLeg.set(pose.rightLeg, pose.rightKnee)
            leftLeg.set(pose.leftLeg, pose.leftKnee)
        }

        fun render(poseStack: PoseStack, consumer: VertexConsumer, light: Int) {
            head.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY)
            torso.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY)
            listOf(rightArm, leftArm, rightLeg, leftLeg).forEach { it.render(poseStack, consumer, light) }
        }

        /** A vanilla box with the skin's base layer at [u], [v] and the outer layer, a bit bigger, at [outerU], [outerV]. */
        private fun part(u: Int, v: Int, outerU: Int, outerV: Int, x: Float, y: Float, w: Int, h: Int, d: Int, grow: Float = 0.25f) =
            ModelPart(listOf(cube(u, v, x, y, w, h, d, 0f), cube(outerU, outerV, x, y, w, h, d, grow)), emptyMap())

        private fun cube(u: Int, v: Int, x: Float, y: Float, w: Int, h: Int, d: Int, grow: Float) = ModelPart.Cube(
            u, v, x, y, -d / 2f, w.toFloat(), h.toFloat(), d.toFloat(), grow, grow, grow, false, SKIN_SIZE, SKIN_SIZE,
            EnumSet.allOf(Direction::class.java)
        )

        private fun ModelPart.set(rot: Rot) {
            xRot = rot.x * Mth.DEG_TO_RAD
            yRot = rot.y * Mth.DEG_TO_RAD
            zRot = rot.z * Mth.DEG_TO_RAD
        }
    }

    /**
     * An arm or leg that bends in the middle: one 12 pixel box drawn as three rings of vertices. The top ring follows
     * the shoulder or hip, the bottom ring is turned by the elbow or knee, and the ring at the joint by half of it, so
     * the skin stretches over the bend like a sleeve instead of splitting into two blocks. Faces and texture
     * coordinates follow vanilla's box unwrap, so a straight limb looks exactly like the player's.
     */
    private class Limb(
        private val pivotX: Float, private val pivotY: Float,
        private val u: Int, private val v: Int, private val outerU: Int, private val outerV: Int,
        private val x: Float, private val top: Float, private val width: Int,
    ) {
        private val rotation = Quaternionf()
        private val bend = Quaternionf()
        private val halfBend = Quaternionf()
        private val joint = Vector3f(x + width / 2f, top + LENGTH / 2f, 0f)

        fun set(limb: Rot, joint: Rot) {
            rotation.rotationZYX(limb.z * Mth.DEG_TO_RAD, limb.y * Mth.DEG_TO_RAD, limb.x * Mth.DEG_TO_RAD)
            bend.rotationZYX(joint.z * Mth.DEG_TO_RAD, joint.y * Mth.DEG_TO_RAD, joint.x * Mth.DEG_TO_RAD)
            halfBend.identity().slerp(bend, 0.5f)
        }

        fun render(poseStack: PoseStack, consumer: VertexConsumer, light: Int) {
            poseStack.pushPose()
            poseStack.translate(pivotX / 16f, pivotY / 16f, 0f)
            poseStack.mulPose(rotation)
            val face = Face(poseStack.last(), consumer, light)
            box(face, u, v, 0f)
            box(face, outerU, outerV, 0.25f)
            poseStack.popPose()
        }

        private fun box(face: Face, u: Int, v: Int, grow: Float) {
            val x0 = x - grow
            val x1 = x + width + grow
            val y0 = top - grow
            val y1 = top + LENGTH + grow
            val z0 = -DEPTH / 2f - grow
            val z1 = DEPTH / 2f + grow
            // Corners named as in vanilla's ModelPart.Cube; y0 is the shoulder or hip end.
            val c7 = Vector3f(x0, y0, z0)
            val c0 = Vector3f(x1, y0, z0)
            val c1 = Vector3f(x1, y1, z0)
            val c2 = Vector3f(x0, y1, z0)
            val c3 = Vector3f(x0, y0, z1)
            val c4 = Vector3f(x1, y0, z1)
            val c5 = Vector3f(x1, y1, z1)
            val c6 = Vector3f(x0, y1, z1)
            val f4 = u.toFloat()
            val f5 = f4 + DEPTH
            val f6 = f5 + width
            val f7 = f6 + width
            val f8 = f6 + DEPTH
            val f9 = f8 + width
            val f10 = v.toFloat()
            val f11 = f10 + DEPTH
            val f12 = f11 + LENGTH

            face.cap(arrayOf(c4, c3, c7, c0), f5, f10, f6, f11, TOP, Vector3f(0f, -1f, 0f))
            face.cap(arrayOf(c1, c2, c6, c5), f6, f11, f7, f10, BOTTOM, Vector3f(0f, 1f, 0f))
            face.side(arrayOf(c7, c3, c6, c2), f4, f11, f5, f12, Vector3f(-1f, 0f, 0f))
            face.side(arrayOf(c0, c7, c2, c1), f5, f11, f6, f12, Vector3f(0f, 0f, -1f))
            face.side(arrayOf(c4, c0, c1, c5), f6, f11, f8, f12, Vector3f(1f, 0f, 0f))
            face.side(arrayOf(c3, c4, c5, c6), f8, f11, f9, f12, Vector3f(0f, 0f, 1f))
        }

        private inner class Face(val pose: PoseStack.Pose, val consumer: VertexConsumer, val light: Int) {
            /** A face at one end. Vertex UVs go (u2,v1), (u1,v1), (u1,v2), (u2,v2) like vanilla's polygons. */
            fun cap(corners: Array<Vector3f>, u1: Float, v1: Float, u2: Float, v2: Float, ring: Int, normal: Vector3f) {
                val us = floatArrayOf(u2, u1, u1, u2)
                val vs = floatArrayOf(v1, v1, v2, v2)
                quad(corners, IntArray(4) { ring }, us, vs, normal, if (ring == TOP) null else bend)
            }

            /** A long side, cut at the joint into an upper and a lower quad that share the joint's vertices. */
            fun side(corners: Array<Vector3f>, u1: Float, v1: Float, u2: Float, v2: Float, normal: Vector3f) {
                val mid0 = Vector3f(corners[0].x, joint.y, corners[0].z)
                val mid1 = Vector3f(corners[1].x, joint.y, corners[1].z)
                val vm = v1 + LENGTH / 2f
                val us = floatArrayOf(u2, u1, u1, u2)
                quad(arrayOf(corners[0], corners[1], mid1, mid0), intArrayOf(TOP, TOP, MID, MID), us, floatArrayOf(v1, v1, vm, vm), normal, halfBend)
                quad(arrayOf(mid0, mid1, corners[2], corners[3]), intArrayOf(MID, MID, BOTTOM, BOTTOM), us, floatArrayOf(vm, vm, v2, v2), normal, halfBend)
            }

            private fun quad(corners: Array<Vector3f>, rings: IntArray, us: FloatArray, vs: FloatArray, normal: Vector3f, turn: Quaternionf?) {
                val n = pose.transformNormal(Vector3f(normal).also { turn?.transform(it) }, Vector3f())
                for (i in 0..3) {
                    val p = bent(corners[i], rings[i])
                    val world = pose.pose().transformPosition(p.x / 16f, p.y / 16f, p.z / 16f, Vector3f())
                    consumer.addVertex(world.x, world.y, world.z, -1, us[i] / SKIN_SIZE, vs[i] / SKIN_SIZE, OverlayTexture.NO_OVERLAY, light, n.x, n.y, n.z)
                }
            }

            private fun bent(point: Vector3f, ring: Int): Vector3f = when (ring) {
                TOP -> point
                else -> Vector3f(point).sub(joint).also { (if (ring == MID) halfBend else bend).transform(it) }.add(joint)
            }
        }

        private companion object {
            const val LENGTH = 12
            const val DEPTH = 4
            const val TOP = 0
            const val MID = 1
            const val BOTTOM = 2
        }
    }

    private companion object {
        const val PLAYER_SCALE = 0.9375f
        const val HIP = ActorState.HIP_HEIGHT.toFloat()
        const val SKIN_SIZE = 64f
    }
}
