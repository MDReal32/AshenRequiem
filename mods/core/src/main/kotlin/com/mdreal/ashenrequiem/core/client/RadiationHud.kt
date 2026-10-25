package com.mdreal.ashenrequiem.core.client

import com.mdreal.ashenrequiem.core.CoreConfig
import com.mdreal.ashenrequiem.core.radiation.ClientRadiation
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

/** A row of 10 icons above the hunger bar, one per unit of radiation, filling left to right in tenths like a progress bar. */
object RadiationHud {
    val ID = CoreConfig.id("radiation")
    private val EMPTY = CoreConfig.id("hud/radiation_empty")
    private val FULL = CoreConfig.id("hud/radiation_full")
    private const val ICON = 9
    private const val INNER = 7 // the yellow between the outline columns 0 and 8

    fun render(graphics: GuiGraphics, delta: DeltaTracker) {
        val mc = Minecraft.getInstance()
        val tenths = ClientRadiation.tenths
        if (tenths <= 0 || mc.options.hideGui || mc.gameMode?.canHurtPlayer() != true) return

        val right = graphics.guiWidth() / 2 + 91
        val y = graphics.guiHeight() - mc.gui.rightHeight
        RenderSystem.enableBlend()
        for (i in 0 until 10) {
            val x = right - (9 - i) * 8 - ICON
            graphics.blitSprite(EMPTY, x, y, ICON, ICON)
            val fill = (tenths - i * 10).coerceIn(0, 10)
            if (fill == 0) continue
            // Each tenth shows at least a column, and the last column only lights at a full unit.
            val shown = if (fill == 10) INNER else Math.round(fill * INNER / 10f).coerceIn(1, INNER - 1)
            val width = if (fill == 10) ICON else 1 + shown
            graphics.blitSprite(FULL, ICON, ICON, 0, 0, x, y, width, ICON)
        }
        RenderSystem.disableBlend()
        mc.gui.rightHeight += 10
    }
}
