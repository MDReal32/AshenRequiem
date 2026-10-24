package com.mdreal.cinematica.entity

import com.mdreal.cinematica.timeline.ActorState
import com.mdreal.cinematica.timeline.Pose
import com.mdreal.cinematica.timeline.Rot
import com.mdreal.cinematica.timeline.Vec
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import java.util.UUID

/**
 * The stand-in that wears a player's skin during a scene. It only ever lives in the client's level (v0.1): the client
 * spawns it, drives it every frame from the timeline and removes it at the end. It never ticks or saves.
 */
class ActorEntity(type: EntityType<out ActorEntity>, level: Level) : Entity(type, level) {
    /** Whose skin to wear. Kept apart from [getUUID] so the entity never collides with the real player. */
    var skinOwner: UUID? = null
    var pose: Pose = Pose.STANDING
    var root: Rot = Rot.ZERO

    /** Body yaw for rendering; the entity's own yRot is left alone so nothing vanilla turns it. */
    var bodyYaw = 0f

    /** Fully hidden when the camera is about to enter its head. */
    var hidden = false

    init {
        noPhysics = true
        noCulling = true
    }

    /** Places the actor exactly: no interpolation from the previous frame. */
    fun apply(state: ActorState, origin: Vec) {
        val x = origin.x + state.pos.x
        val y = origin.y + state.pos.y
        val z = origin.z + state.pos.z
        setPos(x, y, z)
        xo = x
        yo = y
        zo = z
        xOld = x
        yOld = y
        zOld = z
        bodyYaw = state.yaw
        pose = state.pose
        root = state.root
    }

    override fun tick() {}

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {}

    override fun readAdditionalSaveData(tag: CompoundTag) {}

    override fun addAdditionalSaveData(tag: CompoundTag) {}

    override fun isPickable() = false

    override fun hurt(source: DamageSource, amount: Float) = false

    override fun shouldBeSaved() = false
}
