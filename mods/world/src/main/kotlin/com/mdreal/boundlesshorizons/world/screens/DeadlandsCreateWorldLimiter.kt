package com.mdreal.boundlesshorizons.world.screens

import com.mdreal.boundlesshorizons.world.WorldConfig
import com.mdreal.boundlesshorizons.world.config.WorldCommonConfig
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.tabs.TabNavigationBar
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ScreenEvent

@EventBusSubscriber(
    modid = WorldConfig.MOD_ID,
    bus = EventBusSubscriber.Bus.GAME,
    value = [Dist.CLIENT]
)
object DeadlandsCreateWorldLimiter {
    @SubscribeEvent
    fun onInitScreenPost(e: ScreenEvent.Init.Post) {
        if (!WorldCommonConfig.forceDeadlandsOnly.get()) return

        val screen = e.screen
        if (screen !is CreateWorldScreen) return

        try {
            minimalizeCreateWorld(screen)
        } catch (t: Throwable) {
            // Fail soft: never break world creation
            t.printStackTrace()
        }
    }

    private fun minimalizeCreateWorld(screen: CreateWorldScreen) {
        val keep = mutableSetOf<AbstractWidget>()
        val hide = mutableListOf<AbstractWidget>()

        var nameBox: EditBox? = null
        var gamemodeBtn: Button? = null
        var createBtn: Button? = null
        var presetBtn: Button? = null

        screen.children().forEach { c ->
            val w = c as? AbstractWidget ?: return@forEach
            when (w) {
                is EditBox -> { nameBox = w; keep += w }
                is Button -> {
                    val label = w.message.string.lowercase()
                    when {
                        label.contains("create") && label.contains("world") -> { createBtn = w; keep += w }
                        label.contains("game") && label.contains("mode")   -> { gamemodeBtn = w; keep += w }
                        label.contains("preset") || label.contains("world type") -> { presetBtn = w; /* we'll hide later */ }
                        else -> hide += w
                    }
                }
                else -> hide += w
            }
        }

        hide.forEach { it.visible = false; it.active = false }

        presetBtn?.let { btn ->
            var guard = 16
            while (!btn.message.string.lowercase().contains("deadlands") && guard-- > 0) btn.onPress()
            btn.visible = false
            btn.active = false
        }

        stripTabs(screen)

        val cx = screen.width / 2
        var y = screen.height / 2 - 40
        val w = 240
        nameBox?.apply { setX(cx - w/2); setY(y); setWidth(w); y += 28 }
        gamemodeBtn?.apply { setX(cx - w/2); setY(y); setWidth(w); y += 28 }
        createBtn?.apply { setX(cx - 100); setY(y + 10); setWidth(200) }
    }

    @SubscribeEvent
    fun onMousePress(e: ScreenEvent.MouseButtonPressed.Pre) {
        val s = e.screen
        if (s is CreateWorldScreen && WorldCommonConfig.forceDeadlandsOnly.get()) {
            if (e.mouseY <= 44) e.isCanceled = true
        }
    }
    
    @SubscribeEvent
    fun onRender(e: ScreenEvent.Render.Post) {
        val s = e.screen
        if (s is CreateWorldScreen && WorldCommonConfig.forceDeadlandsOnly.get()) {
            e.guiGraphics.fill(0, 0, s.width, 44, 0xC0000000.toInt())
        }
    }

    private fun stripTabs(screen: Screen) {
        val clazz = try { Class.forName("net.minecraft.client.gui.components.tabs.TabNavigationBar") } catch (_: Throwable) { null }
        if (clazz == null) return
        // nuke any fields of that type
        var k: Class<*>? = screen.javaClass
        while (k != null) {
            for (f in k.declaredFields) {
                try {
                    if (f.type == clazz) { f.isAccessible = true; f.set(screen, null) }
                } catch (_: Throwable) {}
            }
            k = k.superclass
        }
        // purge from any internal lists that hold renderables/listeners
        fun shouldRemove(o: Any?) = o != null && (clazz.isInstance(o) || o.javaClass.name.startsWith("net.minecraft.client.gui.components.tabs."))
        k = screen.javaClass
        while (k != null) {
            for (f in k.declaredFields) {
                try {
                    f.isAccessible = true
                    val v = f.get(screen)
                    if (v is MutableList<*>) {
                        @Suppress("UNCHECKED_CAST")
                        (v as MutableList<Any?>).removeIf { shouldRemove(it) }
                    }
                } catch (_: Throwable) {}
            }
            k = k.superclass
        }
    }
}