package com.mdreal.cinematica.client

import com.mdreal.cinematica.entity.ActorEntity
import com.mdreal.cinematica.entity.ModEntities
import com.mdreal.cinematica.timeline.ActorState
import com.mdreal.cinematica.timeline.Vec
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import java.util.UUID

/**
 * A client-only actor wearing [skinOwner]'s skin under a fixed negative [id], so it never clashes with a server
 * entity. Re-created whenever the level changes (dimension change, respawn, rejoin).
 */
class Puppet(private val id: Int, private val skinOwner: () -> UUID?) {
    private var actor: ActorEntity? = null

    fun show(state: ActorState, origin: Vec, hidden: Boolean = false) {
        val level = Minecraft.getInstance().level ?: return
        val entity = actor?.takeIf { it.level() === level && !it.isRemoved } ?: run {
            val created = ModEntities.ACTOR.get().create(level) ?: return
            created.id = id
            created.skinOwner = skinOwner()
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

/** The local player's own actor, used by playback and the recorder. */
object Stage {
    private val puppet = Puppet(-0x0C1A) { Minecraft.getInstance().player?.uuid }

    fun show(state: ActorState, origin: Vec, hidden: Boolean = false) = puppet.show(state, origin, hidden)

    fun remove() = puppet.remove()
}
