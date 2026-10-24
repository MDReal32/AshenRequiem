package com.mdreal.cinematica.client

import com.mdreal.cinematica.entity.ActorEntity
import com.mdreal.cinematica.entity.ModEntities
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mdreal.cinematica.client.Keys.label
import com.mdreal.cinematica.client.Keys.presses
import com.mdreal.cinematica.network.ControlPayload
import com.mdreal.cinematica.network.FinishedPayload
import com.mdreal.cinematica.timeline.ActorState
import com.mdreal.cinematica.timeline.CameraState
import com.mdreal.cinematica.timeline.Rot
import com.mdreal.cinematica.timeline.Timeline
import com.mdreal.cinematica.timeline.Vec
import com.mojang.logging.LogUtils
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.FogRenderer
import net.minecraft.world.level.material.FogType
import net.minecraft.client.gui.screens.DeathScreen
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.GenericMessageScreen
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.ProgressScreen
import net.minecraft.client.gui.screens.ReceivingLevelScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.client.event.ViewportEvent
import net.neoforged.neoforge.network.PacketDistributor
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Runs a timeline on the client: the camera, the actor, letterbox and fade. Used for real playback (the server ends
 * it) and for the recorder's preview (ends by itself).
 */
object Director {
    private val LOGGER = LogUtils.getLogger()

    /** Client ticks past the end before playback gives up waiting for the server. */
    private const val GIVE_UP = 260

    /** During the handoff the actor disappears once the camera is this close to its eyes, before entering its head. */
    private const val HIDE_WITHIN = 0.6

    /** The control bar shows this long after a control key or a mouse move, and always while paused. */
    private const val SHOW_TICKS = 60

    /** At the start it stays longer, until the picture is past the usual fade in from black. */
    private const val SHOW_AT_START = 140

    /** Ticks the control bar takes to fade away. */
    private const val FADE_TICKS = 10

    /** How long the big play or pause sign stays in the middle after toggling. */
    private const val FLASH_TICKS = 14

    /** Played part of the progress track. */
    private const val ACCENT = 0xE0703A

    /** The progress track's distance from the screen's sides. */
    private const val MARGIN = 10

    /** Ticks a step key moves; with Shift, while paused, one. */
    const val STEP = 20

    /** How long the "snapshot saved" note stays on screen. */
    private const val NOTE_TICKS = 50

    private class Run(
        val name: String,
        val timeline: Timeline,
        val origin: Vec,
        val from: Double,
        val preview: Boolean,
        val showActor: Boolean,
    )

    private var run: Run? = null
    private var ticks = 0
    private var paused = false
    private var note: Component? = null
    private var noteTicks = 0
    private var showTicks = 0
    private var flashTicks = 0
    private var mouseX = 0.0
    private var mouseY = 0.0

    /** A snapshot waits for one frame drawn without the control bar, then grabs it. */
    private var shotPending = false
    private var lastTime = 0.0

    /** Paused because another screen opened over the scene; it plays on once that closes. */
    private var heldByScreen = false
    private var finishedSent = false
    private var camera: ActorEntity? = null
    private var cameraState: CameraState? = null
    private var savedCameraType: CameraType? = null

    val active get() = run != null
    val isPreview get() = run?.preview == true

    fun play(name: String, timeline: Timeline, origin: Vec, from: Double = 0.0, preview: Boolean = false, showActor: Boolean = true) {
        stop()
        run = Run(name, timeline, origin, from, preview, showActor)
        val mc = Minecraft.getInstance()
        savedCameraType = mc.options.cameraType
        showTicks = SHOW_AT_START
        mouseX = mc.mouseHandler.xpos()
        mouseY = mc.mouseHandler.ypos()
    }

    fun stop() {
        if (run == null) return
        run = null
        ticks = 0
        paused = false
        note = null
        showTicks = 0
        flashTicks = 0
        shotPending = false
        heldByScreen = false
        finishedSent = false
        cameraState = null
        camera = null
        val mc = Minecraft.getInstance()
        mc.player?.let { if (mc.cameraEntity !== it) mc.setCameraEntity(it) }
        savedCameraType?.let { mc.options.cameraType = it }
        savedCameraType = null
        if (mc.screen is PlayerScreen) mc.setScreen(null)
        if (!Recorder.active) Stage.remove()
    }

    private fun time(partialTick: Float) =
        run!!.let { min(it.from + ticks + if (paused) 0f else partialTick, it.timeline.scene.end.toDouble()) }

