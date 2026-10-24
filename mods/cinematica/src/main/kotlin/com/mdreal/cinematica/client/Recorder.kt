package com.mdreal.cinematica.client

import com.mdreal.cinematica.client.Keys.label
import com.mdreal.cinematica.client.Keys.presses
import com.mdreal.cinematica.network.SavePayload
import com.mdreal.cinematica.network.StartPayload
import com.mdreal.cinematica.timeline.ActorKey
import com.mdreal.cinematica.timeline.ActorState
import com.mdreal.cinematica.timeline.CameraKey
import com.mdreal.cinematica.timeline.Ease
import com.mdreal.cinematica.timeline.FadeKey
import com.mdreal.cinematica.timeline.Pose
import com.mdreal.cinematica.timeline.Rot
import com.mdreal.cinematica.timeline.Scene
import com.mdreal.cinematica.timeline.Timeline
import com.mdreal.cinematica.timeline.Vec
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

/**
 * The in-game recorder. The player flies as a spectator (the server keeps their chunks loaded) and drops keys at a
 * time cursor: camera keys from their own view, actor keys from where they hover. Saving sends the scene to the
 * server, which writes it to `<server dir>/cinematica/scenes/<name>.json`.
 */
object Recorder {
    /** How far the cursor jumps after a new camera key: the default length of a shot. */
    private const val SHOT = 40

    private val POSES = listOf(
        "standing" to Pose.STANDING,
        "arms forward" to Pose(rightArm = Rot(-90f, 0f, 0f), leftArm = Rot(-90f, 0f, 0f)),
        "reaching" to Pose(rightArm = Rot(-100f, 0f, 0f)),
        "waving" to Pose(rightArm = Rot(-170f, 0f, 15f)),
        "sitting" to Pose(
            rightArm = Rot(-36f, 0f, 0f), leftArm = Rot(-36f, 0f, 0f),
            rightLeg = Rot(-81f, 18f, 4f), leftLeg = Rot(-81f, -18f, -4f)
        ),
        "kneeling" to Pose(rightLeg = Rot(-90f, 0f, 0f), rightKnee = Rot(90f, 0f, 0f), leftKnee = Rot(90f, 0f, 0f)),
        "lying on back" to Pose.STANDING,
        "lying face down" to Pose.STANDING,
    )

    /** Root tilt per preset; only the lying ones tip the whole body over. */
    private fun rootOf(preset: String) = when (preset) {
        "lying on back" -> Rot(90f, 0f, 0f)
        "lying face down" -> Rot(-90f, 0f, 0f)
        else -> Rot.ZERO
    }

    /** How far below the hovering feet a preset goes, so lying and sitting actors touch the ground. */
    private fun dropOf(preset: String) = when (preset) {
        "lying on back", "lying face down" -> 0.58
        "sitting" -> 0.6
        "kneeling" -> 0.35
        else -> 0.0
    }

    var active = false
        private set

    private var name = ""
    private var origin = Vec.ZERO
    private var start = ActorState(Vec.ZERO, 0f, Pose.STANDING, Rot.ZERO)
    private var base = Scene(emptyList())
    private val camera = ArrayList<CameraKey>()
    private val actor = ArrayList<ActorKey>()
    private val fade = ArrayList<FadeKey>()
    private var cursor = 0
    private var preset = 0
    private var cached: Timeline? = null

    private val timeline: Timeline get() = cached ?: Timeline(scene(), start).also { cached = it }

    fun start(payload: StartPayload) {
        name = payload.name
        origin = payload.origin
        start = ActorState(payload.start, payload.yaw, Pose(head = Rot(payload.pitch, 0f, 0f)), Rot.ZERO)
        base = payload.scene
        camera.setAll(payload.scene.camera)
        actor.setAll(payload.scene.actor)
        fade.setAll(payload.scene.fade)
        cursor = 0
        cached = null
        active = true
    }

    fun stop() {
        if (!active) return
        active = false
        cached = null
        if (Director.isPreview) Director.stop()
        Stage.remove()
    }

    fun scene() = base.copy(camera = camera.sortedBy { it.t }, actor = actor.sortedBy { it.t }, fade = fade.sortedBy { it.t })

    fun tick() {
        if (!active) {
            Keys.ALL.forEach { it.presses() }
            return
        }
        val player = Minecraft.getInstance().player ?: return
        if (Keys.PREVIEW.presses() % 2 == 1) {
            if (Director.isPreview) Director.stop() else preview(player)
        }
        if (Director.active) {
            Keys.ALL.forEach { it.presses() }
            return
        }
        repeat(Keys.CAMERA.presses()) { cameraKey(player) }
        repeat(Keys.ACTOR.presses()) { actorKey(player) }
        repeat(Keys.POSE.presses()) { nextPose() }
        repeat(Keys.EASE.presses()) { nextEase() }
        repeat(Keys.FADE.presses()) { toggleFade() }
        repeat(Keys.DELETE.presses()) { deleteAtCursor() }
        val step = when {
            Screen.hasShiftDown() -> 1
            Screen.hasControlDown() -> 40
            else -> 10
        }
        cursor = (cursor + step * (Keys.FORWARD.presses() - Keys.BACK.presses())).coerceAtLeast(0)
        repeat(Keys.NEXT_KEY.presses()) { keyTimes().firstOrNull { it > cursor }?.let { cursor = it } }
        repeat(Keys.PREV_KEY.presses()) { keyTimes().lastOrNull { it < cursor }?.let { cursor = it } }
        if (Keys.SAVE.presses() > 0) PacketDistributor.sendToServer(SavePayload(scene()))
    }

