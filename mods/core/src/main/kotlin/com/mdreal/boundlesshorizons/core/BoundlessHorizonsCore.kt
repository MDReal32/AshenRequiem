package com.mdreal.boundlesshorizons.core

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Mod(BoundlessHorizonsCore.ID)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
object BoundlessHorizonsCore {
    const val ID = "boundless_horizons_core"
    val LOGGER: Logger = LogManager.getLogger(ID)


    @SubscribeEvent
    fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOGGER.log(Level.INFO, "Hello! This is working!")
    }
}
