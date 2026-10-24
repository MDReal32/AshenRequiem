package com.mdreal.cinematica.timeline

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

// Pure math, no Minecraft types: everything here is unit tested.

data class Vec(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec(x * s, y * s, z * s)
    fun distanceTo(o: Vec) = sqrt((x - o.x) * (x - o.x) + (y - o.y) * (y - o.y) + (z - o.z) * (z - o.z))

    companion object {
        val ZERO = Vec(0.0, 0.0, 0.0)
    }
}

/** Euler rotation in degrees, used for bones and the actor's root. */
data class Rot(val x: Float, val y: Float, val z: Float) {
    companion object {
        val ZERO = Rot(0f, 0f, 0f)
    }
}

/** How time moves through a segment. Applied to the segment that *starts* at the keyframe carrying it. */
enum class Ease(val serialName: String) {
    LINEAR("linear"),
    IN_SINE("in_sine"),
    OUT_SINE("out_sine"),
    IN_OUT_SINE("in_out_sine"),
    IN_CUBIC("in_cubic"),
    OUT_CUBIC("out_cubic"),
    IN_OUT_CUBIC("in_out_cubic");

    fun apply(t: Double): Double = when (this) {
        LINEAR -> t
        IN_SINE -> 1 - cos(t * PI / 2)
        OUT_SINE -> sin(t * PI / 2)
        IN_OUT_SINE -> -(cos(PI * t) - 1) / 2
        IN_CUBIC -> t * t * t
        OUT_CUBIC -> 1 - (1 - t).let { it * it * it }
        IN_OUT_CUBIC -> if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).let { it * it * it } / 2
    }

    fun next(): Ease = entries[(ordinal + 1) % entries.size]

    companion object {
        fun byName(name: String): Ease? = entries.firstOrNull { it.serialName == name }
    }
}

/** The six bones of the player model, in degrees. `head` is relative to the body, like an armor stand. */
data class Pose(
    val head: Rot = Rot.ZERO,
    val body: Rot = Rot.ZERO,
    val rightArm: Rot = Rot.ZERO,
    val leftArm: Rot = Rot.ZERO,
    val rightLeg: Rot = Rot.ZERO,
    val leftLeg: Rot = Rot.ZERO,
) {
    /** The 18 bone angles in a fixed order, for smooth interpolation. */
    fun channels(): List<Float> =
        listOf(head, body, rightArm, leftArm, rightLeg, leftLeg).flatMap { listOf(it.x, it.y, it.z) }

    companion object {
        val STANDING = Pose()

        fun fromChannels(values: List<Float>, offset: Int = 0): Pose {
            fun rot(bone: Int) = (offset + bone * 3).let { Rot(values[it], values[it + 1], values[it + 2]) }
            return Pose(rot(0), rot(1), rot(2), rot(3), rot(4), rot(5))
        }
    }
}

/** A camera keyframe. [pos] is where the eye is, relative to the scene origin unless the scene is absolute. */
data class CameraKey(
    val t: Int,
    val pos: Vec,
    val yaw: Float,
    val pitch: Float,
    val roll: Float = 0f,
    val fov: Float? = null,
    val ease: Ease = Ease.LINEAR,
)

/**
 * An actor keyframe. [pos] is where the actor's feet are when standing; [yaw] turns the whole body, [root] tilts it
 * around the hips (e.g. `[90, 0, 0]` lies on the back, about 0.6 lower than standing so the back touches the ground).
 */
data class ActorKey(
    val t: Int,
    val pos: Vec,
    val yaw: Float,
    val pose: Pose = Pose.STANDING,
    val root: Rot = Rot.ZERO,
    val ease: Ease = Ease.LINEAR,
)

data class FadeKey(val t: Int, val alpha: Float, val ease: Ease = Ease.LINEAR)