    /** While not previewing, the actor stands where the timeline puts it at the cursor. */
    fun frame() {
        if (active && !Director.active) Stage.show(timeline.actor(cursor.toDouble()), origin)
    }

    private fun preview(player: LocalPlayer) {
        val scene = scene()
        if (scene.camera.isEmpty()) {
            player.displayClientMessage(Component.translatable("cinematica_studio.recorder.no_camera"), true)
            return
        }
        val from = if (cursor >= scene.length) 0 else cursor
        Director.play(name, timeline, origin, from.toDouble(), preview = true)
    }

    private fun cameraKey(player: LocalPlayer) {
        val eye = player.getEyePosition(1f)
        val index = camera.indexOfFirst { it.t == cursor }
        val key = CameraKey(
            cursor, Vec(eye.x, eye.y, eye.z) - origin, player.yRot, player.xRot,
            ease = camera.getOrNull(index)?.ease ?: Ease.LINEAR
        )
        if (index >= 0) {
            camera[index] = key
        } else {
            camera += key
            cursor += SHOT
        }
        changed()
    }

    private fun actorKey(player: LocalPlayer) {
        val (presetName, pose) = POSES[preset]
        val feet = player.position()
        val key = ActorKey(
            cursor, Vec(feet.x, feet.y - dropOf(presetName), feet.z) - origin, player.yRot,
            pose.copy(head = Rot(player.xRot.coerceIn(-60f, 60f), 0f, 0f)),
            rootOf(presetName),
            actor.firstOrNull { it.t == cursor }?.ease ?: Ease.LINEAR
        )
        actor.removeIf { it.t == cursor }
        actor += key
        changed()
    }

    private fun nextPose() {
        val previous = POSES[preset].first
        preset = (preset + 1) % POSES.size
        val (presetName, pose) = POSES[preset]
        val index = actor.indexOfFirst { it.t == cursor }
        if (index >= 0) {
            val lift = Vec(0.0, dropOf(previous) - dropOf(presetName), 0.0)
            actor[index] = actor[index].let {
                it.copy(pos = it.pos + lift, pose = pose.copy(head = it.pose.head), root = rootOf(presetName))
            }
            changed()
        }
    }

    private fun nextEase() {
        camera.indexOfFirst { it.t == cursor }.takeIf { it >= 0 }?.let { camera[it] = camera[it].copy(ease = camera[it].ease.next()) }
            ?: actor.indexOfFirst { it.t == cursor }.takeIf { it >= 0 }?.let { actor[it] = actor[it].copy(ease = actor[it].ease.next()) }
            ?: fade.indexOfFirst { it.t == cursor }.takeIf { it >= 0 }?.let { fade[it] = fade[it].copy(ease = fade[it].ease.next()) }
        changed()
    }

    private fun toggleFade() {
        val alpha = if (timeline.fade(cursor.toDouble()) >= 0.5f) 0f else 1f
        fade.removeIf { it.t == cursor }
        fade += FadeKey(cursor, alpha)
        changed()
    }

    private fun deleteAtCursor() {
        camera.removeIf { it.t == cursor }
        actor.removeIf { it.t == cursor }
        fade.removeIf { it.t == cursor }
        changed()
    }

    private fun keyTimes() = (camera.map { it.t } + actor.map { it.t } + fade.map { it.t }).distinct().sorted()

    private fun changed() {
        cached = null
    }

    fun hud(graphics: GuiGraphics) {
        val font = Minecraft.getInstance().font
        var y = 6
        fun line(text: String, color: Int = 0xFFFFFFFF.toInt()) {
            graphics.drawString(font, text, 6, y, color)
            y += font.lineHeight + 2
        }

        val scene = scene()
        line("● REC $name   ${seconds(cursor)} (tick $cursor)   length ${seconds(scene.length)}", 0xFFFF5555.toInt())
        line("camera ${camera.size}   actor ${actor.size}   fade ${fade.size}   pose: ${POSES[preset].first}")
        val here = listOfNotNull(
            camera.firstOrNull { it.t == cursor }?.let { "camera (${it.ease.serialName})" },
            actor.firstOrNull { it.t == cursor }?.let { "actor (${it.ease.serialName})" },
            fade.firstOrNull { it.t == cursor }?.let { "fade ${it.alpha} (${it.ease.serialName})" },
        )
        line("at cursor: ${here.joinToString().ifEmpty { "-" }}", 0xFFAAAAAA.toInt())
        y += 4
        val gray = 0xFF888888.toInt()
        line("${Keys.CAMERA.label()} camera key   ${Keys.ACTOR.label()} actor key   ${Keys.POSE.label()} pose   ${Keys.EASE.label()} ease", gray)
        line("${Keys.FADE.label()} fade   ${Keys.DELETE.label()} delete   ${Keys.BACK.label()}/${Keys.FORWARD.label()} time (shift 1 tick, ctrl 2s)", gray)
        line("${Keys.PREV_KEY.label()}/${Keys.NEXT_KEY.label()} jump to key   ${Keys.PREVIEW.label()} preview   ${Keys.SAVE.label()} save   /cinematica stop to leave", gray)
    }

    private fun seconds(ticks: Int) = "%.2fs".format(ticks / 20.0)

    private fun <T> MutableList<T>.setAll(items: List<T>) {
        clear()
        addAll(items)
    }
}
