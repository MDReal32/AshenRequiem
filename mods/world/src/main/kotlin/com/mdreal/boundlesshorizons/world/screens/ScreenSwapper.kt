package com.mdreal.boundlesshorizons.world.screens

import com.mdreal.boundlesshorizons.world.WorldConfig
import com.mdreal.boundlesshorizons.world.config.WorldCommonConfig
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ScreenEvent

@EventBusSubscriber(modid = WorldConfig.MOD_ID, value = [Dist.CLIENT])
object ScreenSwapper {
    @SubscribeEvent
    fun onOpening(event: ScreenEvent.Opening) {
        if (!WorldCommonConfig.forceDeadlandsOnly.get()) return
        val incoming = event.screen ?: return
        if (incoming is CreateWorldScreen) {
            val screen = MinimalDeadlandsCreateWorldScreen.wrap(incoming)
            event.newScreen = screen
        }
    }
}