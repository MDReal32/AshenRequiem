package com.mdreal.cinematica.client

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * Open while a scene is paused, so the mouse is free: click the picture to play on, click or drag along the progress
 * track to seek. The playback keys work here too, Shift steps single ticks, and Escape plays on.
 */
class PlayerScreen : Screen(Component.translatable("cinematica_studio.player.title")) {
    private var seeking = false

    override fun isPauseScreen() = false

    // The scene stays as it is behind the screen: no blur, no darkening.
    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        Director.hover(graphics, mouseX, mouseY, width, height)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        if (Director.overTrack(mouseX, mouseY, width, height)) {
            seeking = true
            Director.seek(Director.tickAt(mouseX, width))
        } else {
            Director.togglePause()
        }
        return true
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (seeking) Director.seek(Director.tickAt(mouseX, width))
        return seeking
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        seeking = false
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val step = if (hasShiftDown()) 1 else Director.STEP
        when {
            keyCode == GLFW.GLFW_KEY_ESCAPE || Keys.PLAY_PAUSE.matches(keyCode, scanCode) -> Director.togglePause()
            Keys.STEP_BACK.matches(keyCode, scanCode) -> Director.step(-step)
            Keys.STEP_FORWARD.matches(keyCode, scanCode) -> Director.step(step)
            Keys.SNAPSHOT.matches(keyCode, scanCode) -> Director.snapshot()
            else -> return super.keyPressed(keyCode, scanCode, modifiers)
        }
        return true
    }
}
