package com.mdreal.ashenrequiem.core.client

import com.mdreal.ashenrequiem.core.CoreConfig
import com.mdreal.ashenrequiem.core.radiation.ClientRadiation
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

/** A row of 10 icons above the hunger bar, one per unit of radiation, each filling from the bottom in tenths. */
object RadiationHud {
    val ID = CoreConfig.id("radiation")
    private val EMPTY = CoreConfig.id("hud/radiation_empty")
    private val FULL = CoreConfig.id("hud/radiation_full")
    private const val ICON = 9

    fun render(graphics: GuiGraphics, delta: DeltaTracker) {
        val mc = Minecraft.getInstance()
        val tenths = ClientRadiation.tenths
        if (tenths <= 0 || mc.options.hideGui || mc.gameMode?.canHurtPlayer() != true) return

        val right = graphics.guiWidth() / 2 + 91
        val y = graphics.guiHeight() - mc.gui.rightHeight
        RenderSystem.enableBlend()
        for (i in 0 until 10) {
            val x = right - i * 8 - ICON
            graphics.blitSprite(EMPTY, x, y, ICON, ICON)
            val fill = (tenths - i * 10).coerceIn(0, 10)
            if (fill == 0) continue
            val height = (fill * ICON + 9) / 10
            graphics.blitSprite(FULL, ICON, ICON, 0, ICON - height, x, y + ICON - height, ICON, height)
        }
        RenderSystem.disableBlend()
        mc.gui.rightHeight += 10
    }
}
