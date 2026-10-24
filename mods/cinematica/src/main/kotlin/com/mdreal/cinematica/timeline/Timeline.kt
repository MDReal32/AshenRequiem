package com.mdreal.cinematica.timeline

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
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

/**
 * The bones of the actor, in degrees. `head` is relative to the body, like an armor stand. Arms and legs bend in the
 * middle: elbows and knees turn the lower half relative to the upper one (negative x bends an elbow forward, positive x
 * bends a knee back).
 */
data class Pose(
    val head: Rot = Rot.ZERO,
    val body: Rot = Rot.ZERO,
    val rightArm: Rot = Rot.ZERO,
    val leftArm: Rot = Rot.ZERO,
    val rightLeg: Rot = Rot.ZERO,
    val leftLeg: Rot = Rot.ZERO,
    val rightElbow: Rot = Rot.ZERO,
    val leftElbow: Rot = Rot.ZERO,
    val rightKnee: Rot = Rot.ZERO,
    val leftKnee: Rot = Rot.ZERO,
) {
    /** All bone angles in a fixed order, for smooth interpolation. */
    fun channels(): List<Float> =
        listOf(head, body, rightArm, leftArm, rightLeg, leftLeg, rightElbow, leftElbow, rightKnee, leftKnee)
            .flatMap { listOf(it.x, it.y, it.z) }

    companion object {
        val STANDING = Pose()

        fun fromChannels(values: List<Float>, offset: Int = 0): Pose {
            fun rot(bone: Int) = (offset + bone * 3).let { Rot(values[it], values[it + 1], values[it + 2]) }
            return Pose(rot(0), rot(1), rot(2), rot(3), rot(4), rot(5), rot(6), rot(7), rot(8), rot(9))
        }
    }
}

/**
 * A camera keyframe. [pos] is where the eye is, relative to the scene origin unless the scene is absolute. [ground]
 * (0..1) is how much of its height counts from the ground under it instead of from the origin: 1 flies that high
 * above the land, 0 stays on the origin. See [Scene.onGround].
 */