data class Scene(
    val camera: List<CameraKey>,
    val actor: List<ActorKey> = emptyList(),
    val fade: List<FadeKey> = emptyList(),
    /** Scene length in ticks; defaults to the last keyframe of any track. */
    val duration: Int? = null,
    /** Ticks spent gliding from the last camera keyframe into the actor's eyes before control returns. */
    val handoff: Int = 20,
    val absolute: Boolean = false,
    val letterbox: Boolean = true,
    /** Whether a stand-in wearing the player's skin is shown where the player stands. */
    val showActor: Boolean = true,
) {
    val length: Int
        get() = duration ?: maxOf(camera.lastOrNull()?.t ?: 0, actor.lastOrNull()?.t ?: 0, fade.lastOrNull()?.t ?: 0)

    /** When the player gets control back. */
    val end: Int get() = length + handoff
}

data class CameraState(val pos: Vec, val yaw: Float, val pitch: Float, val roll: Float, val fov: Float?)

data class ActorState(val pos: Vec, val yaw: Float, val pose: Pose, val root: Rot) {
    /** Where the actor's eyes are, and where they look: the hand-back target. */
    fun eye(eyeHeight: Double = EYE_HEIGHT): CameraState =
        CameraState(pos + Vec(0.0, eyeHeight, 0.0), yaw + pose.head.y, pose.head.x, 0f, null)

    companion object {
        const val EYE_HEIGHT = 1.62

        /** [ActorKey.root] tilts the body around this height above the feet, so sitting up bends at the hips. */
        const val HIP_HEIGHT = 0.7
    }
}

/**
 * Evaluates a [Scene] at any time (in ticks, fractional for smooth rendering). Coordinates stay relative; the caller
 * adds the origin. [start] is the player's own state when the scene began, used when the scene has no actor keys.
 */
class Timeline(val scene: Scene, private val start: ActorState) {
    private val cameraKeys = scene.camera.sortedBy { it.t }.let(::unwrapCamera)
    private val actorKeys = scene.actor.sortedBy { it.t }.let(::unwrapActor)
    private val fadeKeys = scene.fade.sortedBy { it.t }

    fun actor(time: Double): ActorState {
        if (actorKeys.isEmpty()) return start
        val (i, u) = locate(actorKeys.map { it.t }, time) ?: return actorKeys.last().state()
        val e = actorKeys[i].ease.apply(u)
        val times = actorKeys.map { it.t }
        // Bones follow the same smooth curves as positions, so motion flows through keys instead of jerking at each.
        val channels = actorChannels.map { values -> hermite1(times, values, i, e).toFloat() }
        return ActorState(
            hermite(times, actorKeys.map { it.pos }, i, e),
            channels[0],
            Pose.fromChannels(channels, 4),
            Rot(channels[1], channels[2], channels[3])
        )
    }

    /** Per-channel values of every actor key: yaw, root x/y/z, then the pose's 18 bone angles. */
    private val actorChannels: List<List<Double>> = run {
        val rows = actorKeys.map { listOf(it.yaw, it.root.x, it.root.y, it.root.z) + it.pose.channels() }
        if (rows.isEmpty()) emptyList() else rows[0].indices.map { c -> rows.map { it[c].toDouble() } }
    }

    fun camera(time: Double): CameraState? {
        if (cameraKeys.isEmpty()) return null
        val handoffStart = scene.length.toDouble()
        if (time >= handoffStart && scene.handoff > 0) {
            val from = cameraAt(handoffStart)
            val to = actor(handoffStart).eye()
            val u = Ease.IN_OUT_SINE.apply(((time - handoffStart) / scene.handoff).coerceIn(0.0, 1.0))
            return CameraState(
                from.pos + (to.pos - from.pos) * u,
                lerp(from.yaw, from.yaw + wrapDegrees(to.yaw - from.yaw), u),
                lerp(from.pitch, to.pitch, u),
                lerp(from.roll, 0f, u),
                from.fov?.let { lerp(it, to.fov ?: it, u) }
            )
        }
        return cameraAt(time)
    }

    private fun cameraAt(time: Double): CameraState {
        val (i, u) = locate(cameraKeys.map { it.t }, time) ?: return cameraKeys.last().let {
            if (time < it.t) cameraKeys.first().state() else it.state()
        }
        val a = cameraKeys[i]
        val b = cameraKeys[i + 1]
        val e = a.ease.apply(u)
        val times = cameraKeys.map { it.t }
        return CameraState(
            hermite(times, cameraKeys.map { it.pos }, i, e),
            hermite1(times, cameraKeys.map { it.yaw.toDouble() }, i, e).toFloat(),
            hermite1(times, cameraKeys.map { it.pitch.toDouble() }, i, e).toFloat(),
            lerp(a.roll, b.roll, e),
            if (a.fov != null && b.fov != null) lerp(a.fov, b.fov, e) else a.fov ?: b.fov
        )
    }