    fun tick() {
        val run = run ?: return
        val mc = Minecraft.getInstance()
        if (mc.level == null) return
        // The pause menu, or a screen it leads to, holds the scene until the player is back.
        val away = mc.screen != null && mc.screen !is PlayerScreen
        if (away && !paused && !run.preview) {
            setPaused(run, true)
            heldByScreen = true
        } else if (!away && heldByScreen) {
            heldByScreen = false
            if (paused) setPaused(run, false)
        }
        if (mc.isPaused) return
        if (noteTicks > 0 && --noteTicks == 0) note = null
        if (showTicks > 0) showTicks--
        if (flashTicks > 0) flashTicks--
        controls(run)
        if (paused) return
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

    /**
     * The playback keys while the scene plays. Once paused, [PlayerScreen] is open and takes the keys and the mouse
     * itself.
     */
    private fun controls(run: Run) {
        // Like a video player, moving the mouse brings the controls up.
        val mouse = Minecraft.getInstance().mouseHandler
        if (abs(mouse.xpos() - mouseX) + abs(mouse.ypos() - mouseY) > 4) reveal()
        mouseX = mouse.xpos()
        mouseY = mouse.ypos()
        if (run.preview) return
        if (Keys.PLAY_PAUSE.presses() % 2 == 1) togglePause()
        val delta = (Keys.STEP_FORWARD.presses() - Keys.STEP_BACK.presses()) * STEP
        if (delta != 0) step(delta)
        if (Keys.SNAPSHOT.presses() > 0) snapshot()
    }

    fun togglePause() {
        val run = run ?: return
        setPaused(run, !paused)
        flashTicks = FLASH_TICKS
    }

    /** Moves [delta] ticks from where the scene is and pauses there, so the moment can be looked at. */
    fun step(delta: Int) {
        val run = run ?: return
        seek(run.from.toInt() + ticks + delta)
    }

    /** Jumps to scene tick [tick] and pauses there. */
    fun seek(tick: Int) {
        val run = run ?: return
        ticks = (tick - run.from.toInt()).coerceIn(0, run.timeline.scene.end - run.from.toInt() - 1)
        setPaused(run, true)
    }

    /** Saves this moment once the next frame is drawn. */
    fun snapshot() {
        if (run == null) return
        shotPending = true
        reveal()
    }

    /** The server's clock follows, so it keeps the right chunks loaded; paused, the mouse is let go for the bar. */
    private fun setPaused(run: Run, paused: Boolean) {
        this.paused = paused
        reveal()
        if (run.preview) return
        PacketDistributor.sendToServer(ControlPayload(run.from.toInt() + ticks, paused))
        val mc = Minecraft.getInstance()
        if (paused && mc.screen == null) mc.setScreen(PlayerScreen())
        if (!paused && mc.screen is PlayerScreen) mc.setScreen(null)
    }

    /**
     * While a scene plays only Escape's pause menu may open over it (and what that menu leads to), no chat, inventory
     * or map. Screens the game itself shows, such as a lost connection, always may.
     */
    fun allowsScreen(current: Screen?, next: Screen?): Boolean {
        val run = run ?: return true
        if (run.preview || current != null || next == null) return true
        return next is PauseScreen || next is PlayerScreen || next is DisconnectedScreen || next is DeathScreen ||
            next is ProgressScreen || next is GenericMessageScreen || next is ReceivingLevelScreen
    }

    private fun reveal() {
        showTicks = max(showTicks, SHOW_TICKS)
    }

    /** Called after every frame. A pending snapshot grabs this frame, which was drawn without the control bar. */
    fun afterFrame() {
        val run = run ?: return
        if (!shotPending) return
        shotPending = false
        saveSnapshot(run, lastTime)
    }

    /**
     * Saves where the scene is right now, so it can be talked about exactly: a screenshot in `screenshots/` and the
     * camera and actor state as JSON in `cinematica/snapshots/`, both named after the scene and tick.
     */
    private fun saveSnapshot(run: Run, time: Double) {
        val mc = Minecraft.getInstance()
        val tick = time.toInt()
        val base = "${run.name.replace(Regex("[^a-z0-9_-]"), "_")}_t$tick"
        Screenshot.grab(mc.gameDirectory, "cinematica_$base.png", mc.mainRenderTarget) { }
        val camera = run.timeline.camera(time)
        val actor = run.timeline.actor(time)
        fun vec(v: Vec) = JsonArray().apply { add(v.x); add(v.y); add(v.z) }
        fun rot(r: Rot) = JsonArray().apply { add(r.x); add(r.y); add(r.z) }
        val json = JsonObject().apply {
            addProperty("scene", run.name)
            addProperty("tick", tick)
            addProperty("seconds", tick / 20.0)
            addProperty("paused", paused)
            addProperty("screenshot", "screenshots/cinematica_$base.png")
            if (camera != null) add("camera", JsonObject().apply {
                add("pos", vec(camera.pos))
                addProperty("yaw", camera.yaw)
                addProperty("pitch", camera.pitch)
                addProperty("roll", camera.roll)
            })
            add("actor", JsonObject().apply {
                add("pos", vec(actor.pos))
                addProperty("yaw", actor.yaw)
                add("root", rot(actor.root))
                val pose = actor.pose
                add("pose", JsonObject().apply {
                    listOf(
                        "head" to pose.head, "body" to pose.body, "right_arm" to pose.rightArm, "left_arm" to pose.leftArm,
                        "right_leg" to pose.rightLeg, "left_leg" to pose.leftLeg, "right_elbow" to pose.rightElbow,
                        "left_elbow" to pose.leftElbow, "right_knee" to pose.rightKnee, "left_knee" to pose.leftKnee,
                    ).forEach { (name, r) -> add(name, rot(r)) }
                })
            })
        }
        val file = mc.gameDirectory.toPath().resolve("cinematica/snapshots/$base.json")
        runCatching {
            Files.createDirectories(file.parent)
            Files.writeString(file, GsonBuilder().setPrettyPrinting().create().toJson(json))
        }.onFailure { LOGGER.error("Cinematica Studio: can't write snapshot {}", file, it) }
        note = Component.translatable("cinematica_studio.player.snapshot", base, "%.2f".format(tick / 20.0), tick)
        noteTicks = NOTE_TICKS
    }

    /** Called before every frame: places the camera and the actor exactly for this frame's time. */
    fun frame(partialTick: Float) {
        val run = run ?: return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val time = time(partialTick)
        lastTime = time
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
            val actor = run.timeline.actor(time)
            // Only during the handoff: close-ups elsewhere may come near the head on purpose.
            val handoff = state != null && time > scene.length && actor.eye().pos.distanceTo(state.pos) < HIDE_WITHIN
            Stage.show(actor, run.origin, hidden = handoff)
        } else if (!Recorder.active) {
            Stage.remove()
        }
    }

