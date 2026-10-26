package com.mdreal.cinematica.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth

/**
 * Spoken lines outside scenes: one subtitle at a time above the hotbar, with its voice when the client knows the sound.
 * A missing sound is fine, the subtitle shows alone. Lines wait while a scene plays and only the newest few are kept,
 * so a burst of them doesn't talk for a minute.
 */
object Lines {
    private const val KEEP = 2
    private const val FADE = 10
    private const val WIDTH = 280

    private class Line(val text: Component, val sound: ResourceLocation?) {
        // Long enough to read: about 17 characters a second, and never shorter than three seconds.
        val ticks = maxOf(60, 30 + text.string.length * 6 / 5)
    }

    private val queue = ArrayDeque<Line>()
    private var current: Line? = null
    private var age = 0

    fun say(key: String, sound: ResourceLocation?) {
        while (queue.size >= KEEP) queue.removeFirst()
        queue.addLast(Line(Component.translatable(key), sound))
    }

    fun tick() {
        if (Director.active) return
        val line = current
        if (line != null && ++age < line.ticks) return
        current = queue.removeFirstOrNull()
        age = 0
        current?.sound?.let(::play)
    }

    fun clear() {
        queue.clear()
        current = null
    }

    fun render(graphics: GuiGraphics, partialTick: Float) {
        val line = current ?: return
        if (Director.active) return
        val time = age + partialTick
        val alpha = Mth.clamp(minOf(time, line.ticks - time) / FADE, 0f, 1f)
        if (alpha <= 0.05f) return

        val font = Minecraft.getInstance().font
        val rows = font.split(line.text, WIDTH)
        val width = rows.maxOf(font::width)
        val height = rows.size * font.lineHeight
        val x = graphics.guiWidth() / 2
        // Above the action bar, which sits 68 up from the bottom.
        val top = graphics.guiHeight() - 80 - height
        graphics.fill(x - width / 2 - 4, top - 3, x + width / 2 + 4, top + height + 2, (alpha * 0x80).toInt() shl 24)
        val color = ((alpha * 255).toInt() shl 24) or 0xFFFFFF
        rows.forEachIndexed { i, row -> graphics.drawString(font, row, x - font.width(row) / 2, top + i * font.lineHeight, color) }
    }

    private fun play(sound: ResourceLocation) {
        val sounds = Minecraft.getInstance().soundManager
        if (sounds.getSoundEvent(sound) == null) return
        sounds.play(
            SimpleSoundInstance(
                sound, SoundSource.VOICE, 1f, 1f, SoundInstance.createUnseededRandom(), false, 0,
                SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true
            )
        )
    }
}