data class CameraKey(
    val t: Int,
    val pos: Vec,
    val yaw: Float,
    val pitch: Float,
    val roll: Float = 0f,
    val fov: Float? = null,
    val ease: Ease = Ease.LINEAR,
    val ground: Float = 0f,
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

/** How far the fog sits, as a multiple of what the world sets there: 1 is the world's own, 3 three times as far. */
data class FogKey(val t: Int, val scale: Float, val ease: Ease = Ease.LINEAR)

data class Scene(
    val camera: List<CameraKey>,
    val actor: List<ActorKey> = emptyList(),
    val fade: List<FadeKey> = emptyList(),
    val fog: List<FogKey> = emptyList(),
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
        get() = duration ?: maxOf(camera.lastOrNull()?.t ?: 0, actor.lastOrNull()?.t ?: 0, fade.lastOrNull()?.t ?: 0, fog.lastOrNull()?.t ?: 0)

    /** When the player gets control back. */
    val end: Int get() = length + handoff

    /** Whether any camera key wants its height from the ground. */
    val grounded: Boolean get() = camera.any { it.ground > 0f }

    /**
     * The scene with each camera key raised by its [CameraKey.ground] weight times how far the ground under it is
     * above the origin, ready to play: the weights are spent. [groundAt] measures that for a key. Along the path the
     * ground is smoothed, so the camera glides over the land instead of copying every block, and only ever upward:
     * no key ends up nearer the ground under it than its own height says.
     *
     * Where the weight is fading, the part that still counts is from the land where it was last full, so a valley or
     * hill under the fade doesn't bend the path. Such a key is still held [CLEARANCE] above the land under it.
     * A key that follows the land fully only looks at the land under the keys that do too, so it doesn't climb a hill
     * ahead that the camera will fly over by then on its own heights.
     */
    fun onGround(groundAt: (CameraKey) -> Double): Scene {
        if (!grounded) return this
        val keys = camera.sortedBy { it.t }
        val raw = keys.map(groundAt)
        val under = smoothGround(keys, raw, keys.indices.toList())
        val follow = smoothGround(keys, raw, keys.indices.filter { keys[it].ground >= 1f })
        var held: Double? = null
        return copy(camera = keys.mapIndexed { i, key ->
            if (key.ground == 0f) {
                held = null
                return@mapIndexed key
            }
            val y = if (key.ground >= 1f) {
                held = follow[i]
                key.pos.y + follow[i]
            } else {
                maxOf(key.pos.y + (held ?: under[i]) * key.ground, under[i] + CLEARANCE)
            }
            key.copy(pos = Vec(key.pos.x, y, key.pos.z), ground = 0f)
        })
    }

    /**
     * The land under each key, smoothed over the keys [among] within reach: every key within reach of a high point lifts
     * to it, then a bell curve over the same reach rounds that off. A key among them never comes out below its own land.
     */
    private fun smoothGround(keys: List<CameraKey>, raw: List<Double>, among: List<Int>): List<Double> {
        fun near(i: Int) = among.filter { abs(keys[it].t - keys[i].t) <= GROUND_REACH }
        val high = keys.indices.map { i -> near(i).maxOfOrNull { raw[it] } ?: raw[i] }
        return keys.indices.map { i ->
            val weights = near(i).associateWith { j -> (keys[j].t - keys[i].t).toDouble().let { exp(-it * it / (2 * GROUND_SIGMA * GROUND_SIGMA)) } }
            if (weights.isEmpty()) high[i] else weights.entries.sumOf { (j, w) -> high[j] * w } / weights.values.sum()
        }
    }

    companion object {
        /** Ticks along the path over which the ground under the camera is smoothed. */
        const val GROUND_REACH = 24

        /** How near the land under it a key whose ground weight is fading may come. */
        const val CLEARANCE = 2.0
        private const val GROUND_SIGMA = GROUND_REACH / 3.0
    }
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
    private val fogKeys = scene.fog.sortedBy { it.t }

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

    /** Per-channel values of every actor key: yaw, root x/y/z, then the pose's bone angles. */
    private val actorChannels: List<List<Double>> = run {
        val rows = actorKeys.map { listOf(it.yaw, it.root.x, it.root.y, it.root.z) + it.pose.channels() }
        if (rows.isEmpty()) emptyList() else rows[0].indices.map { c -> rows.map { it[c].toDouble() } }
    }

    fun camera(time: Double): CameraState? {
        if (cameraKeys.isEmpty()) return null
        val handoffStart = scene.length.toDouble()
        if (time >= handoffStart && scene.handoff > 0) {
            // The glide keeps the speed the camera arrives with and settles in the eyes, so there is no stop before it.
            val from = cameraAt(handoffStart)
            val before = cameraAt(handoffStart - 1.0)
            val to = actor(handoffStart).eye()
            val span = scene.handoff.toDouble()
            val u = ((time - handoffStart) / span).coerceIn(0.0, 1.0)
            fun glide(a: Double, b: Double, rate: Double) = settle(a, b, rate * span, u)
            fun glide(a: Float, b: Float, rate: Float) = glide(a.toDouble(), b.toDouble(), rate.toDouble()).toFloat()
            val velocity = from.pos - before.pos
            return CameraState(
                Vec(
                    glide(from.pos.x, to.pos.x, velocity.x),
                    glide(from.pos.y, to.pos.y, velocity.y),
                    glide(from.pos.z, to.pos.z, velocity.z)
                ),
                glide(from.yaw, nearest(from.yaw, to.yaw), from.yaw - nearest(from.yaw, before.yaw)),
                glide(from.pitch, to.pitch, from.pitch - before.pitch),
                // A camera that rolled full turns ends at the nearest upright one instead of spinning back.
                glide(from.roll, from.roll - wrapDegrees(from.roll), from.roll - before.roll),
                from.fov?.let { lerp(it, to.fov ?: it, Ease.IN_OUT_SINE.apply(u)) }
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
            // Roll follows a curve like the angles above: straight lines between keys jerk at every key of a fast roll.
            hermite1(times, cameraKeys.map { it.roll.toDouble() }, i, e).toFloat(),
            if (a.fov != null && b.fov != null) lerp(a.fov, b.fov, e) else a.fov ?: b.fov
        )
    }

    /** Black-screen opacity, 0..1. Before the first fade key the first value holds. */
    fun fade(time: Double): Float =
        if (fadeKeys.isEmpty()) 0f else track(fadeKeys, time, FadeKey::t, FadeKey::alpha, FadeKey::ease)

    /** Fog distance as a multiple of the world's, or null to leave the world's fog alone. Holds like [fade]. */
    fun fog(time: Double): Float? =
        if (fogKeys.isEmpty()) null else track(fogKeys, time, FogKey::t, FogKey::scale, FogKey::ease)

    private fun <K> track(keys: List<K>, time: Double, t: (K) -> Int, value: (K) -> Float, ease: (K) -> Ease): Float {
        val (i, u) = locate(keys.map(t), time) ?: return value(if (time < t(keys.first())) keys.first() else keys.last())
        return lerp(value(keys[i]), value(keys[i + 1]), ease(keys[i]).apply(u))
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

        /** Hermite from [a] leaving at [m0] (per whole segment) to [b], arriving at rest. */
        fun settle(a: Double, b: Double, m0: Double, u: Double): Double {
            val u2 = u * u
            val u3 = u2 * u
            return (2 * u3 - 3 * u2 + 1) * a + (u3 - 2 * u2 + u) * m0 + (-2 * u3 + 3 * u2) * b
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