    fun angles(event: ViewportEvent.ComputeCameraAngles) {
        cameraState?.let { event.roll = it.roll }
    }

    /**
     * Pushes the terrain fog out (or in) by the scene's fog track, on top of whatever the world set, so a camera far
     * from the player can see the land. Never past the render distance, where there is nothing left to show.
     */
    fun fog(event: ViewportEvent.RenderFog) {
        val run = run ?: return
        if (event.mode != FogRenderer.FogMode.FOG_TERRAIN || event.type != FogType.NONE) return
        val scale = run.timeline.fog(lastTime) ?: return
        val far = min(event.farPlaneDistance * scale, Minecraft.getInstance().gameRenderer.renderDistance)
        event.nearPlaneDistance = min(event.nearPlaneDistance * scale, far * 0.8f)
        event.farPlaneDistance = far
        event.isCanceled = true
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
        if (!run.preview && !shotPending) controlsHud(graphics, run, time, partialTick, width, height)
    }

    /** A video player's bar along the bottom: progress, play state, time, the keys; and the big sign after a toggle. */
    private fun controlsHud(graphics: GuiGraphics, run: Run, time: Double, partialTick: Float, width: Int, height: Int) {
        val font = Minecraft.getInstance().font
        note?.let { graphics.drawString(font, it, 10, height - 58, 0xFFFFFFFF.toInt()) }
        if (flashTicks > 0) {
            val alpha = ((flashTicks - partialTick) / FLASH_TICKS).coerceIn(0f, 1f)
            val x = width / 2
            val y = height / 2
            graphics.fill(x - 20, y - 20, x + 20, y + 20, color(0, 0.45f * alpha))
            icon(graphics, x - 9, y - 11, 22, play = !paused, color(0xFFFFFF, alpha))
        }

        val shown = if (paused) 1f else ((showTicks - partialTick) / FADE_TICKS).coerceIn(0f, 1f)
        if (shown < 0.05f) return
        fun c(rgb: Int, alpha: Float = 1f) = color(rgb, alpha * shown)

        graphics.fillGradient(0, height - 48, width, height, c(0, 0f), c(0, 0.75f))
        val scene = run.timeline.scene
        val end = scene.end.toDouble()
        val left = MARGIN
        val right = width - MARGIN
        val y = trackY(height)
        fun at(t: Double) = trackX(t, end, width)
        graphics.fill(left, y, right, y + 3, c(0xFFFFFF, 0.3f))
        val head = at(time)
        graphics.fill(left, y, head, y + 3, c(ACCENT))
        // Where the scene ends and the camera starts gliding into the player's eyes.
        if (scene.handoff > 0) at(scene.length.toDouble()).let { graphics.fill(it, y - 2, it + 1, y + 5, c(0xFFFFFF, 0.7f)) }
        graphics.fill(head - 2, y - 2, head + 3, y + 5, c(0xFFFFFF))
        graphics.fill(head - 3, y - 1, head + 4, y + 4, c(0xFFFFFF))

        val row = height - 14
        icon(graphics, left, row - 1, 9, play = paused, c(0xFFFFFF))
        var x = left + 16
        val clock = "${clock(time)} / ${clock(end)}"
        graphics.drawString(font, clock, x, row, c(0xFFFFFF))
        x += font.width(clock) + 10
        val info = "tick ${time.toInt()}   ${run.name}"
        graphics.drawString(font, info, x, row, c(0xAAAAAA))
        x += font.width(info)
        val hint = Component.translatable(
            if (paused) "cinematica_studio.player.hint_paused" else "cinematica_studio.player.hint",
            Keys.PLAY_PAUSE.label(), Keys.STEP_BACK.label(), Keys.STEP_FORWARD.label(), Keys.SNAPSHOT.label()
        )
        val hintX = right - font.width(hint)
        graphics.drawString(font, hint, hintX, if (hintX > x + 12) row else y - 13, c(0xAAAAAA))
    }

