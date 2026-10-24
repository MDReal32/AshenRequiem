package com.mdreal.cinematica.client

import com.mdreal.cinematica.entity.ActorEntity
import com.mdreal.cinematica.entity.ModEntities
import com.mdreal.cinematica.timeline.ActorState
import com.mdreal.cinematica.timeline.Vec
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity

/** The one client-side actor. Re-created whenever the level changes (dimension change, respawn, rejoin). */
object Stage {
    /** Negative so it never clashes with a server entity id. */
    private const val ACTOR_ID = -0x0C1A

    private var actor: ActorEntity? = null

    fun show(state: ActorState, origin: Vec, hidden: Boolean = false) {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val entity = actor?.takeIf { it.level() === level && !it.isRemoved } ?: run {
            val created = ModEntities.ACTOR.get().create(level) ?: return
            created.id = ACTOR_ID
            created.skinOwner = mc.player?.uuid
            level.addEntity(created)
            actor = created
            created
        }
        entity.apply(state, origin)
        entity.hidden = hidden
    }

    fun remove() {
        val entity = actor ?: return
        actor = null
        val level = Minecraft.getInstance().level
        if (level != null && entity.level() === level && !entity.isRemoved) level.removeEntity(entity.id, Entity.RemovalReason.DISCARDED)
    }
}
