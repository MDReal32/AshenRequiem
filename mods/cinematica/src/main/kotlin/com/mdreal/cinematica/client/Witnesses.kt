package com.mdreal.cinematica.client

import com.mdreal.cinematica.network.WitnessPayload
import com.mdreal.cinematica.timeline.ActorState
import com.mdreal.cinematica.timeline.Pose
import com.mdreal.cinematica.timeline.Rot
import com.mdreal.cinematica.timeline.Timeline
import com.mdreal.cinematica.timeline.Vec
import net.minecraft.client.Minecraft
import java.util.UUID
import kotlin.math.min

/**
 * Other players' scenes as seen from outside: while someone nearby watches a cutscene their real body is an
 * invisible spectator, so their actor plays the scene here instead. No camera, letterbox or fade; the actor goes
 * away when they reappear.
 */
object Witnesses {
    /** Client ticks past the scene's end before an actor is dropped even if the server never said so. */
    private const val GIVE_UP = 260

    private class Show(val owner: UUID, val timeline: Timeline, val origin: Vec, val from: Int) {
        val puppet = Puppet(idOf(owner)) { owner }
        var ticks = 0
    }

    private val shows = HashMap<UUID, Show>()

    fun start(payload: WitnessPayload) {
        end(payload.owner)
        val start = ActorState(payload.start, payload.yaw, Pose(head = Rot(payload.pitch, 0f, 0f)), Rot.ZERO)
        shows[payload.owner] = Show(payload.owner, Timeline(payload.scene, start), payload.origin, payload.time)
    }

    fun end(owner: UUID) {
        shows.remove(owner)?.puppet?.remove()
    }

    fun clear() {
        shows.values.forEach { it.puppet.remove() }
        shows.clear()
    }

    fun tick() {
        if (shows.isEmpty() || Minecraft.getInstance().isPaused) return
        shows.values.forEach { it.ticks++ }
        shows.values.filter { it.from + it.ticks > it.timeline.scene.end + GIVE_UP }.forEach { end(it.owner) }
    }

    fun frame(partialTick: Float) {
        shows.values.forEach { show ->
            val scene = show.timeline.scene
            // After the action the actor holds its last pose until the server says the real player is back.
            val time = min(show.from + show.ticks + partialTick.toDouble(), scene.length.toDouble())
            show.puppet.show(show.timeline.actor(time), show.origin)
        }
    }

    /** A negative id per player, away from the local [Stage] actor's. */
    private fun idOf(owner: UUID) = -0x10000 - (owner.hashCode() and 0x7FFFFFF)
}
