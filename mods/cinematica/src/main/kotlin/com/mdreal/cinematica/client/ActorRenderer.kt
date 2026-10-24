package com.mdreal.cinematica.client

import com.mdreal.cinematica.entity.ActorEntity
import com.mdreal.cinematica.timeline.Pose
import com.mdreal.cinematica.timeline.Rot
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity

/** Draws the actor as a player model in the skin of [ActorEntity.skinOwner], posed bone by bone. */
class ActorRenderer(context: EntityRendererProvider.Context) : EntityRenderer<ActorEntity>(context) {
    private val wide = PlayerModel<LivingEntity>(context.bakeLayer(ModelLayers.PLAYER), false)
    private val slim = PlayerModel<LivingEntity>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true)

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
        val model = if (skin.model() == PlayerSkin.Model.SLIM) slim else wide
        model.young = false
        model.riding = false
        model.crouching = false
        pose(model, entity.pose)

        poseStack.pushPose()
        // Same transforms as LivingEntityRenderer, plus the root tilt around the feet.
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - entity.bodyYaw))
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.root.x))
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.root.y))
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.root.z))
        poseStack.scale(-1f, -1f, 1f)
        poseStack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE)
        poseStack.translate(0f, -1.501f, 0f)
        model.renderToBuffer(poseStack, buffer.getBuffer(model.renderType(skin.texture())), packedLight, OverlayTexture.NO_OVERLAY)
        poseStack.popPose()
    }

    private fun skin(entity: ActorEntity): PlayerSkin {
        val owner = entity.skinOwner ?: return DefaultPlayerSkin.get(entity.uuid)
        return Minecraft.getInstance().connection?.getPlayerInfo(owner)?.skin ?: DefaultPlayerSkin.get(owner)
    }

    private fun pose(model: PlayerModel<*>, pose: Pose) {
        model.head.set(pose.head)
        model.body.set(pose.body)
        model.rightArm.set(pose.rightArm)
        model.leftArm.set(pose.leftArm)
        model.rightLeg.set(pose.rightLeg)
        model.leftLeg.set(pose.leftLeg)
        model.hat.copyFrom(model.head)
        model.jacket.copyFrom(model.body)
        model.rightSleeve.copyFrom(model.rightArm)
        model.leftSleeve.copyFrom(model.leftArm)
        model.rightPants.copyFrom(model.rightLeg)
        model.leftPants.copyFrom(model.leftLeg)
    }

    private fun ModelPart.set(rot: Rot) {
        xRot = rot.x * Mth.DEG_TO_RAD
        yRot = rot.y * Mth.DEG_TO_RAD
        zRot = rot.z * Mth.DEG_TO_RAD
    }

    private companion object {
        const val PLAYER_SCALE = 0.9375f
    }
}
