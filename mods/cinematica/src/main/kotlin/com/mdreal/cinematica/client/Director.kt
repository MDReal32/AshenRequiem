package com.mdreal.cinematica.client

import com.mdreal.cinematica.entity.ActorEntity
import com.mdreal.cinematica.entity.ModEntities
import com.mdreal.cinematica.network.FinishedPayload
import com.mdreal.cinematica.timeline.ActorState
import com.mdreal.cinematica.timeline.CameraState
import com.mdreal.cinematica.timeline.Timeline
import com.mdreal.cinematica.timeline.Vec
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.neoforged.neoforge.client.event.ViewportEvent
import net.neoforged.neoforge.network.PacketDistributor
import kotlin.math.min

/**
 * Runs a timeline on the client: the camera, the actor, letterbox and fade. Used for real playback (the server ends
 * it) and for the recorder's preview (ends by itself).
 */
object Director {
    /** Client ticks past the end before playback gives up waiting for the server. */
    private const val GIVE_UP = 260

    /** The actor disappears for this last part of the handoff, so the camera never sees the inside of its head. */
    private const val HIDE_AT = 0.7

    private class Run(val timeline: Timeline, val origin: Vec, val from: Double, val preview: Boolean, val showActor: Boolean)

    private var run: Run? = null
    private var ticks = 0
    private var finishedSent = false
    private var camera: ActorEntity? = null
    private var cameraState: CameraState? = null
    private var savedCameraType: CameraType? = null

    val active get() = run != null
    val isPreview get() = run?.preview == true

    fun play(timeline: Timeline, origin: Vec, from: Double = 0.0, preview: Boolean = false, showActor: Boolean = true) {
        stop()
        run = Run(timeline, origin, from, preview, showActor)
        savedCameraType = Minecraft.getInstance().options.cameraType
    }

    fun stop() {
        if (run == null) return
        run = null
        ticks = 0
        finishedSent = false
        cameraState = null
        camera = null
        val mc = Minecraft.getInstance()
        mc.player?.let { if (mc.cameraEntity !== it) mc.setCameraEntity(it) }
        savedCameraType?.let { mc.options.cameraType = it }
        savedCameraType = null
        if (!Recorder.active) Stage.remove()
    }

    private fun time(partialTick: Float) = run!!.let { min(it.from + ticks + partialTick, it.timeline.scene.end.toDouble()) }

    fun tick() {
        val run = run ?: return
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.isPaused) return
        ticks++
        val time = run.from + ticks
        val end = run.timeline.scene.end
        if (time < end) return
        if (run.preview) return stop()
        if (!finishedSent) {
            finishedSent = true
            PacketDistributor.sendToServer(FinishedPayload)
        }
        // Normally the server answers with EndPayload long before this.
        if (time >= end + GIVE_UP) stop()
    }

    /** Called before every frame: places the camera and the actor exactly for this frame's time. */
    fun frame(partialTick: Float) {
        val run = run ?: return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val time = time(partialTick)
        val scene = run.timeline.scene

        val state = run.timeline.camera(time)
        cameraState = state
        if (state != null) {
            val entity = camera?.takeIf { it.level() === level } ?: ModEntities.ACTOR.get().create(level)?.also { camera = it }
            if (entity != null) {
                // The camera entity has the player's eye height, so switching to it and back never dips the view.
                entity.apply(ActorState(state.pos - Vec(0.0, ActorState.EYE_HEIGHT, 0.0), state.yaw, entity.pose, entity.root), run.origin)
                entity.yRot = state.yaw
                entity.yRotO = state.yaw
                entity.xRot = state.pitch
                entity.xRotO = state.pitch
                if (mc.cameraEntity !== entity) mc.setCameraEntity(entity)
                if (!mc.options.cameraType.isFirstPerson) mc.options.cameraType = CameraType.FIRST_PERSON
            }
        }

        if (run.showActor) {
            val handoff = if (scene.handoff > 0) (time - scene.length) / scene.handoff else 0.0
            Stage.show(run.timeline.actor(time), run.origin, hidden = state != null && handoff > HIDE_AT)
        } else if (!Recorder.active) {
            Stage.remove()
        }
    }

    fun angles(event: ViewportEvent.ComputeCameraAngles) {
        cameraState?.let { event.roll = it.roll }
    }

    fun fov(event: ViewportEvent.ComputeFov) {
        cameraState?.fov?.let { event.fov = it.toDouble() }
    }

    /** Letterbox bars slide in at the start and out during the handoff; fade paints black over everything. */
    fun overlay(graphics: GuiGraphics, partialTick: Float) {
        val run = run ?: return
        val time = time(partialTick)
        val scene = run.timeline.scene
        val width = graphics.guiWidth()
        val height = graphics.guiHeight()
        if (scene.letterbox) {
            val enter = ((time - run.from) / 10.0).coerceIn(0.0, 1.0)
            val leave = if (scene.handoff > 0) 1 - ((time - scene.length) / scene.handoff).coerceIn(0.0, 1.0) else if (time >= scene.length) 0.0 else 1.0
            val bar = (height * 0.11 * min(enter, leave)).toInt()
            if (bar > 0) {
                graphics.fill(0, 0, width, bar, BLACK)
                graphics.fill(0, height - bar, width, height, BLACK)
            }
        }
        val alpha = (run.timeline.fade(time) * 255).toInt().coerceIn(0, 255)
        if (alpha > 0) graphics.fill(0, 0, width, height, alpha shl 24)
    }

    private const val BLACK = 0xFF000000.toInt()
}
