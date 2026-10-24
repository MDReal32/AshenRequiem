package com.mdreal.cinematica.client

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.client.settings.IKeyConflictContext
import org.lwjgl.glfw.GLFW

/** Recorder keys. Defaults are keys vanilla leaves unbound, and they only count while the recorder is open. */
object Keys {
    private const val CATEGORY = "key.categories.cinematica_studio"

    private object WhileRecording : IKeyConflictContext {
        override fun isActive() = Recorder.active && Minecraft.getInstance().screen == null
        override fun conflicts(other: IKeyConflictContext) = other === this
    }

    private fun key(name: String, code: Int) =
        KeyMapping("key.cinematica_studio.$name", WhileRecording, InputConstants.Type.KEYSYM, code, CATEGORY)

    val CAMERA = key("camera_key", GLFW.GLFW_KEY_K)
    val ACTOR = key("actor_key", GLFW.GLFW_KEY_J)
    val POSE = key("pose", GLFW.GLFW_KEY_O)
    val EASE = key("ease", GLFW.GLFW_KEY_U)
    val FADE = key("fade", GLFW.GLFW_KEY_B)
    val DELETE = key("delete", GLFW.GLFW_KEY_DELETE)
    val BACK = key("back", GLFW.GLFW_KEY_LEFT)
    val FORWARD = key("forward", GLFW.GLFW_KEY_RIGHT)
    val PREV_KEY = key("prev_key", GLFW.GLFW_KEY_DOWN)
    val NEXT_KEY = key("next_key", GLFW.GLFW_KEY_UP)
    val PREVIEW = key("preview", GLFW.GLFW_KEY_G)
    val SAVE = key("save", GLFW.GLFW_KEY_ENTER)

    val ALL = listOf(CAMERA, ACTOR, POSE, EASE, FADE, DELETE, BACK, FORWARD, PREV_KEY, NEXT_KEY, PREVIEW, SAVE)

    /** Number of presses since the last call, so held keys and quick taps both count. */
    fun KeyMapping.presses(): Int {
        var count = 0
        while (consumeClick()) count++
        return count
    }

    fun KeyMapping.label(): String = translatedKeyMessage.string
}
