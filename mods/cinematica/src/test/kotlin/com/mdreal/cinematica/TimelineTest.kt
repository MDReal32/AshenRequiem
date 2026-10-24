package com.mdreal.cinematica

import com.mdreal.cinematica.timeline.ActorKey
import com.mdreal.cinematica.timeline.ActorState
import com.mdreal.cinematica.timeline.CameraKey
import com.mdreal.cinematica.timeline.Ease
import com.mdreal.cinematica.timeline.FadeKey
import com.mdreal.cinematica.timeline.FogKey
import com.mdreal.cinematica.timeline.Pose
import com.mdreal.cinematica.timeline.Rot
import com.mdreal.cinematica.timeline.Scene
import com.mdreal.cinematica.timeline.Timeline
import com.mdreal.cinematica.timeline.Vec
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineTest {
    private val start = ActorState(Vec.ZERO, 90f, Pose.STANDING, Rot.ZERO)

    private fun close(expected: Double, actual: Double, eps: Double = 1e-6) =
        assertTrue(abs(expected - actual) < eps, "expected $expected, got $actual")

    @Test
    fun `camera passes exactly through its keyframes`() {
        val keys = listOf(
            CameraKey(0, Vec(0.0, 0.0, 0.0), 0f, 0f),
            CameraKey(20, Vec(10.0, 5.0, 0.0), 45f, 10f),
            CameraKey(60, Vec(10.0, 5.0, 30.0), 90f, -20f),
        )
        val timeline = Timeline(Scene(keys, handoff = 0), start)
        keys.forEach { key ->
            val state = timeline.camera(key.t.toDouble())!!
            close(key.pos.x, state.pos.x)
            close(key.pos.y, state.pos.y)
            close(key.pos.z, state.pos.z)
            close(key.yaw.toDouble(), state.yaw.toDouble(), 1e-4)
        }
    }

    @Test
    fun `a straight path with evenly spaced keys moves at constant speed`() {
        val keys = (0..3).map { CameraKey(it * 20, Vec(it * 10.0, 0.0, 0.0), 0f, 0f) }
        val timeline = Timeline(Scene(keys, handoff = 0), start)
        close(5.0, timeline.camera(10.0)!!.pos.x)
        close(25.0, timeline.camera(50.0)!!.pos.x)
    }

    @Test
    fun `yaw takes the short way across the wrap`() {
        val keys = listOf(CameraKey(0, Vec.ZERO, 170f, 0f), CameraKey(20, Vec.ZERO, -170f, 0f))
        val yaw = Timeline(Scene(keys, handoff = 0), start).camera(10.0)!!.yaw
        close(180.0, yaw.toDouble(), 1e-3)
    }

    @Test
    fun `unwrap keeps a full turn continuous`() {
        assertEquals(listOf(350f, 370f, 390f), Timeline.unwrap(listOf(350f, 10f, 30f)))
    }

    @Test
    fun `ease applies to the segment starting at its key`() {
        val keys = listOf(
            CameraKey(0, Vec.ZERO, 0f, 0f, ease = Ease.IN_CUBIC),
            CameraKey(20, Vec(0.0, 0.0, 0.0), 0f, 80f),
        )
        val pitch = Timeline(Scene(keys, handoff = 0), start).camera(10.0)!!.pitch
        assertTrue(pitch < 40f, "in_cubic should be behind linear at the midpoint, got $pitch")
    }

    @Test
    fun `before the first and after the last key the camera holds`() {
        val keys = listOf(CameraKey(10, Vec(1.0, 2.0, 3.0), 0f, 0f), CameraKey(20, Vec(4.0, 5.0, 6.0), 0f, 0f))
        val timeline = Timeline(Scene(keys, handoff = 0), start)
        close(1.0, timeline.camera(0.0)!!.pos.x)
        close(4.0, timeline.camera(100.0)!!.pos.x)
    }

    @Test
    fun `handoff ends in the actor's eyes`() {
        val camera = listOf(CameraKey(0, Vec(0.0, 20.0, 0.0), 0f, 90f), CameraKey(40, Vec(5.0, 20.0, 5.0), 0f, 90f))
        val actor = listOf(
            ActorKey(0, Vec(2.0, 0.0, 2.0), 30f),
            ActorKey(40, Vec(2.0, 0.0, 2.0), 30f, Pose(head = Rot(15f, 10f, 0f))),
        )
        val scene = Scene(camera, actor, handoff = 20)
        val end = Timeline(scene, start).camera(scene.end.toDouble())!!
        close(2.0, end.pos.x)
        close(ActorState.EYE_HEIGHT, end.pos.y)
        close(40.0, end.yaw.toDouble(), 1e-3)
        close(15.0, end.pitch.toDouble(), 1e-3)
    }

    @Test
    fun `roll keeps its speed through a key`() {
        val camera = listOf(0f, 10f, 40f, 90f).mapIndexed { i, roll -> CameraKey(i * 5, Vec.ZERO, 0f, 0f, roll) }
        val timeline = Timeline(Scene(camera), start)
        // degrees per tick just before and just after the key at t5 (straight lines would jump from 2 to 6)
        val before = (timeline.camera(5.0)!!.roll - timeline.camera(4.99)!!.roll) / 0.01
        val after = (timeline.camera(5.01)!!.roll - timeline.camera(5.0)!!.roll) / 0.01
        close(before, after, 0.1)
    }

    @Test
    fun `handoff keeps the camera's speed and never unrolls a full turn`() {
        val camera = listOf(
            CameraKey(0, Vec(0.0, 1.62, -4.0), 0f, 0f, 0f),
            CameraKey(40, Vec(0.0, 1.62, -2.0), 0f, 0f, 360f),
        )
        val scene = Scene(camera, listOf(ActorKey(0, Vec.ZERO, 0f)), handoff = 20)
        val timeline = Timeline(scene, start)
        val arriving = timeline.camera(40.0)!!.pos.z - timeline.camera(39.0)!!.pos.z
        val leaving = timeline.camera(41.0)!!.pos.z - timeline.camera(40.0)!!.pos.z
        close(arriving, leaving, 0.01)
        assertTrue(timeline.camera(50.0)!!.roll > 359f, "roll spun back")
        close(360.0, timeline.camera(scene.end.toDouble())!!.roll.toDouble(), 1e-3)
    }

    @Test
    fun `without actor keys the actor stays where the player started`() {
        val scene = Scene(listOf(CameraKey(0, Vec.ZERO, 0f, 0f)))
        assertEquals(start, Timeline(scene, start).actor(5.0))
    }

    @Test
    fun `bones pass through their keys and flow smoothly between them`() {
        val raised = Pose(rightArm = Rot(-90f, 0f, 30f))
        val actor = listOf(
            ActorKey(0, Vec.ZERO, 0f),
            ActorKey(20, Vec.ZERO, 0f, raised, Rot(90f, 0f, 0f)),
            ActorKey(40, Vec.ZERO, 0f),
        )
        val timeline = Timeline(Scene(emptyList(), actor), start)
        assertEquals(raised, timeline.actor(20.0).pose)
        assertEquals(Rot(90f, 0f, 0f), timeline.actor(20.0).root)
        // At the peak the curve is flat, so just before it the arm is already almost fully raised: no linear corner.
        val before = timeline.actor(18.0).pose.rightArm.x
        assertTrue(before < -85f, "expected a flat peak, got $before")
        close(0.0, timeline.actor(40.0).pose.rightArm.x.toDouble())
    }

    @Test
    fun `elbows and knees animate like the other bones`() {
        val bent = Pose(rightElbow = Rot(-100f, 0f, 0f), leftKnee = Rot(80f, 0f, 0f))
        assertEquals(bent, Pose.fromChannels(bent.channels()))
        val actor = listOf(ActorKey(0, Vec.ZERO, 0f), ActorKey(20, Vec.ZERO, 0f, bent))
        val half = Timeline(Scene(emptyList(), actor), start).actor(10.0).pose
        close(-50.0, half.rightElbow.x.toDouble(), 1e-3)
        close(40.0, half.leftKnee.x.toDouble(), 1e-3)
    }

    @Test
    fun `fade interpolates and holds at the ends`() {
        val scene = Scene(emptyList(), fade = listOf(FadeKey(0, 1f), FadeKey(20, 0f)))
        val timeline = Timeline(scene, start)
        close(1.0, timeline.fade(-5.0).toDouble())
        close(0.5, timeline.fade(10.0).toDouble())
        close(0.0, timeline.fade(30.0).toDouble())
    }

    @Test
    fun `fog leaves the world alone without keys and scales between them`() {
        assertNull(Timeline(Scene(emptyList()), start).fog(10.0))
        val timeline = Timeline(Scene(emptyList(), fog = listOf(FogKey(0, 3f), FogKey(20, 1f))), start)
        close(3.0, timeline.fog(-5.0)!!.toDouble())
        close(2.0, timeline.fog(10.0)!!.toDouble())
        close(1.0, timeline.fog(30.0)!!.toDouble())
    }

    @Test
    fun `ground raises camera keys by their weight and spends the weights`() {
        val scene = Scene(listOf(
            CameraKey(0, Vec(0.0, 90.0, 300.0), 0f, 0f, ground = 1f),
            CameraKey(10, Vec(0.0, 40.0, 100.0), 0f, 0f, ground = 0.5f),
            CameraKey(20, Vec(0.0, 2.0, 0.0), 0f, 0f),
        ))
        assertTrue(scene.grounded)
        val lifted = scene.onGround { -12.0 }
        assertEquals(listOf(78.0, 34.0, 2.0), lifted.camera.map { it.pos.y })
        assertTrue(!lifted.grounded)
        val flat = Scene(listOf(CameraKey(0, Vec.ZERO, 0f, 0f)))
        assertTrue(flat.onGround { 5.0 } === flat)
    }

    @Test
    fun `ground under the path is smoothed but never let below a key`() {
        // flat land with one 10 block hill under the key at t50
        val scene = Scene((0..20).map { CameraKey(it * 5, Vec(0.0, 12.0, it * -5.0), 0f, 0f, ground = 1f) })
        val lifted = scene.onGround { if (it.t == 50) 10.0 else 0.0 }.camera.associate { it.t to it.pos.y - 12.0 }
        assertTrue(lifted.getValue(50) >= 10.0, "camera dipped into the hill")
        assertTrue(lifted.getValue(40) > 0.0 && lifted.getValue(40) < lifted.getValue(50) + 1e-9, "no rise before the hill")
        close(0.0, lifted.getValue(0))
    }

    @Test
    fun `a fading ground weight counts from the land where it was full and keeps clear of the land under it`() {
        // keys far enough apart that the land under each isn't smoothed into the others
        fun lifted(landAt30: Double) = Scene(listOf(
            CameraKey(0, Vec(0.0, 14.0, 0.0), 0f, 0f, ground = 1f),
            CameraKey(30, Vec(0.0, 8.0, -30.0), 0f, 0f, ground = 0.5f),
            CameraKey(60, Vec(0.0, 2.0, -60.0), 0f, 0f),
        )).onGround { mapOf(0 to -8.0, 30 to landAt30).getOrDefault(it.t, 0.0) }.camera.map { it.pos.y }
        // over a valley it carries on from where the full weight ended: 8 + 0.5 * -8
        assertEquals(listOf(6.0, 4.0, 2.0), lifted(-24.0))
        // over a hill it stays above the land
        assertEquals(listOf(6.0, 10.0 + Scene.CLEARANCE, 2.0), lifted(10.0))
    }

    @Test
    fun `keys that follow the land don't climb a hill only the fading keys after them fly over`() {
        // a valley 20 down under the following keys, the land back up at the origin's height under the fading ones
        // after key 4 it comes down from 30 over the land to 2 over the origin, the weight going as it does
        val scene = Scene((0..8).map { i ->
            val down = ((i - 4) / 4.0).coerceAtLeast(0.0)
            CameraKey(i * 5, Vec(0.0, 30.0 - 28.0 * down, 40.0 - i * 5), 0f, 0f, ground = (1 - down).toFloat())
        })
        val lifted = scene.onGround { if (it.t <= 20) -20.0 else 0.0 }.camera.map { it.pos.y }
        // the last following key stays 30 over the valley, and from there the fade only comes down
        close(10.0, lifted[4])
        assertTrue(lifted.zipWithNext().drop(4).all { (a, b) -> b <= a + 1e-9 }, "climbed after the fade began: $lifted")
    }

    @Test
    fun `an empty camera track has no camera`() {
        assertNull(Timeline(Scene(emptyList()), start).camera(0.0))
    }

    @Test
    fun `scene length comes from the longest track`() {
        val scene = Scene(
            listOf(CameraKey(0, Vec.ZERO, 0f, 0f), CameraKey(40, Vec.ZERO, 0f, 0f)),
            fade = listOf(FadeKey(60, 0f)),
            handoff = 20,
        )
        assertEquals(60, scene.length)
        assertEquals(80, scene.end)
    }
}