    /** Black-screen opacity, 0..1. Before the first fade key the first value holds. */
    fun fade(time: Double): Float {
        if (fadeKeys.isEmpty()) return 0f
        val (i, u) = locate(fadeKeys.map { it.t }, time)
            ?: return if (time < fadeKeys.first().t) fadeKeys.first().alpha else fadeKeys.last().alpha
        val a = fadeKeys[i]
        return lerp(a.alpha, fadeKeys[i + 1].alpha, a.ease.apply(u))
    }

    private fun CameraKey.state() = CameraState(pos, yaw, pitch, roll, fov)
    private fun ActorKey.state() = ActorState(pos, yaw, pose, root)

    companion object {
        /** Segment index and 0..1 progress inside it, or null when [time] is outside the keys. */
        fun locate(times: List<Int>, time: Double): Pair<Int, Double>? {
            if (times.size < 2 || time < times.first() || time >= times.last()) return null
            var i = 0
            while (time >= times[i + 1]) i++
            val span = (times[i + 1] - times[i]).toDouble()
            return i to if (span <= 0) 1.0 else (time - times[i]) / span
        }

        /**
         * Cubic Hermite through the keys with Catmull-Rom tangents scaled by time, so the path is smooth across keys
         * and keeps its speed even when segments have different lengths. Ends use one-sided tangents.
         */
        fun hermite(times: List<Int>, points: List<Vec>, i: Int, u: Double): Vec = Vec(
            hermite1(times, points.map { it.x }, i, u),
            hermite1(times, points.map { it.y }, i, u),
            hermite1(times, points.map { it.z }, i, u),
        )

        fun hermite1(times: List<Int>, values: List<Double>, i: Int, u: Double): Double {
            val span = (times[i + 1] - times[i]).toDouble()
            val m0 = tangent(times, values, i) * span
            val m1 = tangent(times, values, i + 1) * span
            val u2 = u * u
            val u3 = u2 * u
            return (2 * u3 - 3 * u2 + 1) * values[i] + (u3 - 2 * u2 + u) * m0 +
                (-2 * u3 + 3 * u2) * values[i + 1] + (u3 - u2) * m1
        }

        private fun tangent(times: List<Int>, values: List<Double>, i: Int): Double {
            val lo = (i - 1).coerceAtLeast(0)
            val hi = (i + 1).coerceAtMost(values.lastIndex)
            val dt = (times[hi] - times[lo]).toDouble()
            return if (dt <= 0) 0.0 else (values[hi] - values[lo]) / dt
        }

        /** Shifts each yaw by whole turns so it is the closest to the previous one: no spinning the long way round. */
        fun unwrap(yaws: List<Float>): List<Float> {
            val out = ArrayList<Float>(yaws.size)
            yaws.forEachIndexed { i, yaw -> out += if (i == 0) yaw else nearest(out[i - 1], yaw) }
            return out
        }

        /** [yaw] moved by whole turns to be within 180 degrees of [reference]. */
        fun nearest(reference: Float, yaw: Float): Float = reference + wrapDegrees(yaw - reference)

        fun wrapDegrees(degrees: Float): Float {
            val d = degrees - 360f * floor((degrees + 180f) / 360f)
            return d
        }

        private fun unwrapCamera(keys: List<CameraKey>): List<CameraKey> =
            keys.zip(unwrap(keys.map { it.yaw })) { k, yaw -> k.copy(yaw = yaw) }

        private fun unwrapActor(keys: List<ActorKey>): List<ActorKey> =
            keys.zip(unwrap(keys.map { it.yaw })) { k, yaw -> k.copy(yaw = yaw) }
    }
}

internal fun lerp(a: Float, b: Float, t: Double): Float = (a + (b - a) * t).toFloat()
