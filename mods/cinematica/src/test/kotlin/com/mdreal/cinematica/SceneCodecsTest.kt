package com.mdreal.cinematica

import com.google.gson.JsonParser
import com.mdreal.cinematica.scene.SceneCodecs
import com.mdreal.cinematica.timeline.ActorKey
import com.mdreal.cinematica.timeline.CameraKey
import com.mdreal.cinematica.timeline.Ease
import com.mdreal.cinematica.timeline.FadeKey
import com.mdreal.cinematica.timeline.Pose
import com.mdreal.cinematica.timeline.Rot
import com.mdreal.cinematica.timeline.Scene
import com.mdreal.cinematica.timeline.Vec
import com.mojang.serialization.JsonOps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SceneCodecsTest {
    private fun encode(scene: Scene) = SceneCodecs.SCENE.encodeStart(JsonOps.INSTANCE, scene).getOrThrow().asJsonObject

    private fun decode(json: String) = SceneCodecs.SCENE.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow()

    @Test
    fun `a scene survives a round trip`() {
        val scene = Scene(
            camera = listOf(
                CameraKey(0, Vec(1.0, 2.0, 3.0), 10f, -5f, roll = 3f, fov = 60f, ease = Ease.IN_OUT_SINE),
                CameraKey(40, Vec(4.0, 5.0, 6.0), 90f, 0f),
            ),
            actor = listOf(ActorKey(0, Vec.ZERO, 45f, Pose(rightArm = Rot(-90f, 0f, 0f)), Rot(90f, 0f, 0f), Ease.OUT_CUBIC)),
            fade = listOf(FadeKey(0, 1f), FadeKey(20, 0f, Ease.IN_SINE)),
            duration = 60,
            handoff = 30,
            absolute = true,
            letterbox = false,
            showActor = false,
        )
        assertEquals(scene, decode(encode(scene).toString()))
    }

    @Test
    fun `defaults are left out of the json`() {
        val json = encode(Scene(listOf(CameraKey(0, Vec.ZERO, 0f, 0f))))
        assertEquals(setOf("camera"), json.keySet())
        assertEquals(setOf("t", "pos", "yaw", "pitch"), json.getAsJsonArray("camera")[0].asJsonObject.keySet())
    }

    @Test
    fun `a hand-written scene uses the defaults`() {
        val scene = decode("""{"camera": [{"t": 0, "pos": [0, 1, 0], "yaw": 0, "pitch": 0, "ease": "in_sine"}]}""")
        assertEquals(Ease.IN_SINE, scene.camera.single().ease)
        assertEquals(20, scene.handoff)
        assertTrue(scene.letterbox)
        assertFalse(scene.absolute)
    }

    @Test
    fun `an unknown ease is an error`() {
        val result = SceneCodecs.SCENE.parse(
            JsonOps.INSTANCE,
            JsonParser.parseString("""{"camera": [{"t": 0, "pos": [0, 0, 0], "yaw": 0, "pitch": 0, "ease": "bouncy"}]}""")
        )
        assertTrue(result.isError)
    }
}