    private fun trackY(height: Int) = height - 25

    private fun trackX(time: Double, end: Double, width: Int) =
        MARGIN + ((width - 2 * MARGIN) * (time / end).coerceIn(0.0, 1.0)).toInt()

    /** Whether a point on screen is on the progress track, with some room around it for the mouse. */
    fun overTrack(x: Double, y: Double, width: Int, height: Int) =
        x in MARGIN - 4.0..width - MARGIN + 4.0 && y in trackY(height) - 6.0..trackY(height) + 9.0

    /** The scene tick under [x] on the progress track. */
    fun tickAt(x: Double, width: Int): Int {
        val end = run?.timeline?.scene?.end ?: return 0
        return ((x - MARGIN) / (width - 2 * MARGIN) * end).roundToInt().coerceIn(0, end)
    }

    /** While the mouse is over the track: a line where it points and the time there. */
    fun hover(graphics: GuiGraphics, mouseX: Int, mouseY: Int, width: Int, height: Int) {
        val run = run ?: return
        if (shotPending || !overTrack(mouseX.toDouble(), mouseY.toDouble(), width, height)) return
        val font = Minecraft.getInstance().font
        val tick = tickAt(mouseX.toDouble(), width)
        val x = trackX(tick.toDouble(), run.timeline.scene.end.toDouble(), width)
        val y = trackY(height)
        graphics.fill(x, y - 3, x + 1, y + 6, 0xFFFFFFFF.toInt())
        val label = "${clock(tick.toDouble())}  tick $tick"
        val labelX = (x - font.width(label) / 2).coerceIn(4, width - font.width(label) - 4)
        graphics.fill(labelX - 3, y - 18, labelX + font.width(label) + 3, y - 6, color(0, 0.75f))
        graphics.drawString(font, label, labelX, y - 16, 0xFFFFFFFF.toInt())
    }

    /** A play triangle or a pause sign, [size] tall, with its top left at [x], [y]. */
    private fun icon(graphics: GuiGraphics, x: Int, y: Int, size: Int, play: Boolean, color: Int) {
        val wide = size * 4 / 5
        if (play) {
            for (i in 0 until wide) {
                val inset = i * size / (2 * wide)
                graphics.fill(x + i, y + inset, x + i + 1, y + size - inset, color)
            }
        } else {
            val bar = max(1, size * 3 / 10)
            graphics.fill(x, y, x + bar, y + size, color)
            graphics.fill(x + wide - bar, y, x + wide, y + size, color)
        }
    }

    private fun color(rgb: Int, alpha: Float) = ((alpha * 255).toInt().coerceIn(0, 255) shl 24) or rgb

    private fun clock(ticks: Double): String {
        val seconds = ticks / 20.0
        return "%d:%05.2f".format((seconds / 60).toInt(), seconds % 60)
    }

    private const val BLACK = 0xFF000000.toInt()
}
